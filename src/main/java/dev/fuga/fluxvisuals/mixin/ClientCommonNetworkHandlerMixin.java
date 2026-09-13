package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
    @Inject(method = "onKeepAlive", at = @At("HEAD"))
    private void fluxvisuals$observeKeepAlive(KeepAliveS2CPacket packet, CallbackInfo ci) {
        if (FluxVisualsClient.MULTI_BOT_MANAGER.hasManagedSessions()) {
            FluxVisualsClient.MULTI_BOT_MANAGER.observeInboundLatencyPacket(
                    (ClientCommonNetworkHandler) (Object) this, packet);
        }
    }

    @Inject(method = "onPing", at = @At("HEAD"))
    private void fluxvisuals$observeCommonPing(CommonPingS2CPacket packet, CallbackInfo ci) {
        if (FluxVisualsClient.MULTI_BOT_MANAGER.hasManagedSessions()) {
            FluxVisualsClient.MULTI_BOT_MANAGER.observeInboundLatencyPacket(
                    (ClientCommonNetworkHandler) (Object) this, packet);
        }
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$suppressBotTickEnd(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientTickEndC2SPacket
                && FluxVisualsClient.MULTI_BOT_MANAGER.shouldSuppressBotTickEnd(
                (net.minecraft.network.listener.PacketListener) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBotResourcePack(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        if (FluxVisualsClient.MULTI_BOT_MANAGER.handleBotResourcePack(
                (ClientCommonNetworkHandler) (Object) this,
                packet.id()
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "onResourcePackRemove", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$skipBotResourcePackRemoval(ResourcePackRemoveS2CPacket packet, CallbackInfo ci) {
        if (FluxVisualsClient.MULTI_BOT_MANAGER.shouldIgnoreBotResourcePackRemoval(
                (ClientCommonNetworkHandler) (Object) this
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "onDisconnected", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$handleBanDisconnect(DisconnectionInfo info, CallbackInfo ci) {
        String reason = info == null || info.reason() == null ? "Disconnected" : info.reason().getString();
        if (FluxVisualsClient.MULTI_BOT_MANAGER.handleDisconnected(
                (ClientCommonNetworkHandler) (Object) this,
                reason
        )) {
            ci.cancel();
            return;
        }
        if (info != null && info.reason() != null) {
            FluxVisualsClient.MODULE_MANAGER.getTelegram().handleDisconnect(info.reason().getString());
        }
    }
}
