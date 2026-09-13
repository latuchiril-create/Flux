package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.HitColor;
import dev.fuga.fluxvisuals.util.FluxEntityRenderState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected EntityModel<LivingEntityRenderState> model;

    @Shadow
    public abstract Identifier getTexture(LivingEntityRenderState state);

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void fluxvisuals$storeEntityId(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
        ((FluxEntityRenderState) state).fluxvisuals$setEntityId(entity.getId());
    }

    @Inject(method = "getMixColor", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hitColor(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        int entityId = ((FluxEntityRenderState) state).fluxvisuals$getEntityId();
        var hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();
        if (hitColor.isHighlighted(entityId)) {
            cir.setReturnValue(hitColor.getArgbColor());
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideSelfWhenCameraReturns(LivingEntityRenderState state, MatrixStack matrices,
                                                       VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }
        int entityId = ((FluxEntityRenderState) state).fluxvisuals$getEntityId();
        if (FluxVisualsClient.MODULE_MANAGER.getAnimations().shouldHideLocalPlayerForCameraReturn(entityId)) {
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V", shift = At.Shift.BEFORE))
    private void fluxvisuals$renderLightHitOverlay(LivingEntityRenderState state, MatrixStack matrices,
                                                   VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        int entityId = ((FluxEntityRenderState) state).fluxvisuals$getEntityId();
        HitColor hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();
        if (!hitColor.isHighlighted(entityId) || !hitColor.shouldRenderLightOverlay()) {
            return;
        }

        VertexConsumer consumer = vertexConsumers.getBuffer(HitColor.lightOverlayLayer(getTexture(state)));
        model.render(matrices, consumer, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV, hitColor.getArgbColor());
    }
}
