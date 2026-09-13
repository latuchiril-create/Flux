package dev.fuga.fluxvisuals.modules;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.minecraft.client.MinecraftClient;

public abstract class Module {
    private final String name;
    private final String description;
    private final ModuleCategory category;
    private boolean enabled;

    protected Module(String name, String description, ModuleCategory category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean enabled) {
        setEnabled(enabled, true);
    }

    /** Changes a session-local module state without changing the saved global configuration. */
    public final void setEnabledSilently(boolean enabled) {
        setEnabled(enabled, false);
    }

    private void setEnabled(boolean enabled, boolean persist) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;
        if (enabled) {
            onEnable(MinecraftClient.getInstance());
        } else {
            onDisable(MinecraftClient.getInstance());
        }
        if (persist) {
            if (shouldNotifyWatermark()) {
                FluxVisualsClient.MODULE_MANAGER.getWatermark().showModuleToast(name, enabled);
            }
            FluxVisualsClient.requestConfigSave();
        }
    }

    public void onTick(MinecraftClient client) {
    }

    /** Called once per rendered frame (FPS-based). deltaSeconds is real frame time, clamped. */
    public void onFrame(MinecraftClient client, float deltaSeconds) {
    }

    protected void onEnable(MinecraftClient client) {
    }

    protected void onDisable(MinecraftClient client) {
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean shouldNotifyWatermark() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        if (FluxVisualsClient.CONFIG_MANAGER != null && FluxVisualsClient.CONFIG_MANAGER.isLoading()) {
            return false;
        }
        return FluxVisualsClient.MODULE_MANAGER != null && FluxVisualsClient.MODULE_MANAGER.getWatermark() != null;
    }
}
