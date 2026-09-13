package dev.fuga.fluxvisuals.modules;

public enum ModuleCategory {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    VISUALS("Visuals"),
    PLAYER("Player"),
    HUD("HUD"),
    UTILS("Utils"),
    SETTINGS("Settings");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
