package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents background sessions from replacing or rebuilding Sodium's visible world. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumWorldRendererMixin {
    @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void fluxvisuals$keepActiveLevel(CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void fluxvisuals$skipBackgroundReload(CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "isSectionReady", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void fluxvisuals$skipBackgroundSectionCheck(CallbackInfoReturnable<Boolean> cir) {
        if (MultiBotManager.isBackgroundContext()) {
            cir.setReturnValue(true);
        }
    }
}
