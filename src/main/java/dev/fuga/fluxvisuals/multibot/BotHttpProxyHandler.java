package dev.fuga.fluxvisuals.multibot;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class BotHttpProxyHandler extends ChannelDuplexHandler {
    private final InetSocketAddress proxyAddress;
    private final String username;
    private final String password;
    private SocketAddress targetAddress;
    private ChannelPromise connectPromise;
    private boolean headerSent;

    public BotHttpProxyHandler(InetSocketAddress proxyAddress, String username, String password) {
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
                sendConnectHeader(ctx);
            }
        }));
    }

    private void sendConnectHeader(ChannelHandlerContext ctx) {
        if (!(targetAddress instanceof InetSocketAddress inetTarget)) {
            fail(ctx, new IllegalArgumentException("Target address is not InetSocketAddress: " + targetAddress));
            return;
        }

        String host = inetTarget.getHostString();
        int port = inetTarget.getPort();
        StringBuilder req = new StringBuilder();
        req.append("CONNECT ").append(host).append(":").append(port).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(host).append(":").append(port).append("\r\n");
        if (!username.isBlank() || !password.isBlank()) {
            String auth = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            req.append("Proxy-Authorization: Basic ").append(encoded).append("\r\n");
        }
        req.append("\r\n");

        headerSent = true;
        byte[] bytes = req.toString().getBytes(StandardCharsets.US_ASCII);
        ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf in)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            if (headerSent) {
                String text = in.toString(StandardCharsets.US_ASCII);
                if (text.contains("\r\n\r\n")) {
                    int endHeaderIndex = text.indexOf("\r\n\r\n") + 4;
                    String firstLine = text.substring(0, text.indexOf("\r\n"));
                    if (firstLine.contains("200")) {
                        ctx.pipeline().remove(this);
                        if (connectPromise != null && !connectPromise.isDone()) {
                            connectPromise.trySuccess();
                        }
                        in.skipBytes(endHeaderIndex);
                        if (in.isReadable()) {
                            ctx.fireChannelRead(in.retain());
                        }
                        return;
                    } else {
                        fail(ctx, new DecoderException("HTTP Proxy connection failed: " + firstLine));
                        return;
                    }
                }
            }
        } finally {
            in.release();
        }
    }

    private void fail(ChannelHandlerContext ctx, Throwable cause) {
        if (connectPromise != null && !connectPromise.isDone()) {
            connectPromise.tryFailure(cause);
        }
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }
}
