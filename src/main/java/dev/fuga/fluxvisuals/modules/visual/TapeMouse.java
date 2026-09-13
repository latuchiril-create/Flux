package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.mixin.MinecraftClientAccessor;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class TapeMouse extends Module {
    public enum ButtonMode {
        LEFT("Left"),
        RIGHT("Right");

        private final String label;

        ButtonMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private ButtonMode buttonMode = ButtonMode.LEFT;
    private int delayMs = 100;
    private long lastClickAt;

    public TapeMouse() {
        super("TapeMouse", "Repeats selected mouse action with configurable delay.", ModuleCategory.UTILS);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        lastClickAt = 0L;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.interactionManager == null || client.getWindow() == null) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastClickAt < delayMs) {
            return;
        }
        lastClickAt = now;

        if (buttonMode == ButtonMode.LEFT) {
            clickKey(client.options.attackKey, true);
            ((MinecraftClientAccessor) client).fluxvisuals$doAttack();
            clickKey(client.options.attackKey, false);
        } else {
            clickKey(client.options.useKey, true);
            ((MinecraftClientAccessor) client).fluxvisuals$doItemUse();
            clickKey(client.options.useKey, false);
        }
    }

    private static void clickKey(KeyBinding keyBinding, boolean pressed) {
        if (keyBinding != null && keyBinding.getDefaultKey() != null) {
            KeyBinding.setKeyPressed(keyBinding.getDefaultKey(), pressed);
        }
    }

    public ButtonMode getButtonMode() {
        return buttonMode;
    }

    public void setButtonMode(ButtonMode buttonMode) {
        ButtonMode next = buttonMode == null ? ButtonMode.LEFT : buttonMode;
        if (this.buttonMode == next) {
            return;
        }
        this.buttonMode = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(int delayMs) {
        int next = Math.max(10, Math.min(1000, delayMs));
        if (this.delayMs == next) {
            return;
        }
        this.delayMs = next;
        FluxVisualsClient.requestConfigSave();
    }
}
