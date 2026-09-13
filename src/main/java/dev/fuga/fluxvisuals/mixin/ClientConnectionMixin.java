package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import dev.fuga.fluxvisuals.multibot.BotSession;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.state.NetworkState;
import io.netty.channel.ChannelFutureListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    /**
     * A controlled bot is installed in MinecraftClient, so vanilla would tick
     * its connection once while MultiBotManager also ticks it. That duplicate
     * tick drains the same Netty queue twice and makes the primary account look
     * stalled during account switching. Keep exactly one owner per socket.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipManagerOwnedConnectionTick(CallbackInfo ci) {
        if (!FluxVisualsClient.MULTI_BOT_MANAGER.isManagerConnectionTicking()
                && FluxVisualsClient.MULTI_BOT_MANAGER.shouldSkipConnectionTick(
                (ClientConnection) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendInternal(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$suppressBotTickEndPacketInternal(
            Packet<?> packet,
            ChannelFutureListener listener,
            boolean flush,
            CallbackInfo ci
    ) {
        if (FluxVisualsClient.MULTI_BOT_MANAGER.hasManagedSessions()) {
            FluxVisualsClient.MULTI_BOT_MANAGER.observeOutboundLatencyPacket(
                    (ClientConnection) (Object) this, packet);
        }
        if (fluxvisuals$isSuppressedBotPlayPacket(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$suppressBotTickEndPacket(Packet<?> packet, CallbackInfo ci) {
        if (fluxvisuals$isSuppressedBotPlayPacket(packet)) {
            ci.cancel();
        }
    }

    private boolean fluxvisuals$isSuppressedBotPlayPacket(Packet<?> packet) {
        // The last world/player remain installed as a frozen visual snapshot
        // while configuration is negotiated. The manager filters by packet
        // id rather than Java package because production Minecraft classes
        // are loaded with intermediary names (net.minecraft.class_*).
        ClientConnection connection = (ClientConnection) (Object) this;
        boolean suppressed = FluxVisualsClient.MULTI_BOT_MANAGER.shouldSuppressBotPlayPacket(connection, packet);
        if (suppressed) {
            BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findSession(connection);
            String packetType = packet == null || packet.getPacketType() == null || packet.getPacketType().id() == null
                    ? "null" : packet.getPacketType().id().toString();
            BotDebug.warn("DEBUG_ANARCHY_OUTBOUND_DROPPED_V1", session,
                    "packet=" + (packet == null ? "null" : packet.getClass().getSimpleName())
                            + ", type=" + packetType);
        }
        return suppressed;
    }

    @Inject(method = "transitionInbound", at = @At("TAIL"))
    private void fluxvisuals$traceBotInboundProtocolTransition(
            NetworkState<?> state, PacketListener listener, CallbackInfo ci
    ) {
        ClientConnection connection = (ClientConnection) (Object) this;
        BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findSession(connection);
        if (session != null && !session.isMain()) {
            BotDebug.info("DEBUG_ANARCHY_INBOUND_PHASE_V1", session,
                    "phase=" + state.id() + ", listener=" + listener.getClass().getSimpleName());
        }
    }

    @Inject(method = "handleDisconnection", at = @At("HEAD"))
    private void fluxvisuals$traceBotDisconnect(CallbackInfo ci) {
        ClientConnection connection = (ClientConnection) (Object) this;
        BotSession session = FluxVisualsClient.MULTI_BOT_MANAGER.findSession(connection);
        if (session == null || session.isMain()) {
            return;
        }
        DisconnectionInfo info = connection.getDisconnectionInfo();
        String reason = info == null || info.reason() == null ? "unknown" : info.reason().getString();
        BotDebug.warn("DEBUG_ANARCHY_DISCONNECT_V1", session, "reason=" + reason);
    }
}
