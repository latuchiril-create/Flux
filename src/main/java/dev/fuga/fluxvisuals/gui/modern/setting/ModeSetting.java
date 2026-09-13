package dev.fuga.fluxvisuals.gui.modern.setting;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ModeSetting extends Setting<String> {
    private final List<String> modes;
    public boolean expanded;

    public ModeSetting(String name, List<String> modes, Supplier<String> getter, Consumer<String> setter) {
        super(name, getter, setter);
        this.modes = modes;
    }

    public List<String> getModes() {
        return modes;
    }

    public void cycle() {
        if (modes.isEmpty()) {
            return;
        }
        int currentIndex = modes.indexOf(get());
        int nextIndex = (currentIndex + 1) % modes.size();
        set(modes.get(nextIndex));
    }
}
