package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.HitColor;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin {
    @Inject(method = "getDyeColor", at = @At("HEAD"), cancellable = true)
    private static void fluxvisuals$overrideArmorColor(EquipmentModel.Layer layer, int dyeColor, CallbackInfoReturnable<Integer> cir) {
        if (HitColor.isArmorTintActive()) {
            cir.setReturnValue(0xFF000000 | FluxVisualsClient.MODULE_MANAGER.getHitColor().getColorRgb());
        }
    }

    @Redirect(method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer fluxvisuals$lightArmorOverlayLayer(Identifier texture) {
        HitColor hitColor = FluxVisualsClient.MODULE_MANAGER.getHitColor();
        if (HitColor.isArmorTintActive() && hitColor.shouldRenderLightOverlay()) {
            return HitColor.lightOverlayLayer(texture);
        }
        return RenderLayer.getArmorCutoutNoCull(texture);
    }
}
