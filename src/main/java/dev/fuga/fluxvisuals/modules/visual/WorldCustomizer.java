package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;

public final class WorldCustomizer extends Module {
    public enum TimePreset {
        NIGHT("\u041d\u043e\u0447\u044c", 13000L),
        MIDNIGHT("\u041f\u043e\u043b\u043d\u043e\u0447\u044c", 18000L),
        DAY("\u0414\u0435\u043d\u044c", 1000L),
        SUNSET("\u0417\u0430\u043a\u0430\u0442", 12000L),
        CUSTOM("\u041a\u0430\u0441\u0442\u043e\u043c", -1L);

        private final String label;
        private final long timeOfDay;

        TimePreset(String label, long timeOfDay) {
            this.label = label;
            this.timeOfDay = timeOfDay;
        }

        public String label() {
            return label;
        }

        public long timeOfDay() {
            return timeOfDay;
        }
    }

    private TimePreset timePreset = TimePreset.DAY;
    private long customTime = 1000L;
    private boolean customFogEnabled;
    private float fogDistance = 128.0F;
    private float fogHue = 0.58F;
    private float fogSaturation = 0.18F;
    private float fogValue = 0.92F;

    public WorldCustomizer() {
        super("World Customizer", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0442\u0443\u043c\u0430\u043d\u0430 \u0438 \u0432\u0440\u0435\u043c\u0435\u043d\u0438.", ModuleCategory.VISUALS);
    }

    public TimePreset getTimePreset() {
        return timePreset;
    }

    public void setTimePreset(TimePreset timePreset) {
        TimePreset next = timePreset == null ? TimePreset.DAY : timePreset;
        if (this.timePreset == next) {
            return;
        }

        this.timePreset = next;
        FluxVisualsClient.requestConfigSave();
    }

    public long getCustomTime() {
        return customTime;
    }

    public void setCustomTime(long customTime) {
        long clamped = Math.max(0L, Math.min(23999L, customTime));
        if (this.customTime == clamped) {
            return;
        }

        this.customTime = clamped;
        FluxVisualsClient.requestConfigSave();
    }

    public long getEffectiveTime() {
        return timePreset == TimePreset.CUSTOM ? customTime : timePreset.timeOfDay();
    }

    public float getEffectiveSkyAngle(float tickProgress) {
        double dayTime = Math.floorMod(getEffectiveTime(), 24000L) + tickProgress;
        float angle = (float) (dayTime / 24000.0D - 0.25D);
        if (angle < 0.0F) {
            angle += 1.0F;
        }
        if (angle > 1.0F) {
            angle -= 1.0F;
        }

        float rawAngle = angle;
        angle = 1.0F - (float) ((Math.cos(angle * Math.PI) + 1.0D) / 2.0D);
        return rawAngle + (angle - rawAngle) / 3.0F;
    }

    public boolean isCustomFogEnabled() {
        return customFogEnabled;
    }

    public void setCustomFogEnabled(boolean customFogEnabled) {
        if (this.customFogEnabled == customFogEnabled) {
            return;
        }

        this.customFogEnabled = customFogEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public float getFogDistance() {
        return fogDistance;
    }

    public void setFogDistance(float fogDistance) {
        float clamped = Math.max(8.0F, Math.min(1024.0F, fogDistance));
        if (Math.abs(this.fogDistance - clamped) < 0.001F) {
            return;
        }

        this.fogDistance = clamped;
        FluxVisualsClient.requestConfigSave();
    }

    public float getFogHue() {
        return fogHue;
    }

    public float getFogSaturation() {
        return fogSaturation;
    }

    public float getFogValue() {
        return fogValue;
    }

    public int getFogColorRgb() {
        return 0x00FFFFFF & java.awt.Color.HSBtoRGB(fogHue, fogSaturation, fogValue);
    }

    public int getFogArgbColor() {
        return 0xFF000000 | getFogColorRgb();
    }

    public void setFogArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 255, (col >> 8) & 255, col & 255, null);
        setFogColor(hsb[0], hsb[1], hsb[2]);
    }

    public float getFogRed() {
        return ((getFogColorRgb() >> 16) & 0xFF) / 255.0F;
    }

    public float getFogGreen() {
        return ((getFogColorRgb() >> 8) & 0xFF) / 255.0F;
    }

    public float getFogBlue() {
        return (getFogColorRgb() & 0xFF) / 255.0F;
    }

    public void setFogColor(float hue, float saturation, float value) {
        float clampedHue = Math.max(0.0F, Math.min(1.0F, hue));
        float clampedSaturation = Math.max(0.0F, Math.min(1.0F, saturation));
        float clampedValue = Math.max(0.0F, Math.min(1.0F, value));
        if (Math.abs(fogHue - clampedHue) < 0.001F
                && Math.abs(fogSaturation - clampedSaturation) < 0.001F
                && Math.abs(fogValue - clampedValue) < 0.001F) {
            return;
        }

        fogHue = clampedHue;
        fogSaturation = clampedSaturation;
        fogValue = clampedValue;
        FluxVisualsClient.requestConfigSave();
    }
}
