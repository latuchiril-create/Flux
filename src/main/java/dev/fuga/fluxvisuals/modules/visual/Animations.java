package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;

public final class Animations extends Module {
    private static final long INVENTORY_OPEN_NS = 280_000_000L;
    private static final long CAMERA_RETURN_HIDE_SELF_GRACE_NS = 80_000_000L;
    private static final long CAMERA_RETURN_ROTATION_NS = 260_000_000L;
    private static final float CAMERA_RETURN_DONE_DISTANCE = 0.08F;
    private static final float CAMERA_RETURN_DONE_ROTATION = 0.995F;
    private static final float CAMERA_RETURN_ITEMS_SHOW_DISTANCE = 0.22F;
    private static final float CAMERA_RETURN_HIDE_SELF_DISTANCE = 2.25F;
    private boolean tab = true;
    private boolean thirdPerson = true;
    private boolean hotbar = true;
    private boolean inventory = true;
    private float tabProgress;
    private long lastTabNanos;
    private float cameraDistance;
    private float cameraReturnStartDistance = 1.0F;
    private float cameraReturnRotationProgress = 1.0F;
    private long cameraReturnRotationStartedAtNanos;
    private long lastCameraNanos;
    private boolean cameraWasThirdPerson;
    private boolean cameraReturningToFirstPerson;
    private long cameraReturnHideSelfUntilNanos;
    private float hotbarSelectionX = Float.NaN;
    private long lastHotbarNanos;
    private int inventoryScreenId;
    private long inventoryOpenedAtNanos;

    public Animations() {
        super("Animations", "Smooths selected vanilla UI and camera transitions.", ModuleCategory.VISUALS);
        setEnabledSilently(true);
    }

    public boolean isTabEnabled() {
        return tab;
    }

    public void setTabEnabled(boolean tab) {
        if (this.tab == tab) {
            return;
        }
        this.tab = tab;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isThirdPersonEnabled() {
        return thirdPerson;
    }

    public void setThirdPersonEnabled(boolean thirdPerson) {
        if (this.thirdPerson == thirdPerson) {
            return;
        }
        this.thirdPerson = thirdPerson;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isHotbarEnabled() {
        return hotbar;
    }

    public void setHotbarEnabled(boolean hotbar) {
        if (this.hotbar == hotbar) {
            return;
        }
        this.hotbar = hotbar;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isInventoryEnabled() {
        return inventory;
    }

    public void setInventoryEnabled(boolean inventory) {
        if (this.inventory == inventory) {
            return;
        }
        this.inventory = inventory;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean shouldAnimateTab() {
        return isEnabled() && tab;
    }

    public boolean shouldAnimateThirdPerson() {
        return isEnabled() && thirdPerson;
    }

    public boolean shouldAnimateHotbar() {
        return isEnabled() && hotbar;
    }

    public boolean shouldAnimateInventory() {
        return isEnabled() && inventory;
    }

    public float tabProgress(boolean visible) {
        if (!shouldAnimateTab()) {
            tabProgress = visible ? 1.0F : 0.0F;
            lastTabNanos = 0L;
            return tabProgress;
        }

        long now = System.nanoTime();
        float dt = deltaSeconds(now, lastTabNanos);
        lastTabNanos = now;
        tabProgress = moveTowards(tabProgress, visible ? 1.0F : 0.0F, dt * (visible ? 8.0F : 11.5F));
        if (Math.abs(tabProgress - (visible ? 1.0F : 0.0F)) < 0.004F) {
            tabProgress = visible ? 1.0F : 0.0F;
        }
        return visible ? easeOutCubic(tabProgress) : easeInCubic(tabProgress);
    }

    public boolean shouldRenderTab(boolean visible) {
        return visible || (shouldAnimateTab() && tabProgress > 0.01F);
    }

    public boolean shouldUseThirdPersonCamera(boolean thirdPersonView, boolean freeLookActive) {
        if (thirdPersonView || freeLookActive) {
            return true;
        }
        if (!shouldAnimateThirdPerson()) {
            cameraDistance = 0.0F;
            cameraReturnStartDistance = 1.0F;
            cameraReturnRotationProgress = 1.0F;
            cameraReturnRotationStartedAtNanos = 0L;
            cameraWasThirdPerson = false;
            cameraReturningToFirstPerson = false;
            cameraReturnHideSelfUntilNanos = 0L;
            lastCameraNanos = 0L;
            return false;
        }
        long now = System.nanoTime();
        boolean returning = cameraWasThirdPerson
                && (cameraDistance > CAMERA_RETURN_DONE_DISTANCE
                || cameraReturnRotationProgress < CAMERA_RETURN_DONE_ROTATION
                || cameraReturningToFirstPerson);
        if (returning && !cameraReturningToFirstPerson) {
            cameraReturningToFirstPerson = true;
            cameraReturnStartDistance = Math.max(cameraDistance, 1.0F);
            cameraReturnRotationProgress = 0.0F;
            cameraReturnRotationStartedAtNanos = now;
        }
        if (returning) {
            updateCameraReturnRotationProgress(now);
        }
        return returning;
    }

    public float cameraDistance(float vanillaDistance, boolean thirdPersonView, float freeLookDistance, boolean freeLookActive) {
        boolean activeView = thirdPersonView || freeLookActive;
        boolean smooth = shouldAnimateThirdPerson() || freeLookActive;
        if (!activeView && !smooth) {
            cameraDistance = 0.0F;
            cameraReturnStartDistance = 1.0F;
            cameraReturnRotationProgress = 1.0F;
            cameraReturnRotationStartedAtNanos = 0L;
            cameraWasThirdPerson = false;
            cameraReturningToFirstPerson = false;
            cameraReturnHideSelfUntilNanos = 0L;
            lastCameraNanos = 0L;
            return vanillaDistance;
        }

        float target = activeView ? (freeLookActive ? freeLookDistance : vanillaDistance) : 0.0F;
        if (!smooth) {
            cameraDistance = target;
            cameraReturnStartDistance = Math.max(target, 1.0F);
            cameraReturnRotationProgress = 1.0F;
            cameraReturnRotationStartedAtNanos = 0L;
            cameraWasThirdPerson = activeView;
            cameraReturningToFirstPerson = false;
            cameraReturnHideSelfUntilNanos = 0L;
            lastCameraNanos = 0L;
            return target;
        }

        long now = System.nanoTime();
        float dt = deltaSeconds(now, lastCameraNanos);
        lastCameraNanos = now;
        boolean wasReturning = cameraReturningToFirstPerson;
        cameraReturningToFirstPerson = !activeView
                && cameraWasThirdPerson
                && (cameraDistance > CAMERA_RETURN_DONE_DISTANCE || cameraReturnRotationProgress < CAMERA_RETURN_DONE_ROTATION);
        if (cameraReturningToFirstPerson && !wasReturning) {
            cameraReturnStartDistance = Math.max(cameraDistance, 1.0F);
            cameraReturnRotationProgress = 0.0F;
            cameraReturnRotationStartedAtNanos = now;
        }
        if (activeView) {
            cameraReturningToFirstPerson = false;
            cameraReturnHideSelfUntilNanos = 0L;
            cameraReturnStartDistance = Math.max(target, 1.0F);
            cameraReturnRotationProgress = 1.0F;
            cameraReturnRotationStartedAtNanos = 0L;
        }
        if (!cameraWasThirdPerson && activeView) {
            cameraDistance = 0.05F;
        }
        cameraDistance = approach(cameraDistance, target, dt, activeView ? 8.5F : 12.0F);
        if (cameraReturningToFirstPerson) {
            updateCameraReturnRotationProgress(now);
        }
        boolean rotationDone = cameraReturnRotationProgress >= CAMERA_RETURN_DONE_ROTATION;
        cameraWasThirdPerson = activeView || cameraDistance > CAMERA_RETURN_DONE_DISTANCE
                || (cameraReturningToFirstPerson && !rotationDone);
        if (!activeView && cameraDistance <= CAMERA_RETURN_DONE_DISTANCE) {
            cameraDistance = 0.0F;
            if (rotationDone) {
                cameraReturnRotationProgress = 1.0F;
                cameraReturnRotationStartedAtNanos = 0L;
                cameraReturningToFirstPerson = false;
                cameraWasThirdPerson = false;
                cameraReturnHideSelfUntilNanos = wasReturning ? now + CAMERA_RETURN_HIDE_SELF_GRACE_NS : 0L;
                lastCameraNanos = 0L;
            }
            return 0.0F;
        }
        return Math.max(0.05F, cameraDistance);
    }

    public boolean shouldHideFirstPersonItems() {
        return cameraReturningToFirstPerson
                && cameraWasThirdPerson
                && cameraDistance > CAMERA_RETURN_ITEMS_SHOW_DISTANCE;
    }

    public float cameraReturnRotationProgress() {
        return cameraReturningToFirstPerson ? cameraReturnRotationProgress : 1.0F;
    }

    public boolean shouldHideLocalPlayerForCameraReturn(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null || client.player == null) {
            return false;
        }
        boolean returningOrJustFinished = cameraReturningToFirstPerson || (cameraReturnHideSelfUntilNanos > 0L && System.nanoTime() < cameraReturnHideSelfUntilNanos);
        return returningOrJustFinished
                && cameraDistance < CAMERA_RETURN_HIDE_SELF_DISTANCE
                && client.player.getId() == entityId;
    }

    public int hotbarSelectionX(int vanillaX) {
        if (!shouldAnimateHotbar()) {
            hotbarSelectionX = vanillaX;
            lastHotbarNanos = 0L;
            return vanillaX;
        }

        long now = System.nanoTime();
        float dt = deltaSeconds(now, lastHotbarNanos);
        lastHotbarNanos = now;
        if (Float.isNaN(hotbarSelectionX) || Math.abs(hotbarSelectionX - vanillaX) > 200.0F) {
            hotbarSelectionX = vanillaX;
        }
        hotbarSelectionX = approach(hotbarSelectionX, vanillaX, dt, 18.0F);
        return Math.round(hotbarSelectionX);
    }

    public float inventoryOpenProgress(Object screen) {
        if (!shouldAnimateInventory() || screen == null) {
            inventoryScreenId = 0;
            inventoryOpenedAtNanos = 0L;
            return 1.0F;
        }

        int screenId = System.identityHashCode(screen);
        long now = System.nanoTime();
        if (inventoryScreenId != screenId) {
            inventoryScreenId = screenId;
            inventoryOpenedAtNanos = now;
        }

        float progress = (now - inventoryOpenedAtNanos) / (float) INVENTORY_OPEN_NS;
        return easeOutCubic(clamp01(progress));
    }

    private static float deltaSeconds(long now, long previous) {
        if (previous == 0L) {
            return 1.0F / 60.0F;
        }
        return Math.min(0.08F, Math.max(0.0F, (now - previous) / 1_000_000_000.0F));
    }

    private static float approach(float current, float target, float dt, float speed) {
        return current + (target - current) * (1.0F - (float) Math.exp(-speed * dt));
    }

    private static float moveTowards(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private static float easeOutCubic(float value) {
        float t = clamp01(value);
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeInCubic(float value) {
        float t = clamp01(value);
        return t * t * t;
    }

    private void updateCameraReturnRotationProgress(long now) {
        if (cameraReturnRotationStartedAtNanos == 0L) {
            cameraReturnRotationStartedAtNanos = now;
        }
        float linear = (now - cameraReturnRotationStartedAtNanos) / (float) CAMERA_RETURN_ROTATION_NS;
        cameraReturnRotationProgress = easeOutCubic(clamp01(linear));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
