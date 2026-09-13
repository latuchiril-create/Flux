package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "setWorld", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$keepActiveWorld(World world, CallbackInfo ci) {
        if (MultiBotManager.shouldBlockNonVisibleRendererWorld(world)
                || (world == null && MultiBotManager.isCurrentBotContext())) {
            ci.cancel();
        }
    }
}
