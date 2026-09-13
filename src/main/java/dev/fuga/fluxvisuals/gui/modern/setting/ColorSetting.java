package dev.fuga.fluxvisuals.gui.modern.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ColorSetting extends Setting<Integer> {
    public boolean pickerOpen;
    public float hue = 0.65F;
    public float saturation = 0.8F;
    public float brightness = 0.9F;

    public ColorSetting(String name, Supplier<Integer> getter, Consumer<Integer> setter) {
        super(name, getter, setter);
        int color = getter.get();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float[] hsb = java.awt.Color.RGBtoHSB(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    public void updateFromHsb() {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        int alpha = (get() >> 24) & 0xFF;
        if (alpha == 0) alpha = 255;
        set((alpha << 24) | rgb);
    }
}
