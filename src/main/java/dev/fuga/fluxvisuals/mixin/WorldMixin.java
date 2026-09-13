package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$overrideTime(CallbackInfoReturnable<Long> cir) {
        World world = (World) (Object) this;
        if (!world.isClient()) {
            return;
        }

        var customizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        if (customizer.isEnabled()) {
            cir.setReturnValue(customizer.getEffectiveTime());
        }
    }

    @Inject(method = "getSkyAngleRadians", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$overrideSkyAngleRadians(float tickProgress, CallbackInfoReturnable<Float> cir) {
        World world = (World) (Object) this;
        if (!world.isClient()) {
            return;
        }

        var customizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        if (customizer.isEnabled()) {
            cir.setReturnValue(customizer.getEffectiveSkyAngle(tickProgress) * ((float) Math.PI * 2.0F));
        }
    }
}
