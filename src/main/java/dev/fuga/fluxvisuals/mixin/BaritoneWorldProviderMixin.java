package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import baritone.cache.WorldProvider;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Baritone keeps one world cache for the one MinecraftClient instance. The
 * multibot layer temporarily swaps client worlds while applying detached
 * packets; those swaps are not real world unload/load events and must not
 * make Baritone close and asynchronously save the visible world's cache.
 */
@Mixin(value = WorldProvider.class, remap = false)
public abstract class BaritoneWorldProviderMixin {
    private static boolean fluxvisuals$isDetachedContext() {
        MultiBotManager manager = dev.fuga.fluxvisuals.FluxVisualsClient.MULTI_BOT_MANAGER;
        return MultiBotManager.isBackgroundContext()
                || MultiBotManager.isManagedSessionSwitch()
                || (manager != null && manager.hasManagedSessions());
    }

    @Inject(method = "initWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluxvisuals$ignoreDetachedWorldInit(World world, CallbackInfo ci) {
        if (fluxvisuals$isDetachedContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "closeWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluxvisuals$ignoreDetachedWorldClose(CallbackInfo ci) {
        if (fluxvisuals$isDetachedContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "detectAndHandleBrokenLoading", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluxvisuals$ignoreDetachedContextWorldSwap(CallbackInfo ci) {
        if (fluxvisuals$isDetachedContext()) {
            ci.cancel();
        }
    }
}
