package dev.fuga.fluxvisuals.render.util;

import java.awt.Color;

/**
 * High-performance color helper for Render2D.
 */
public final class RenderColor {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int TRANSPARENT = 0x00000000;

    private RenderColor() {
    }

    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    public static int rgba(float r, float g, float b, float a) {
        return rgba(
                Math.round(Math.clamp(r, 0.0F, 1.0F) * 255.0F),
                Math.round(Math.clamp(g, 0.0F, 1.0F) * 255.0F),
                Math.round(Math.clamp(b, 0.0F, 1.0F) * 255.0F),
                Math.round(Math.clamp(a, 0.0F, 1.0F) * 255.0F)
        );
    }

    public static int getRed(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int getGreen(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int getBlue(int argb) {
        return argb & 0xFF;
    }

    public static int getAlpha(int argb) {
        return (argb >> 24) & 0xFF;
    }

    public static float getRedFloat(int argb) {
        return getRed(argb) / 255.0F;
    }

    public static float getGreenFloat(int argb) {
        return getGreen(argb) / 255.0F;
    }

    public static float getBlueFloat(int argb) {
        return getBlue(argb) / 255.0F;
    }

    public static float getAlphaFloat(int argb) {
        return getAlpha(argb) / 255.0F;
    }

    public static void toFloats(int argb, float[] dest) {
        dest[0] = getRedFloat(argb);
        dest[1] = getGreenFloat(argb);
        dest[2] = getBlueFloat(argb);
        dest[3] = getAlphaFloat(argb);
    }

    public static void toFloats(Color color, float[] dest) {
        if (color == null) {
            dest[0] = 1.0F;
            dest[1] = 1.0F;
            dest[2] = 1.0F;
            dest[3] = 1.0F;
            return;
        }
        dest[0] = color.getRed() / 255.0F;
        dest[1] = color.getGreen() / 255.0F;
        dest[2] = color.getBlue() / 255.0F;
        dest[3] = color.getAlpha() / 255.0F;
    }

    public static int withAlpha(int argb, float alpha) {
        int a = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static Color withAlpha(Color color, float alpha) {
        int a = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    public static int multiplyAlpha(int argb, float alphaFactor) {
        int originalA = getAlpha(argb);
        int a = Math.round(Math.clamp(originalA * alphaFactor, 0.0F, 255.0F));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int interpolate(int start, int end, float factor) {
        float f = Math.clamp(factor, 0.0F, 1.0F);
        int a = Math.round(getAlpha(start) + f * (getAlpha(end) - getAlpha(start)));
        int r = Math.round(getRed(start) + f * (getRed(end) - getRed(start)));
        int g = Math.round(getGreen(start) + f * (getGreen(end) - getGreen(start)));
        int b = Math.round(getBlue(start) + f * (getBlue(end) - getBlue(start)));
        return rgba(r, g, b, a);
    }

    public static Color interpolate(Color start, Color end, float factor) {
        float f = Math.clamp(factor, 0.0F, 1.0F);
        int a = Math.round(start.getAlpha() + f * (end.getAlpha() - start.getAlpha()));
        int r = Math.round(start.getRed() + f * (end.getRed() - start.getRed()));
        int g = Math.round(start.getGreen() + f * (end.getGreen() - start.getGreen()));
        int b = Math.round(start.getBlue() + f * (end.getBlue() - start.getBlue()));
        return new Color(r, g, b, a);
    }

    public static int rainbow(float speed, int offset, float saturation, float brightness) {
        double time = (System.currentTimeMillis() * (double) speed + offset) % 360.0;
        float hue = (float) (time / 360.0);
        return Color.HSBtoRGB(hue, saturation, brightness);
    }
}
