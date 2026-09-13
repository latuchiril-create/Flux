package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.block.BlockState;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BubbleColumnBlock.class)
public abstract class BubbleColumnBlockMixin {
    @Shadow @Final public static BooleanProperty DRAG;

    @Redirect(
            method = "randomDisplayTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addImportantParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private void fluxvisuals$removeSoulSandBubbleParticles(World world, ParticleEffect parameters,
                                                           double x, double y, double z,
                                                           double velocityX, double velocityY, double velocityZ,
                                                           BlockState state, World originalWorld, BlockPos pos, Random random) {
        boolean soulSandColumn = !state.get(DRAG);
        if (soulSandColumn
                && parameters == ParticleTypes.BUBBLE_COLUMN_UP
                && FluxVisualsClient.MODULE_MANAGER.getRemovals().removeSoulSandBubbles()) {
            return;
        }

        world.addImportantParticleClient(parameters, x, y, z, velocityX, velocityY, velocityZ);
    }
}
