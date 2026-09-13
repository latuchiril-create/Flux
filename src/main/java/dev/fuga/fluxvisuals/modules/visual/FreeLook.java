package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.gui.PremiumClickGuiRenderer;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

public final class FreeLook extends Module {
    public static final String PERSPECTIVE_BIND_KEY = "freelook_perspective";

    private ActivationMode activationMode = ActivationMode.HOLD;
    private boolean active;
    private boolean keyWasDown;
    private Perspective previousPerspective = Perspective.FIRST_PERSON;
    private float yaw;
    private float pitch;
    private float cameraDistance = 4.0F;

    public FreeLook() {
        super("FreeLook", "Free third-person camera.", ModuleCategory.VISUALS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.getWindow() == null || client.player == null || client.currentScreen != null) {
            stop(client);
            keyWasDown = false;
            return;
        }

        int keyCode = getKeyBind();
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            stop(client);
            keyWasDown = false;
            return;
        }

        boolean keyDown = (keyCode >= 1000 && keyCode <= 1010) ?
                GLFW.glfwGetMouseButton(client.getWindow().getHandle(), keyCode - 1000) == GLFW.GLFW_PRESS :
                GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
        if (activationMode == ActivationMode.HOLD) {
            if (keyDown) {
                start(client);
            } else {
                stop(client);
            }
        } else if (keyDown && !keyWasDown) {
            if (active) {
                stop(client);
            } else {
                start(client);
            }
        }
        keyWasDown = keyDown;
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        stop(client);
        keyWasDown = false;
    }

    public void changeLookDirection(double cursorDeltaX, double cursorDeltaY) {
        yaw = wrapDegrees(yaw + (float) cursorDeltaX * 0.15F);
        pitch = clamp(pitch + (float) cursorDeltaY * 0.15F, -90.0F, 90.0F);
    }

    public boolean isActive() {
        return active && isEnabled();
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public ActivationMode getActivationMode() {
        return activationMode;
    }

    public float getCameraDistance() {
        return cameraDistance;
    }

    public void setCameraDistance(float cameraDistance) {
        float next = 2.0F + clamp((cameraDistance - 2.0F) / 10.0F, 0.0F, 1.0F) * 10.0F;
        if (Math.abs(this.cameraDistance - next) < 0.001F) {
            return;
        }
        this.cameraDistance = next;
        FluxVisualsClient.requestConfigSave();
    }

    public void setActivationMode(ActivationMode activationMode) {
        if (activationMode == null || this.activationMode == activationMode) {
            return;
        }
        this.activationMode = activationMode;
        stop(MinecraftClient.getInstance());
        FluxVisualsClient.requestConfigSave();
    }

    public void cycleActivationMode() {
        setActivationMode(activationMode == ActivationMode.HOLD ? ActivationMode.TOGGLE : ActivationMode.HOLD);
    }

    private void start(MinecraftClient client) {
        if (active || client == null || client.player == null) {
            return;
        }

        previousPerspective = client.options.getPerspective();
        yaw = client.player.getYaw();
        pitch = client.player.getPitch();
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        active = true;
    }

    private void stop(MinecraftClient client) {
        if (!active) {
            return;
        }

        active = false;
        if (client != null) {
            client.options.setPerspective(previousPerspective);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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

    public enum ActivationMode {
        HOLD("Hold"),
        TOGGLE("Toggle");

        private final String label;

        ActivationMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public int getKeyBind() {
        return dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.getOrDefault("action_freelook", PremiumClickGuiRenderer.getBind(PERSPECTIVE_BIND_KEY, GLFW.GLFW_KEY_LEFT_ALT));
    }

    public void setKeyBind(int key) {
        dev.fuga.fluxvisuals.gui.modern.ModernClickGuiRenderer.MODULE_BINDS.put("action_freelook", key);
        PremiumClickGuiRenderer.setBind(PERSPECTIVE_BIND_KEY, key);
        FluxVisualsClient.requestConfigSave();
    }
}
