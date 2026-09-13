package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fluxvisuals$skipBackgroundServerParticle(
            ParticleEffect effect,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfoReturnable<Particle> cir
    ) {
        if (MultiBotManager.isBackgroundContext()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "setWorld", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$keepActiveParticles(ClientWorld world, CallbackInfo ci) {
        if (MultiBotManager.shouldBlockNonVisibleRendererWorld(world)
                || (world == null && MultiBotManager.isCurrentBotContext())) {
            ci.cancel();
        }
    }
}
