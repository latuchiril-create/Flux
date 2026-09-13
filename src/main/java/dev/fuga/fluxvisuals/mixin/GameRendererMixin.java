package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow private float fovMultiplier;
    @Shadow private float lastFovMultiplier;

    @Inject(method = "setWorld", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$keepActiveWorld(ClientWorld world, CallbackInfo ci) {
        if (MultiBotManager.shouldBlockNonVisibleRendererWorld(world)
                || (world == null && MultiBotManager.isCurrentBotContext())) {
            ci.cancel();
        }
    }

    @Inject(method = "reset", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$keepActiveRendererState(CallbackInfo ci) {
        // enterReconfiguration resets the shared renderer. A background bot
        // must clear only its detached session state and never reset camera
        // effects or render interpolation for the visible account.
        if (MultiBotManager.isCurrentBotContext()) {
            ci.cancel();
        }
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"))
    private void fluxvisuals$stretchProjection(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float actualAspect = width / (float) height;
        float scale = FluxVisualsClient.MODULE_MANAGER.getAspectRatio().getProjectionScale(actualAspect);
        if (Math.abs(scale - 1.0F) > 0.001F) {
            Matrix4f matrix = cir.getReturnValue();
            matrix.m00(matrix.m00() * scale);
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void fluxvisuals$applyZoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(FluxVisualsClient.MODULE_MANAGER.getZoom().applyFov(cir.getReturnValueF()));
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$cancelHurtTilt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (FluxVisualsClient.MODULE_MANAGER.getRemovals().removeHurtCamera()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideHandsDuringThirdPersonReturn(float tickProgress, boolean sleeping, Matrix4f positionMatrix,
                                                              CallbackInfo ci) {
        if (FluxVisualsClient.MODULE_MANAGER.getAnimations().shouldHideFirstPersonItems()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateFovMultiplier", at = @At("TAIL"))
    private void fluxvisuals$cancelSprintFov(CallbackInfo ci) {
        if (FluxVisualsClient.MODULE_MANAGER.getRemovals().removeSprintFov()) {
            fovMultiplier = 1.0F;
            lastFovMultiplier = 1.0F;
        }
    }
}
