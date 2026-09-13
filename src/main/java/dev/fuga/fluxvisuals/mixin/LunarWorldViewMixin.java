package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.world.LunarWorldView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LunarWorldView.class)
public interface LunarWorldViewMixin {
    @Inject(method = "getSkyAngle", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$overrideSkyAngle(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof World world) || !world.isClient()) {
            return;
        }

        var customizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        if (customizer.isEnabled()) {
            cir.setReturnValue(customizer.getEffectiveSkyAngle(tickProgress));
        }
    }
}
