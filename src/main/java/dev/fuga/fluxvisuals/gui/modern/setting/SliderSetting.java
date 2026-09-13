package dev.fuga.fluxvisuals.gui.modern.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SliderSetting extends Setting<Float> {
    private final float min;
    private final float max;
    private final float step;
    private final String unit;
    public boolean dragging;

    public SliderSetting(String name, float min, float max, float step, String unit,
                         Supplier<Float> getter, Consumer<Float> setter) {
        super(name, getter, setter);
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    public float getStep() {
        return step;
    }

    public String getUnit() {
        return unit;
    }

    public float getNormalized() {
        return (get() - min) / (max - min);
    }

    public void setNormalized(float normalized) {
        float clamped = Math.max(0.0F, Math.min(1.0F, normalized));
        float value = min + clamped * (max - min);
        if (step > 0.0F) {
            value = Math.round(value / step) * step;
        }
        set(Math.max(min, Math.min(max, value)));
    }

    public String getValueString() {
        float val = get();
        if (step >= 1.0F) {
            return Math.round(val) + (unit.isEmpty() ? "" : unit);
        } else {
            return String.format(java.util.Locale.US, "%.1f", val) + (unit.isEmpty() ? "" : unit);
        }
    }
}
