package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.util.EntityRenderDispatcherAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class HitboxCustomizer extends Module {
    private float outlineHue = 0.02F;
    private float outlineSaturation = 0.95F;
    private float outlineValue = 1.0F;
    private float outlineAlpha = 1.0F;
    private float fillHue = 0.02F;
    private float fillSaturation = 0.80F;
    private float fillValue = 1.0F;
    private float fillAlpha = 0.18F;
    private float lineThickness = 1.8F;
    private boolean alwaysShow;
    private boolean fillEnabled = true;
    private boolean showLookVector;
    private boolean includeInvisible;
    private boolean includeSelf;
    private boolean cornersOnly;
    private final EnumSet<TargetType> enabledTargets = EnumSet.allOf(TargetType.class);

    public HitboxCustomizer() {
        super("Hitbox Customizer", "Draws configurable hitboxes around entities.", ModuleCategory.VISUALS);
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client == null || client.world == null || context.matrixStack() == null || context.consumers() == null) {
            return;
        }
        if (!shouldRenderNow(client)) {
            return;
        }

        AbstractClientPlayerEntity self = client.player;
        Vec3d cameraPos = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickProgress(false);

        List<Box> boxes = new ArrayList<>();
        RenderLayer fillLayer = RenderLayer.getDebugFilledBox();
        VertexConsumer fillConsumer = fillEnabled ? context.consumers().getBuffer(fillLayer) : null;
        MatrixStack matrices = context.matrixStack();

        for (Entity entity : client.world.getEntities()) {
            if (!shouldRenderEntity(client, entity, self)) {
                continue;
            }

            Box box = interpolatedBox(entity, cameraPos, tickDelta).expand(0.02D);
            boxes.add(box);
            if (fillConsumer != null && !box.contains(cameraPos)) {
                if (box.getLengthX() > 0.01D && box.getLengthY() > 0.01D && box.getLengthZ() > 0.01D) {
                    drawFilledBox(matrices, fillConsumer, box, getFillArgbColor());
                }
            }
        }

        if (fillConsumer != null && context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(fillLayer);
        }

        VertexConsumer lineConsumer = context.consumers().getBuffer(fillLayer);
        double lineOffset = fillEnabled ? 0.006D : 0.0D;
        double lineRadius = lineRadius();
        for (Box box : boxes) {
            Box lineBox = box.expand(lineOffset);
            if (cornersOnly) {
                drawBoxCorners(matrices, lineConsumer, lineBox, getOutlineArgbColor(), lineRadius);
            } else {
                drawBoxEdges(matrices, lineConsumer, lineBox, getOutlineArgbColor(), lineRadius);
            }
        }
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(fillLayer);
        }
    }

    private boolean shouldRenderNow(MinecraftClient client) {
        return alwaysShow || ((EntityRenderDispatcherAccessor) client.getEntityRenderDispatcher()).fluxvisuals$getRawRenderHitboxes();
    }

    private boolean shouldRenderEntity(MinecraftClient client, Entity entity, AbstractClientPlayerEntity self) {
        if (entity.isRemoved()) {
            return false;
        }
        if (entity instanceof LivingEntity living && !living.isAlive()) {
            return false;
        }
        if (isInvisibleEntity(entity, self)) {
            return false;
        }
        if (entity == self) {
            if (client.options.getPerspective().isFirstPerson()) {
                return false;
            }
            return includeSelf;
        }
        TargetType targetType = TargetType.of(entity);
        return targetType != null && enabledTargets.contains(targetType);
    }

    private boolean isInvisibleEntity(Entity entity, AbstractClientPlayerEntity self) {
        if (includeInvisible) {
            return false;
        }
        if (entity.isInvisible() || (self != null && entity.isInvisibleTo(self))) {
            return true;
        }
        return entity instanceof LivingEntity living && living.hasStatusEffect(StatusEffects.INVISIBILITY);
    }

    private Box interpolatedBox(Entity entity, Vec3d cameraPos, float tickDelta) {
        Vec3d currentPos = entity.getPos();
        Vec3d lerpedPos = entity.getLerpedPos(tickDelta);
        Vec3d shift = lerpedPos.subtract(currentPos).subtract(cameraPos);
        return entity.getBoundingBox().offset(shift);
    }

    private void drawBoxEdges(MatrixStack matrices, VertexConsumer consumer, Box box, int color, double radius) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        drawEdgeBox(matrices, consumer, x1, y1 - radius, z1 - radius, x2, y1 + radius, z1 + radius, color);
        drawEdgeBox(matrices, consumer, x1, y1 - radius, z2 - radius, x2, y1 + radius, z2 + radius, color);
        drawEdgeBox(matrices, consumer, x1, y2 - radius, z1 - radius, x2, y2 + radius, z1 + radius, color);
        drawEdgeBox(matrices, consumer, x1, y2 - radius, z2 - radius, x2, y2 + radius, z2 + radius, color);

        drawEdgeBox(matrices, consumer, x1 - radius, y1, z1 - radius, x1 + radius, y2, z1 + radius, color);
        drawEdgeBox(matrices, consumer, x1 - radius, y1, z2 - radius, x1 + radius, y2, z2 + radius, color);
        drawEdgeBox(matrices, consumer, x2 - radius, y1, z1 - radius, x2 + radius, y2, z1 + radius, color);
        drawEdgeBox(matrices, consumer, x2 - radius, y1, z2 - radius, x2 + radius, y2, z2 + radius, color);

        drawEdgeBox(matrices, consumer, x1 - radius, y1 - radius, z1, x1 + radius, y1 + radius, z2, color);
        drawEdgeBox(matrices, consumer, x1 - radius, y2 - radius, z1, x1 + radius, y2 + radius, z2, color);
        drawEdgeBox(matrices, consumer, x2 - radius, y1 - radius, z1, x2 + radius, y1 + radius, z2, color);
        drawEdgeBox(matrices, consumer, x2 - radius, y2 - radius, z1, x2 + radius, y2 + radius, z2, color);
    }

    private void drawBoxCorners(MatrixStack matrices, VertexConsumer consumer, Box box, int color, double radius) {
        double lenX = cornerLength(box.getLengthX());
        double lenY = cornerLength(box.getLengthY());
        double lenZ = cornerLength(box.getLengthZ());

        drawCorner(matrices, consumer, box.minX, box.minY, box.minZ, 1.0D, 1.0D, 1.0D, lenX, lenY, lenZ, radius, color);
        drawCorner(matrices, consumer, box.maxX, box.minY, box.minZ, -1.0D, 1.0D, 1.0D, lenX, lenY, lenZ, radius, color);
        drawCorner(matrices, consumer, box.minX, box.maxY, box.minZ, 1.0D, -1.0D, 1.0D, lenX, lenY, lenZ, radius, color);
        drawCorner(matrices, consumer, box.maxX, box.maxY, box.minZ, -1.0D, -1.0D, 1.0D, lenX, lenY, lenZ, radius, color);
        drawCorner(matrices, consumer, box.minX, box.minY, box.maxZ, 1.0D, 1.0D, -1.0D, lenX, lenY, lenZ, radius, color);
        drawCorner(matrices, consumer, box.maxX, box.minY, box.maxZ, -1.0D, 1.0D, -1.0D, lenX, lenY, lenZ, radius, color);
        drawCorner(matrices, consumer, box.minX, box.maxY, box.maxZ, 1.0D, -1.0D, -1.0D, lenX, lenY, lenZ, radius, color);
        drawCorner(matrices, consumer, box.maxX, box.maxY, box.maxZ, -1.0D, -1.0D, -1.0D, lenX, lenY, lenZ, radius, color);
    }

    private void drawCorner(MatrixStack matrices, VertexConsumer consumer, double x, double y, double z,
                            double xDirection, double yDirection, double zDirection,
                            double lenX, double lenY, double lenZ, double radius, int color) {
        drawEdgeBoxNormalized(matrices, consumer, x, y - radius, z - radius, x + xDirection * lenX, y + radius, z + radius, color);
        drawEdgeBoxNormalized(matrices, consumer, x - radius, y, z - radius, x + radius, y + yDirection * lenY, z + radius, color);
        drawEdgeBoxNormalized(matrices, consumer, x - radius, y - radius, z, x + radius, y + radius, z + zDirection * lenZ, color);
    }

    private void drawEdgeBoxNormalized(MatrixStack matrices, VertexConsumer consumer,
                                       double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        drawEdgeBox(matrices, consumer,
                Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ), color);
    }

    private void drawEdgeBox(MatrixStack matrices, VertexConsumer consumer,
                             double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        drawFilledBox(matrices, consumer, new Box(minX, minY, minZ, maxX, maxY, maxZ), color);
    }

    private void drawFilledBox(MatrixStack matrices, VertexConsumer consumer, Box box, int color) {
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        float a = (color >>> 24) / 255.0F;
        VertexRendering.drawFilledBox(matrices, consumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
    }

    public float getOutlineHue() {
        return outlineHue;
    }

    public float getOutlineSaturation() {
        return outlineSaturation;
    }

    public float getOutlineValue() {
        return outlineValue;
    }

    public void setOutlineColor(float hue, float saturation, float value) {
        float nextHue = clamp01(hue);
        float nextSaturation = clamp01(saturation);
        float nextValue = clamp01(value);
        if (outlineHue == nextHue && outlineSaturation == nextSaturation && outlineValue == nextValue) {
            return;
        }

        outlineHue = nextHue;
        outlineSaturation = nextSaturation;
        outlineValue = nextValue;
        FluxVisualsClient.requestConfigSave();
    }

    public float getFillHue() {
        return fillHue;
    }

    public float getFillSaturation() {
        return fillSaturation;
    }

    public float getFillValue() {
        return fillValue;
    }

    public void setFillColor(float hue, float saturation, float value) {
        float nextHue = clamp01(hue);
        float nextSaturation = clamp01(saturation);
        float nextValue = clamp01(value);
        if (fillHue == nextHue && fillSaturation == nextSaturation && fillValue == nextValue) {
            return;
        }

        fillHue = nextHue;
        fillSaturation = nextSaturation;
        fillValue = nextValue;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isFillEnabled() {
        return fillEnabled;
    }

    public boolean isAlwaysShow() {
        return alwaysShow;
    }

    public void setAlwaysShow(boolean alwaysShow) {
        if (this.alwaysShow == alwaysShow) {
            return;
        }

        this.alwaysShow = alwaysShow;
        FluxVisualsClient.requestConfigSave();
    }

    public void setFillEnabled(boolean fillEnabled) {
        if (this.fillEnabled == fillEnabled) {
            return;
        }

        this.fillEnabled = fillEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public float getFillAlpha() {
        return fillAlpha;
    }

    public void setFillAlpha(float fillAlpha) {
        float next = clamp01(fillAlpha);
        if (Math.abs(this.fillAlpha - next) < 0.001F) {
            return;
        }

        this.fillAlpha = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getLineThickness() {
        return lineThickness;
    }

    private double lineRadius() {
        return 0.004D + (lineThickness - 1.0F) * 0.0045D;
    }

    public void setLineThickness(float lineThickness) {
        float next = 1.0F + clamp01((lineThickness - 1.0F) / 4.0F) * 4.0F;
        if (Math.abs(this.lineThickness - next) < 0.001F) {
            return;
        }

        this.lineThickness = next;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isShowLookVector() {
        return showLookVector;
    }

    public void setShowLookVector(boolean showLookVector) {
        if (this.showLookVector == showLookVector) {
            return;
        }

        this.showLookVector = showLookVector;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isPlayersOnly() {
        return enabledTargets.size() == 1 && enabledTargets.contains(TargetType.PLAYERS);
    }

    public void setPlayersOnly(boolean playersOnly) {
        enabledTargets.clear();
        if (playersOnly) {
            enabledTargets.add(TargetType.PLAYERS);
        } else {
            enabledTargets.addAll(EnumSet.allOf(TargetType.class));
        }
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isTargetEnabled(TargetType targetType) {
        return targetType != null && enabledTargets.contains(targetType);
    }

    public void setTargetEnabled(TargetType targetType, boolean enabled) {
        if (targetType == null) {
            return;
        }
        if (enabled) {
            if (!enabledTargets.add(targetType)) {
                return;
            }
        } else {
            if (enabledTargets.size() <= 1 || !enabledTargets.remove(targetType)) {
                return;
            }
        }
        FluxVisualsClient.requestConfigSave();
    }

    public String enabledTargetNames() {
        StringBuilder builder = new StringBuilder();
        for (TargetType type : TargetType.values()) {
            if (enabledTargets.contains(type)) {
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(type.name());
            }
        }
        return builder.toString();
    }

    public void setEnabledTargetNames(String names) {
        if (names == null || names.isBlank()) {
            return;
        }
        EnumSet<TargetType> next = EnumSet.noneOf(TargetType.class);
        for (String raw : names.split(",")) {
            try {
                next.add(TargetType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale config values.
            }
        }
        if (next.isEmpty() || next.equals(enabledTargets)) {
            return;
        }
        enabledTargets.clear();
        enabledTargets.addAll(next);
        FluxVisualsClient.requestConfigSave();
    }

    public java.util.Set<String> getTargets() {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (TargetType type : enabledTargets) {
            set.add(type.label());
        }
        return set;
    }

    public void setTargets(java.util.Set<String> targets) {
        if (targets == null || targets.isEmpty()) return;
        enabledTargets.clear();
        for (TargetType type : TargetType.values()) {
            if (targets.contains(type.name()) || targets.contains(type.label())) {
                enabledTargets.add(type);
            }
        }
        if (enabledTargets.isEmpty()) enabledTargets.add(TargetType.PLAYERS);
        FluxVisualsClient.requestConfigSave();
    }

    public String targetSummary() {
        return "\u0412\u044b\u0431\u0440\u0430\u043d\u043e " + Math.max(1, enabledTargets.size());
    }

    public boolean isIncludeInvisible() {
        return false;
    }

    public void setIncludeInvisible(boolean includeInvisible) {
        if (!this.includeInvisible) {
            return;
        }

        this.includeInvisible = false;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isIncludeSelf() {
        return includeSelf;
    }

    public void setIncludeSelf(boolean includeSelf) {
        if (this.includeSelf == includeSelf) {
            return;
        }

        this.includeSelf = includeSelf;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isCornersOnly() {
        return cornersOnly;
    }

    public void setCornersOnly(boolean cornersOnly) {
        if (this.cornersOnly == cornersOnly) {
            return;
        }

        this.cornersOnly = cornersOnly;
        FluxVisualsClient.requestConfigSave();
    }

    public int getOutlineColorRgb() {
        return java.awt.Color.HSBtoRGB(outlineHue, outlineSaturation, outlineValue) & 0x00FFFFFF;
    }

    public int getFillColorRgb() {
        return java.awt.Color.HSBtoRGB(fillHue, fillSaturation, fillValue) & 0x00FFFFFF;
    }

    public float getOutlineAlpha() {
        return outlineAlpha;
    }

    public void setOutlineAlpha(float outlineAlpha) {
        float next = clamp01(outlineAlpha);
        if (Math.abs(this.outlineAlpha - next) < 0.001F) {
            return;
        }
        this.outlineAlpha = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getOutlineArgbColor() {
        return (Math.round(outlineAlpha * 255.0F) << 24) | getOutlineColorRgb();
    }

    public void setOutlineArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        this.outlineHue = hsb[0];
        this.outlineSaturation = hsb[1];
        this.outlineValue = hsb[2];
        this.outlineAlpha = ((col >>> 24) & 0xFF) / 255.0F;
        FluxVisualsClient.requestConfigSave();
    }

    public int getFillArgbColor() {
        return (Math.round(fillAlpha * 255.0F) << 24) | getFillColorRgb();
    }

    public void setFillArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        this.fillHue = hsb[0];
        this.fillSaturation = hsb[1];
        this.fillValue = hsb[2];
        this.fillAlpha = ((col >>> 24) & 0xFF) / 255.0F;
        FluxVisualsClient.requestConfigSave();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static double cornerLength(double length) {
        return Math.min(Math.max(0.03D, length * 0.28D), length * 0.45D);
    }

    public enum TargetType {
        PLAYERS("\u0418\u0433\u0440\u043e\u043a\u0438", "\u0418\u0433\u0440."),
        MOBS("\u041c\u043e\u0431\u044b", "\u041c\u043e\u0431\u044b"),
        ITEMS("\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b", "\u041f\u0440\u0435\u0434."),
        PROJECTILES("\u0421\u043d\u0430\u0440\u044f\u0434\u044b", "\u0421\u043d\u0430\u0440.");

        private final String label;
        private final String shortLabel;

        TargetType(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }

        public String label() {
            return label;
        }

        public String shortLabel() {
            return shortLabel;
        }

        private static TargetType of(Entity entity) {
            if (entity instanceof AbstractClientPlayerEntity) {
                return PLAYERS;
            }
            if (entity instanceof ItemEntity || entity instanceof TridentEntity || entity instanceof ExperienceOrbEntity) {
                return ITEMS;
            }
            if (entity instanceof ProjectileEntity) {
                return PROJECTILES;
            }
            if (entity instanceof LivingEntity) {
                return MOBS;
            }
            return null;
        }
    }
}
