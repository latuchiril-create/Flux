package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;

public final class Zoom extends Module {
    public static final String BIND_KEY = "zoom_bind";

    private static final float BASE_MULTIPLIER = 0.32F;
    private static final float MIN_MULTIPLIER = 0.08F;
    private static final float MAX_MULTIPLIER = 0.82F;
    private static final float WHEEL_STEP = 0.07F;

    private boolean keyDown;
    private boolean wheelZoomEnabled = true;
    private float smoothness = 0.58F;
    private float targetMultiplier = BASE_MULTIPLIER;
    private float currentMultiplier = 1.0F;
    private long lastUpdateNanos;

    public Zoom() {
        super("Zoom", "Smooth configurable camera zoom.", ModuleCategory.VISUALS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.currentScreen != null) {
            keyDown = false;
        } else {
            int keyCode = getKeyBind();
            if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN && keyCode != 0 && client.getWindow() != null) {
                long handle = client.getWindow().getHandle();
                boolean isDown = (keyCode >= 1000 && keyCode <= 1010) ?
                        org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, keyCode - 1000) == org.lwjgl.glfw.GLFW.GLFW_PRESS :
                        org.lwjgl.glfw.GLFW.glfwGetKey(handle, keyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                handleBindState(isDown);
            }
        }
        updateCurrentMultiplier();
    }

    public int getKeyBind() {
        return dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.getOrDefault("action_zoom", dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer.getBind(BIND_KEY, org.lwjgl.glfw.GLFW.GLFW_KEY_C));
    }

    public void setKeyBind(int key) {
        dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.put("action_zoom", key);
        dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer.setBind(BIND_KEY, key);
        FluxVisualsClient.requestConfigSave();
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        keyDown = false;
        targetMultiplier = BASE_MULTIPLIER;
        currentMultiplier = 1.0F;
        lastUpdateNanos = 0L;
    }

    public void handleBindState(boolean pressed) {
        if (!isEnabled()) {
            pressed = false;
        }
        if (pressed && !keyDown) {
            targetMultiplier = BASE_MULTIPLIER;
        }
        keyDown = pressed;
    }

    public boolean onMouseScroll(double amount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !keyDown || !wheelZoomEnabled || amount == 0.0D
                || client == null || client.currentScreen != null) {
            return false;
        }
        targetMultiplier = clamp(targetMultiplier - (float) amount * WHEEL_STEP, MIN_MULTIPLIER, MAX_MULTIPLIER);
        return true;
    }

    public float applyFov(float fov) {
        updateCurrentMultiplier();
        if (!isEnabled() || currentMultiplier > 0.999F) {
            return fov;
        }
        return fov * currentMultiplier;
    }

    public boolean isActive() {
        updateCurrentMultiplier();
        return isEnabled() && (keyDown || currentMultiplier < 0.999F);
    }

    public float getSmoothness() {
        return smoothness;
    }

    public void setSmoothness(float smoothness) {
        float clamped = clamp(smoothness, 0.0F, 1.0F);
        if (Math.abs(this.smoothness - clamped) < 0.001F) {
            return;
        }
        this.smoothness = clamped;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isWheelZoomEnabled() {
        return wheelZoomEnabled;
    }

    public void setWheelZoomEnabled(boolean wheelZoomEnabled) {
        if (this.wheelZoomEnabled == wheelZoomEnabled) {
            return;
        }
        this.wheelZoomEnabled = wheelZoomEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    private void updateCurrentMultiplier() {
        float target = isEnabled() && keyDown ? targetMultiplier : 1.0F;
        long now = System.nanoTime();
        if (lastUpdateNanos == 0L) {
            lastUpdateNanos = now;
            return;
        }

        float deltaSeconds = Math.min((now - lastUpdateNanos) / 1_000_000_000.0F, 0.1F);
        lastUpdateNanos = now;
        float speed = 14.0F - smoothness * 10.0F;
        float factor = 1.0F - (float) Math.exp(-speed * deltaSeconds);
        currentMultiplier += (target - currentMultiplier) * factor;
        if (Math.abs(currentMultiplier - target) < 0.0005F) {
            currentMultiplier = target;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
