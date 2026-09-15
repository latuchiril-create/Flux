package dev.fuga.fluxvisuals.gui.modern.setting;

public final class ActionSetting extends Setting<Void> {
    private final String buttonText;
    private final Runnable action;

    public ActionSetting(String name, String buttonText, Runnable action) {
        super(name, () -> null, v -> {});
        this.buttonText = buttonText;
        this.action = action;
    }

    public String getButtonText() {
        return buttonText;
    }

    public void run() {
        if (action != null) {
            action.run();
        }
    }
}
