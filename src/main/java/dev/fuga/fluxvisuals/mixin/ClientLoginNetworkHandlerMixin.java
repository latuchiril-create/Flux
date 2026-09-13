package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLoginNetworkHandler.class)
public abstract class ClientLoginNetworkHandlerMixin {
    @Inject(method = "onDisconnected", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$handleBotLoginDisconnect(DisconnectionInfo info, CallbackInfo ci) {
        String reason = info == null || info.reason() == null ? "Disconnected" : info.reason().getString();
        if (FluxVisualsClient.MULTI_BOT_MANAGER.handleDisconnected(
                (ClientLoginNetworkHandler) (Object) this,
                reason
        )) {
            ci.cancel();
        }
    }
}
