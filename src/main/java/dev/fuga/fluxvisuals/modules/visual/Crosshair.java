package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;

public final class Crosshair extends Module {
    private Preset preset = Preset.CLEAN_WHITE;
    private float hue = 0.78F;
    private float saturation = 0.62F;
    private float value = 1.0F;
    private float size = 8.0F;
    private float gap = 4.0F;
    private float thickness = 2.0F;
    private float opacity = 0.92F;
    private boolean dot = true;
    private boolean outline = true;
    private boolean redOnTarget = true;
    private boolean showInThirdPerson;
    private Shape shape = Shape.PLUS;

    public Crosshair() {
        super("Crosshair", "Custom crosshair.", ModuleCategory.VISUALS);
        applyPreset(Preset.CLEAN_WHITE, false);
    }

    public boolean shouldReplaceVanilla(MinecraftClient client) {
        return isEnabled()
                && client != null
                && client.player != null
                && client.options != null
                && (client.options.getPerspective() == Perspective.FIRST_PERSON || showInThirdPerson)
                && client.interactionManager != null
                && client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR
                && !client.inGameHud.shouldRenderCrosshair();
    }

    public void render(DrawContext context, MinecraftClient client) {
        if (!shouldReplaceVanilla(client)) {
            return;
        }

        int overrideColor = redOnTarget && client.targetedEntity instanceof LivingEntity ? 0xFFFF4040 : 0;
        draw(context, context.getScaledWindowWidth() * 0.5F, context.getScaledWindowHeight() * 0.5F, 1.0F, 1.0F, overrideColor);
    }

    public void draw(DrawContext context, float centerX, float centerY, float uiScale, float alpha) {
        draw(context, centerX, centerY, uiScale, alpha, 0);
    }

    public void draw(DrawContext context, float centerX, float centerY, float uiScale, float alpha, int overrideArgb) {
        float scaledThickness = Math.max(1.0F, thickness * uiScale);
        float scaledSize = Math.max(scaledThickness, size * uiScale);
        float scaledGap = Math.max(0.0F, gap * uiScale);
        float cx = (float) Math.floor(centerX) + 0.5F;
        float cy = (float) Math.floor(centerY) + 0.5F;
        int color = applyAlpha(overrideArgb == 0 ? getColorArgb() : overrideArgb, alpha);
        int outlineColor = applyAlpha(0xC9000000, alpha * 0.72F);

        List<RectF> parts = buildShape(cx, cy, scaledSize, scaledGap, scaledThickness, dot, shape);
        if (outline) {
            for (RectF part : parts) {
                rect(context, part.x - 1.0F, part.y - 1.0F, part.w + 2.0F, part.h + 2.0F, outlineColor);
            }
        }
        for (RectF part : parts) {
            rect(context, part.x, part.y, part.w, part.h, color);
        }
    }

    public int getColorRgb() {
        return java.awt.Color.HSBtoRGB(hue, saturation, value) & 0x00FFFFFF;
    }

    public int getColorArgb() {
        return (Math.round(opacity * 255.0F) << 24) | getColorRgb();
    }

    public void setColorArgb(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 255, (col >> 8) & 255, col & 255, null);
        this.hue = clamp(hsb[0]);
        this.saturation = clamp(hsb[1]);
        this.value = clamp(hsb[2]);
        this.opacity = clamp(((col >>> 24) & 255) / 255.0F);
        markCustom();
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getValue() {
        return value;
    }

    public void setColor(float hue, float saturation, float value) {
        this.hue = clamp(hue);
        this.saturation = clamp(saturation);
        this.value = clamp(value);
        markCustom();
    }

    public Preset getPreset() {
        return preset;
    }

    public void applyPreset(Preset preset) {
        applyPreset(preset, true);
    }

    public void applyPreset(Preset preset, boolean save) {
        if (preset == null) {
            return;
        }

        this.preset = preset;
        if (preset == Preset.CUSTOM) {
            if (save) {
                FluxVisualsClient.requestConfigSave();
            }
            return;
        }
        this.hue = preset.hue;
        this.saturation = preset.saturation;
        this.value = preset.value;
        this.size = preset.size;
        this.gap = preset.gap;
        this.thickness = preset.thickness;
        this.opacity = preset.opacity;
        this.dot = preset.dot;
        this.outline = preset.outline;
        this.redOnTarget = preset.redOnTarget;
        this.shape = preset.shape;
        if (save) {
            FluxVisualsClient.requestConfigSave();
        }
    }

    public void setPresetDirect(Preset preset) {
        this.preset = preset == null ? Preset.CUSTOM : preset;
    }

    public String getShapeName() {
        return shape.name();
    }

    public void setShapeName(String name) {
        if (name == null) {
            return;
        }

        try {
            shape = Shape.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            shape = Shape.PLUS;
        }
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = clamp(size, 3.0F, 18.0F);
        markCustom();
    }

    public float getGap() {
        return gap;
    }

    public void setGap(float gap) {
        this.gap = clamp(gap, 0.0F, 12.0F);
        markCustom();
    }

    public float getThickness() {
        return thickness;
    }

    public void setThickness(float thickness) {
        this.thickness = clamp(thickness, 1.0F, 5.0F);
        markCustom();
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = clamp(opacity);
        markCustom();
    }

    public boolean isDot() {
        return dot;
    }

    public void setDot(boolean dot) {
        this.dot = dot;
        markCustom();
    }

    public boolean isOutline() {
        return outline;
    }

    public void setOutline(boolean outline) {
        this.outline = outline;
        markCustom();
    }

    public boolean isRedOnTarget() {
        return redOnTarget;
    }

    public void setRedOnTarget(boolean redOnTarget) {
        this.redOnTarget = redOnTarget;
        markCustom();
    }

    public boolean isShowInThirdPerson() {
        return showInThirdPerson;
    }

    public void setShowInThirdPerson(boolean showInThirdPerson) {
        if (this.showInThirdPerson == showInThirdPerson) {
            return;
        }

        this.showInThirdPerson = showInThirdPerson;
        FluxVisualsClient.requestConfigSave();
    }

    private void markCustom() {
        preset = Preset.CUSTOM;
        FluxVisualsClient.requestConfigSave();
    }

    private static List<RectF> buildShape(float cx, float cy, float size, float gap, float thickness, boolean dot, Shape shape) {
        List<RectF> parts = new ArrayList<>();
        switch (shape) {
            case PLUS -> {
                parts.add(centeredRect(cx, cy - gap - size + size * 0.5F, thickness, size));
                parts.add(centeredRect(cx, cy + gap + size * 0.5F, thickness, size));
                parts.add(centeredRect(cx - gap - size + size * 0.5F, cy, size, thickness));
                parts.add(centeredRect(cx + gap + size * 0.5F, cy, size, thickness));
            }
            case T -> {
                parts.add(centeredRect(cx, cy + gap + size * 0.5F, thickness, size));
                parts.add(centeredRect(cx - gap - size + size * 0.5F, cy, size, thickness));
                parts.add(centeredRect(cx + gap + size * 0.5F, cy, size, thickness));
            }
            case CORNERS -> {
                float corner = Math.max(3.0F, size * 0.58F);
                parts.add(new RectF(cx - gap - size, cy - gap - size, corner, thickness));
                parts.add(new RectF(cx - gap - size, cy - gap - size, thickness, corner));
                parts.add(new RectF(cx + gap + size - corner, cy - gap - size, corner, thickness));
                parts.add(new RectF(cx + gap + size - thickness, cy - gap - size, thickness, corner));
                parts.add(new RectF(cx - gap - size, cy + gap + size - thickness, corner, thickness));
                parts.add(new RectF(cx - gap - size, cy + gap + size - corner, thickness, corner));
                parts.add(new RectF(cx + gap + size - corner, cy + gap + size - thickness, corner, thickness));
                parts.add(new RectF(cx + gap + size - thickness, cy + gap + size - corner, thickness, corner));
            }
            case DIAMOND -> {
                parts.add(centeredRect(cx, cy - gap - size + size / 2, thickness, size));
                parts.add(centeredRect(cx, cy + gap + size / 2, thickness, size));
                parts.add(centeredRect(cx - gap - size + size / 2, cy, size, thickness));
                parts.add(centeredRect(cx + gap + size / 2, cy, size, thickness));
                parts.add(centeredRect(cx - gap - Math.max(1.0F, size * 0.5F), cy - gap - Math.max(1.0F, size * 0.5F), thickness, thickness));
                parts.add(centeredRect(cx + gap + Math.max(1.0F, size * 0.5F), cy - gap - Math.max(1.0F, size * 0.5F), thickness, thickness));
                parts.add(centeredRect(cx - gap - Math.max(1.0F, size * 0.5F), cy + gap + Math.max(1.0F, size * 0.5F), thickness, thickness));
                parts.add(centeredRect(cx + gap + Math.max(1.0F, size * 0.5F), cy + gap + Math.max(1.0F, size * 0.5F), thickness, thickness));
            }
            case RING -> {
                float pip = Math.max(thickness, size * 0.34F);
                float radius = gap + Math.max(2.0F, size * 0.72F);
                parts.add(centeredRect(cx, cy - radius, thickness, pip));
                parts.add(centeredRect(cx, cy + radius, thickness, pip));
                parts.add(centeredRect(cx - radius, cy, pip, thickness));
                parts.add(centeredRect(cx + radius, cy, pip, thickness));
            }
        }

        if (dot) {
            float dotSize = centeredDotSize(thickness);
            parts.add(centeredRect(cx, cy, dotSize, dotSize));
        }
        return parts;
    }

    private static float centeredDotSize(float thickness) {
        int size = Math.max(1, Math.round(thickness));
        return (size % 2 == 0 ? size + 1 : size);
    }

    private static RectF centeredRect(float cx, float cy, float w, float h) {
        return new RectF(cx - w * 0.5F, cy - h * 0.5F, w, h);
    }

    private static void rect(DrawContext context, float x, float y, float w, float h, int color) {
        if ((color >>> 24) == 0 || w <= 0.0F || h <= 0.0F) {
            return;
        }

        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(y);
        int x2 = (int) Math.ceil(x + w);
        int y2 = (int) Math.ceil(y + h);
        for (int py = y1; py < y2; py++) {
            float coverY = Math.max(0.0F, Math.min(py + 1.0F, y + h) - Math.max(py, y));
            if (coverY <= 0.0F) {
                continue;
            }
            for (int px = x1; px < x2; px++) {
                float coverX = Math.max(0.0F, Math.min(px + 1.0F, x + w) - Math.max(px, x));
                float coverage = coverX * coverY;
                if (coverage > 0.0F) {
                    context.fill(px, py, px + 1, py + 1, applyAlpha(color, coverage));
                }
            }
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 255) * clamp(alpha));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static float clamp(float value) {
        return MathHelper.clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return MathHelper.clamp(value, min, max);
    }

    public enum Preset {
        CUSTOM("Custom", Shape.PLUS, 0.78F, 0.62F, 1.0F, 8.0F, 4.0F, 2.0F, 0.92F, true, true, true),
        CLEAN_WHITE("Clean White", Shape.PLUS, 0.0F, 0.0F, 1.0F, 5.5F, 3.0F, 1.15F, 0.86F, false, true, true),
        SOFT_CYAN("Soft Cyan", Shape.PLUS, 0.50F, 0.42F, 1.0F, 6.0F, 3.5F, 1.25F, 0.9F, true, true, true),
        LIME_DOT("Lime Dot", Shape.PLUS, 0.32F, 0.58F, 1.0F, 4.5F, 3.0F, 1.0F, 0.88F, true, true, true),
        T_SHAPE("T-Shape", Shape.T, 0.35F, 0.52F, 1.0F, 7.0F, 4.0F, 1.35F, 0.9F, false, true, true),
        COMPACT_RED("Compact Red", Shape.PLUS, 0.0F, 0.62F, 1.0F, 5.0F, 2.5F, 1.1F, 0.88F, false, true, true),
        SKY_CORNERS("Sky Corners", Shape.CORNERS, 0.55F, 0.36F, 1.0F, 7.0F, 4.0F, 1.2F, 0.86F, false, true, true),
        MONO_THIN("Mono Thin", Shape.PLUS, 0.0F, 0.0F, 0.92F, 6.0F, 4.0F, 1.0F, 0.76F, false, false, false),
        FOCUS_GREEN("Focus Green", Shape.PLUS, 0.37F, 0.68F, 0.94F, 6.5F, 3.0F, 1.45F, 0.92F, true, true, true);

        private final String label;
        private final Shape shape;
        private final float hue;
        private final float saturation;
        private final float value;
        private final float size;
        private final float gap;
        private final float thickness;
        private final float opacity;
        private final boolean dot;
        private final boolean outline;
        private final boolean redOnTarget;

        Preset(String label, Shape shape, float hue, float saturation, float value, float size, float gap,
               float thickness, float opacity, boolean dot, boolean outline, boolean redOnTarget) {
            this.label = label;
            this.shape = shape;
            this.hue = hue;
            this.saturation = saturation;
            this.value = value;
            this.size = size;
            this.gap = gap;
            this.thickness = thickness;
            this.opacity = opacity;
            this.dot = dot;
            this.outline = outline;
            this.redOnTarget = redOnTarget;
        }

        public String label() {
            return label;
        }
    }

    private enum Shape {
        PLUS,
        T,
        CORNERS,
        DIAMOND,
        RING
    }

    private record RectF(float x, float y, float w, float h) {
    }
}
