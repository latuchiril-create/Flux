package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReconfiguringScreen.class)
public abstract class ReconfiguringScreenMixin {
    @Shadow @Final
    private ClientConnection connection;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$tickManagedConnectionInSessionContext(CallbackInfo ci) {
        if (FluxVisualsClient.MULTI_BOT_MANAGER.tickReconfigurationScreenConnection(connection)) {
            ci.cancel();
        }
    }
}
