package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;

public final class AspectRatio extends Module {
    public enum Preset {
        ONE_ONE("1:1", 1.0F),
        FIVE_FOUR("5:4", 5.0F / 4.0F),
        FOUR_THREE("4:3", 4.0F / 3.0F),
        SIXTEEN_TEN("16:10", 16.0F / 10.0F),
        SIXTEEN_NINE("16:9", 16.0F / 9.0F),
        THREE_TWO("3:2", 3.0F / 2.0F),
        TWENTY_ONE_NINE("21:9", 21.0F / 9.0F),
        CUSTOM("Custom", -1.0F);

        private final String label;
        private final float ratio;

        Preset(String label, float ratio) {
            this.label = label;
            this.ratio = ratio;
        }

        public String label() {
            return label;
        }

        public float ratio() {
            return ratio;
        }
    }

    private Preset preset = Preset.SIXTEEN_NINE;
    private float customRatio = 16.0F / 9.0F;

    public AspectRatio() {
        super("AspectRatio", "Stretches the game projection matrix.", ModuleCategory.VISUALS);
    }

    public Preset getPreset() {
        return preset;
    }

    public void setPreset(Preset preset) {
        Preset next = preset == null ? Preset.SIXTEEN_NINE : preset;
        if (this.preset == next) {
            return;
        }

        this.preset = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getCustomRatio() {
        return customRatio;
    }

    public void setCustomRatio(float customRatio) {
        float clamped = Math.max(1.0F, Math.min(2.5F, customRatio));
        if (this.customRatio == clamped) {
            return;
        }

        this.customRatio = clamped;
        FluxVisualsClient.requestConfigSave();
    }

    public float getRatio() {
        return preset == Preset.CUSTOM ? customRatio : preset.ratio();
    }

    public String getActiveLabel() {
        return preset == Preset.CUSTOM ? String.format(java.util.Locale.ROOT, "%.2f", customRatio) : preset.label();
    }

    public float getProjectionScale(float actualAspect) {
        if (!isEnabled() || actualAspect <= 0.0F) {
            return 1.0F;
        }

        float targetAspect = Math.max(0.1F, getRatio());
        return actualAspect / targetAspect;
    }
}
