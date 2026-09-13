package dev.fuga.fluxvisuals.multibot;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

public final class BotSocks5Handler extends ChannelDuplexHandler {
    private enum State {
        INIT,
        AUTH_REQUESTED,
        CONNECT_REQUESTED,
        DONE
    }

    private final InetSocketAddress proxyAddress;
    private final String username;
    private final String password;
    private SocketAddress targetAddress;
    private ChannelPromise connectPromise;
    private State state = State.INIT;

    public BotSocks5Handler(InetSocketAddress proxyAddress, String username, String password) {
        this.proxyAddress = proxyAddress;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        this.targetAddress = remoteAddress;
        this.connectPromise = promise;
        ctx.connect(proxyAddress, localAddress, ctx.newPromise().addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                connectPromise.tryFailure(future.cause());
            } else {
                sendInitialGreeting(ctx);
            }
        }));
    }

    private void sendInitialGreeting(ChannelHandlerContext ctx) {
        boolean hasAuth = !username.isBlank() || !password.isBlank();
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeByte(0x05); // SOCKS5
        if (hasAuth) {
            buf.writeByte(2); // 2 methods
            buf.writeByte(0x00); // NO_AUTH
            buf.writeByte(0x02); // USER_PASS
        } else {
            buf.writeByte(1); // 1 method
            buf.writeByte(0x00); // NO_AUTH
        }
        state = State.INIT;
        ctx.writeAndFlush(buf);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf in)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            if (state == State.INIT) {
                if (in.readableBytes() < 2) return;
                byte ver = in.readByte();
                byte method = in.readByte();
                if (ver != 0x05) {
                    fail(ctx, new DecoderException("Invalid SOCKS version: " + ver));
                    return;
                }
                if (method == (byte) 0xFF) {
                    fail(ctx, new DecoderException("SOCKS5 proxy rejected authentication methods"));
                    return;
                }
                if (method == 0x02) {
                    sendAuthRequest(ctx);
                } else if (method == 0x00) {
                    sendConnectRequest(ctx);
                } else {
                    fail(ctx, new DecoderException("Unsupported SOCKS5 auth method: " + method));
                    return;
                }
            } else if (state == State.AUTH_REQUESTED) {
                if (in.readableBytes() < 2) return;
                byte ver = in.readByte();
                byte status = in.readByte();
                if (status != 0x00) {
                    fail(ctx, new DecoderException("SOCKS5 authentication failed (status " + status + ")"));
                    return;
                }
                sendConnectRequest(ctx);
            } else if (state == State.CONNECT_REQUESTED) {
                if (in.readableBytes() < 4) return;
                in.markReaderIndex();
                byte ver = in.readByte();
                byte rep = in.readByte();
                in.readByte(); // RSV
                byte atype = in.readByte();

                int neededAddrLen = 0;
                if (atype == 0x01) {
                    neededAddrLen = 4 + 2; // IPv4 + port
                } else if (atype == 0x03) {
                    if (in.readableBytes() < 1) {
                        in.resetReaderIndex();
                        return;
                    }
                    int domainLen = in.readUnsignedByte();
                    neededAddrLen = domainLen + 2; // domain + port
                } else if (atype == 0x04) {
                    neededAddrLen = 16 + 2; // IPv6 + port
                } else {
                    fail(ctx, new DecoderException("Unknown SOCKS5 address type: " + atype));
                    return;
                }

                if (in.readableBytes() < neededAddrLen) {
                    in.resetReaderIndex();
                    return;
                }

                in.skipBytes(neededAddrLen);

                if (rep != 0x00) {
                    fail(ctx, new DecoderException("SOCKS5 connection failed with reply code: " + rep));
                    return;
                }

                state = State.DONE;
                ctx.pipeline().remove(this);

                if (connectPromise != null && !connectPromise.isDone()) {
                    connectPromise.trySuccess();
                }

                if (in.isReadable()) {
                    ctx.fireChannelRead(in.retain());
                }
                return;
            }
        } finally {
            in.release();
        }
    }

    private void sendAuthRequest(ChannelHandlerContext ctx) {
        byte[] userBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] passBytes = password.getBytes(StandardCharsets.UTF_8);

        ByteBuf buf = ctx.alloc().buffer();
        buf.writeByte(0x01); // Auth subnegotiation version
        buf.writeByte(userBytes.length);
        buf.writeBytes(userBytes);
        buf.writeByte(passBytes.length);
        buf.writeBytes(passBytes);

        state = State.AUTH_REQUESTED;
        ctx.writeAndFlush(buf);
    }

    private void sendConnectRequest(ChannelHandlerContext ctx) {
        if (!(targetAddress instanceof InetSocketAddress inetTarget)) {
            fail(ctx, new IllegalArgumentException("Target address is not InetSocketAddress: " + targetAddress));
            return;
        }

        String host = inetTarget.getHostString();
        int port = inetTarget.getPort();

        byte[] hostBytes = host.getBytes(StandardCharsets.US_ASCII);
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeByte(0x05); // SOCKS5
        buf.writeByte(0x01); // CMD: CONNECT
        buf.writeByte(0x00); // RSV
        buf.writeByte(0x03); // ATYPE: DOMAINNAME
        buf.writeByte(hostBytes.length);
        buf.writeBytes(hostBytes);
        buf.writeShort(port);

        state = State.CONNECT_REQUESTED;
        ctx.writeAndFlush(buf);
    }

    private void fail(ChannelHandlerContext ctx, Throwable cause) {
        state = State.DONE;
        if (connectPromise != null && !connectPromise.isDone()) {
            connectPromise.tryFailure(cause);
        }
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }
}
