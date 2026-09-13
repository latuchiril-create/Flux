package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.CustomFogModifier;
import dev.fuga.fluxvisuals.modules.visual.WorldCustomizer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.ByteBuffer;
import java.util.List;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow @Final private static List<FogModifier> FOG_MODIFIERS;

    @Shadow
    private void applyFog(ByteBuffer buffer, int bufPos, Vector4f fogColor, float environmentalStart, float environmentalEnd,
                          float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudEnd) {
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void fluxvisuals$registerCustomFogModifier(CallbackInfo ci) {
        FOG_MODIFIERS.add(0, new CustomFogModifier());
    }

    @Redirect(
            method = "applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            )
    )
    private void fluxvisuals$applyCustomFogSettings(FogRenderer instance, ByteBuffer buffer, int bufPos, Vector4f fogColor,
                                                    float environmentalStart, float environmentalEnd,
                                                    float renderDistanceStart, float renderDistanceEnd,
                                                    float skyEnd, float cloudEnd) {
        WorldCustomizer customizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        if (!customizer.isEnabled() || !customizer.isCustomFogEnabled()) {
            applyFog(buffer, bufPos, fogColor, environmentalStart, environmentalEnd, renderDistanceStart, renderDistanceEnd, skyEnd, cloudEnd);
            return;
        }

        float distance = customizer.getFogDistance();
        Vector4f customColor = customFogColor(customizer);
        applyFog(buffer, bufPos, customColor,
                0.0F, distance,
                0.0F, distance,
                distance, distance);
    }

    @Inject(
            method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IFZ)Lorg/joml/Vector4f;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fluxvisuals$getCustomFogColor(Camera camera, float tickProgress, ClientWorld world, int viewDistance,
                                               float skyDarkness, boolean thick, CallbackInfoReturnable<Vector4f> cir) {
        WorldCustomizer customizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        if (customizer.isEnabled() && customizer.isCustomFogEnabled()) {
            cir.setReturnValue(customFogColor(customizer));
        }
    }

    @Inject(
            method = "applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void fluxvisuals$returnCustomFogColor(Camera camera, int viewDistance, boolean thick, RenderTickCounter tickCounter,
                                                  float skyDarkness, ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        WorldCustomizer customizer = FluxVisualsClient.MODULE_MANAGER.getWorldCustomizer();
        if (customizer.isEnabled() && customizer.isCustomFogEnabled()) {
            cir.setReturnValue(customFogColor(customizer));
        }
    }

    private static Vector4f customFogColor(WorldCustomizer customizer) {
        return new Vector4f(customizer.getFogRed(), customizer.getFogGreen(), customizer.getFogBlue(), 1.0F);
    }
}
