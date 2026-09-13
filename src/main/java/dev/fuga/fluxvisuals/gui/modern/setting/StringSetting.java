package dev.fuga.fluxvisuals.gui.modern.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class StringSetting extends Setting<String> {
    private final String placeholder;
    public boolean focused = false;

    public StringSetting(String name, String placeholder, Supplier<String> getter, Consumer<String> setter) {
        super(name, getter, setter);
        this.placeholder = placeholder;
    }

    public String getPlaceholder() {
        return placeholder;
    }
}
