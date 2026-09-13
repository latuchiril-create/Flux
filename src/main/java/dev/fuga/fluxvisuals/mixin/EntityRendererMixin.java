package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$showPlayerLabels(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (FluxVisualsClient.MODULE_MANAGER.getSafeNametag().isEnabled()
                && client != null
                && entity instanceof PlayerEntity
                && entity == client.player
                && client.options.getPerspective().isFrontView() == false
                && client.options.getPerspective().isFirstPerson() == false) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void fluxvisuals$storeBelowText(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity instanceof AbstractClientPlayerEntity player
                && FluxVisualsClient.MODULE_MANAGER.getSafeNametag().isEnabled()
                && client != null
                && entity == client.player
                && !client.options.getPerspective().isFirstPerson()) {
            state.displayName = player.getDisplayName();
            if (state.nameLabelPos == null) {
                state.nameLabelPos = player.getAttachments().getPointNullable(net.minecraft.entity.EntityAttachmentType.NAME_TAG, 0, player.getLerpedYaw(tickProgress));
            }
        }
    }
}
