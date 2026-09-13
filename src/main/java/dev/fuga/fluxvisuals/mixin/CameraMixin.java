package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.visual.FreeLook;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    private static final long FREE_LOOK_RETURN_ROTATION_NS = 240_000_000L;

    @Shadow
    private boolean thirdPerson;

    private boolean fluxvisuals$requestedThirdPerson;
    private boolean fluxvisuals$renderingThirdPerson;
    private boolean fluxvisuals$hasThirdPersonAngle;
    private float fluxvisuals$thirdPersonYaw;
    private float fluxvisuals$thirdPersonPitch;
    private boolean fluxvisuals$freeLookWasActive;
    private boolean fluxvisuals$freeLookReturningRotation;
    private long fluxvisuals$freeLookReturnStartedAtNanos;
    private float fluxvisuals$freeLookReturnYaw;
    private float fluxvisuals$freeLookReturnPitch;

    @Shadow
    private float yaw;

    @Shadow
    private float pitch;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", shift = At.Shift.AFTER)
    )
    private void fluxvisuals$applyFreeLookRotation(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                                   boolean inverseView, float tickProgress, CallbackInfo ci) {
        FreeLook freeLook = FluxVisualsClient.MODULE_MANAGER.getFreeLook();
        if (freeLook.isActive()) {
            setRotation(freeLook.getYaw(), freeLook.getPitch());
        }
    }

    @ModifyVariable(method = "update", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean fluxvisuals$keepThirdPersonCameraForClosing(boolean thirdPerson) {
        FreeLook freeLook = FluxVisualsClient.MODULE_MANAGER.getFreeLook();
        fluxvisuals$requestedThirdPerson = thirdPerson;
        fluxvisuals$renderingThirdPerson = FluxVisualsClient.MODULE_MANAGER.getAnimations()
                .shouldUseThirdPersonCamera(thirdPerson, freeLook.isActive());
        return fluxvisuals$renderingThirdPerson;
    }

    @Inject(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F", shift = At.Shift.BEFORE)
    )
    private void fluxvisuals$keepClosingCameraAngle(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                                    boolean inverseView, float tickProgress, CallbackInfo ci) {
        FreeLook freeLook = FluxVisualsClient.MODULE_MANAGER.getFreeLook();
        float targetYaw = yaw;
        float targetPitch = pitch;
        if (freeLook.isActive()) {
            setRotation(freeLook.getYaw(), freeLook.getPitch());
            fluxvisuals$thirdPersonYaw = yaw;
            fluxvisuals$thirdPersonPitch = pitch;
            fluxvisuals$hasThirdPersonAngle = true;
            fluxvisuals$freeLookWasActive = true;
            fluxvisuals$freeLookReturningRotation = false;
            fluxvisuals$freeLookReturnStartedAtNanos = 0L;
            return;
        }

        boolean usingFreeLookReturn = false;
        if (fluxvisuals$freeLookWasActive) {
            fluxvisuals$freeLookWasActive = false;
            fluxvisuals$freeLookReturningRotation = true;
            fluxvisuals$freeLookReturnStartedAtNanos = System.nanoTime();
            fluxvisuals$freeLookReturnYaw = fluxvisuals$thirdPersonYaw;
            fluxvisuals$freeLookReturnPitch = fluxvisuals$thirdPersonPitch;
        }
        if (fluxvisuals$freeLookReturningRotation) {
            float progress = fluxvisuals$freeLookReturnProgress();
            setRotation(
                    lerpAngleDegrees(progress, fluxvisuals$freeLookReturnYaw, targetYaw),
                    lerp(progress, fluxvisuals$freeLookReturnPitch, targetPitch)
            );
            usingFreeLookReturn = true;
            if (progress >= 0.995F) {
                fluxvisuals$freeLookReturningRotation = false;
                fluxvisuals$freeLookReturnStartedAtNanos = 0L;
                setRotation(targetYaw, targetPitch);
                usingFreeLookReturn = false;
            }
        }

        if (fluxvisuals$requestedThirdPerson) {
            fluxvisuals$thirdPersonYaw = yaw;
            fluxvisuals$thirdPersonPitch = pitch;
            fluxvisuals$hasThirdPersonAngle = true;
        } else if (fluxvisuals$renderingThirdPerson && fluxvisuals$hasThirdPersonAngle) {
            if (usingFreeLookReturn) {
                fluxvisuals$thirdPersonYaw = yaw;
                fluxvisuals$thirdPersonPitch = pitch;
            } else {
                float progress = FluxVisualsClient.MODULE_MANAGER.getAnimations().cameraReturnRotationProgress();
                setRotation(
                        lerpAngleDegrees(progress, fluxvisuals$thirdPersonYaw, targetYaw),
                        lerp(progress, fluxvisuals$thirdPersonPitch, targetPitch)
                );
            }
        } else if (!usingFreeLookReturn) {
            fluxvisuals$hasThirdPersonAngle = false;
        }
    }

    @ModifyArg(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"),
            index = 0
    )
    private float fluxvisuals$smoothThirdPersonDistance(float distance) {
        FreeLook freeLook = FluxVisualsClient.MODULE_MANAGER.getFreeLook();
        return FluxVisualsClient.MODULE_MANAGER.getAnimations().cameraDistance(
                distance,
                fluxvisuals$requestedThirdPerson || freeLook.isActive(),
                freeLook.getCameraDistance(),
                freeLook.isActive()
        );
    }

    @Inject(method = "getSubmersionType", at = @At("RETURN"), cancellable = true)
    private void fluxvisuals$removeSelectedFluidSubmersion(CallbackInfoReturnable<CameraSubmersionType> cir) {
        CameraSubmersionType submersionType = cir.getReturnValue();
        if (FluxVisualsClient.MODULE_MANAGER.getRemovals().removeFluid(submersionType)) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        }
    }

    private static float lerp(float delta, float start, float end) {
        return start + (end - start) * delta;
    }

    private static float lerpAngleDegrees(float delta, float start, float end) {
        return start + wrapDegrees(end - start) * delta;
    }

    private float fluxvisuals$freeLookReturnProgress() {
        long startedAt = fluxvisuals$freeLookReturnStartedAtNanos;
        if (startedAt == 0L) {
            fluxvisuals$freeLookReturnStartedAtNanos = System.nanoTime();
            return 0.0F;
        }
        float linear = (System.nanoTime() - startedAt) / (float) FREE_LOOK_RETURN_ROTATION_NS;
        return easeOutCubic(Math.max(0.0F, Math.min(1.0F, linear)));
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float wrapDegrees(float value) {
        value %= 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }
}
