package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.util.EntityRenderDispatcherAccessor;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements EntityRenderDispatcherAccessor {
    @Shadow
    private boolean renderHitboxes;

    @Override
    public boolean fluxvisuals$getRawRenderHitboxes() {
        return renderHitboxes;
    }

    @Inject(method = "setWorld", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$keepVisibleEntityWorld(World world, CallbackInfo ci) {
        if (dev.fuga.fluxvisuals.multibot.MultiBotManager.shouldBlockNonVisibleRendererWorld(world)
                || (world == null && dev.fuga.fluxvisuals.multibot.MultiBotManager.isCurrentBotContext())) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderHitboxes", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideVanillaHitboxes(CallbackInfoReturnable<Boolean> cir) {
        var hitboxCustomizer = FluxVisualsClient.MODULE_MANAGER.getHitboxCustomizer();
        if (hitboxCustomizer.isEnabled() && (hitboxCustomizer.isAlwaysShow() || renderHitboxes)) {
            cir.setReturnValue(false);
        }
    }
}
