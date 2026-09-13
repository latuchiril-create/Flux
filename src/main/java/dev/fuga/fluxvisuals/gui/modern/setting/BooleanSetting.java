package dev.fuga.fluxvisuals.gui.modern.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BooleanSetting extends Setting<Boolean> {
    public float toggleAnim;

    public BooleanSetting(String name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        super(name, getter, setter);
        this.toggleAnim = Boolean.TRUE.equals(getter.get()) ? 1.0F : 0.0F;
    }

    public void toggle() {
        set(!get());
    }
}
