package dev.fuga.fluxvisuals.modules.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class Trails extends Module {
    public static final float MIN_LENGTH = 1.0F;
    public static final float MAX_LENGTH = 6.0F;
    public static final float DEFAULT_ALPHA = 0.65F;
    public static final float MAX_ALPHA = 1.0F;

    private static final int MAX_POINTS = 48;
    private static final int MAX_SAMPLES = 96;
    private static final int TRAIL_POINT_LIFETIME_TICKS = 34;
    private static final double MIN_POINT_DISTANCE_SQ = 0.030D * 0.030D;
    private static final double TELEPORT_DISTANCE_SQ = 64.0D;
    private static final double PLAYER_CLEAR_DISTANCE_SQ = 0.18D * 0.18D;
    private static final double ANCHOR_MIN_SPEED_SQ = 0.010D * 0.010D;
    private static final double BACK_ANCHOR_DISTANCE = 0.12D;
    private static final double TRAIL_BOTTOM_OFFSET = 0.10D;
    private static final double TRAIL_TOP_OFFSET = 1.58D;
    private static final RenderPipeline TRAIL_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/trails_smooth"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .build());
    private static final RenderLayer TRAIL_LAYER = RenderLayer.of(
            "fluxvisuals_trails_smooth",
            1536,
            false,
            true,
            TRAIL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false)
    );

    private final double[] x = new double[MAX_POINTS];
    private final double[] y = new double[MAX_POINTS];
    private final double[] z = new double[MAX_POINTS];
    private final int[] age = new int[MAX_POINTS];
    private final double[] sampleX = new double[MAX_SAMPLES];
    private final double[] sampleY = new double[MAX_SAMPLES];
    private final double[] sampleZ = new double[MAX_SAMPLES];
    private final float[] sampleAlpha = new float[MAX_SAMPLES];

    private int pointCount;
    private float maxLength = MAX_LENGTH;
    private float hue = 0.78F;
    private float saturation = 0.78F;
    private float value = 1.0F;
    private float alpha = DEFAULT_ALPHA;

    public Trails() {
        super("Trails", "Draws a smooth optimized trail behind the player.", ModuleCategory.VISUALS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.world == null || !client.player.isAlive()) {
            clear();
            return;
        }

        ageTrail();
        boolean compactPose = client.player.isSwimming() || client.player.isInSwimmingPose() || client.player.isGliding();
        Vec3d pos = trailAnchor(client.player, client.player.getPos(), 1.0F, compactPose);
        if (pointCount == 0) {
            appendPoint(pos.x, pos.y, pos.z);
            return;
        }

        double dx = pos.x - x[pointCount - 1];
        double dy = pos.y - y[pointCount - 1];
        double dz = pos.z - z[pointCount - 1];
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq > TELEPORT_DISTANCE_SQ) {
            clear();
            appendPoint(pos.x, pos.y, pos.z);
            return;
        }
        if (distanceSq >= MIN_POINT_DISTANCE_SQ) {
            appendPoint(pos.x, pos.y, pos.z);
        }
        trimTrail();
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        clear();
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (!isEnabled() || player == null || client.world == null || pointCount < 1
                || context.matrixStack() == null || context.consumers() == null || context.camera() == null) {
            return;
        }
        boolean firstPerson = client.options.getPerspective() == Perspective.FIRST_PERSON;
        if (firstPerson) {
            return;
        }

        float tickDelta = context.tickCounter().getTickProgress(false);
        Vec3d current = player.getLerpedPos(tickDelta);
        boolean compactPose = player.isSwimming() || player.isInSwimmingPose() || player.isGliding();
        boolean moving = player.getVelocity().horizontalLengthSquared() > ANCHOR_MIN_SPEED_SQ
                || player.getVelocity().y * player.getVelocity().y > ANCHOR_MIN_SPEED_SQ;
        Vec3d anchor = moving ? trailAnchor(player, current, tickDelta, compactPose) : current.add(0.0D, compactPose ? 0.08D : 0.0D, 0.0D);
        boolean addAnchor = !firstPerson && moving && squaredDistance(pointCount - 1, anchor) >= PLAYER_CLEAR_DISTANCE_SQ;
        int samples = buildSamples(anchor, addAnchor, !firstPerson);
        if (samples < 2) {
            return;
        }

        VertexConsumer consumer = context.consumers().getBuffer(TRAIL_LAYER);
        drawSmoothRibbon(consumer, context.matrixStack().peek(), context.camera().getPos(), samples, getColorRgb(), alpha, compactPose);
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(TRAIL_LAYER);
        }
    }

    public float getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(float maxLength) {
        float next = clamp(maxLength, MIN_LENGTH, MAX_LENGTH);
        if (Math.abs(this.maxLength - next) < 0.001F) {
            return;
        }
        this.maxLength = next;
        trimTrail();
        FluxVisualsClient.requestConfigSave();
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
        float nextHue = clamp01(hue);
        float nextSaturation = clamp01(saturation);
        float nextValue = clamp01(value);
        if (this.hue == nextHue && this.saturation == nextSaturation && this.value == nextValue) {
            return;
        }
        this.hue = nextHue;
        this.saturation = nextSaturation;
        this.value = nextValue;
        FluxVisualsClient.requestConfigSave();
    }

    public int getColorRgb() {
        return java.awt.Color.HSBtoRGB(hue, saturation, value) & 0x00FFFFFF;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        float next = clamp(alpha, 0.0F, MAX_ALPHA);
        if (Math.abs(this.alpha - next) < 0.001F) {
            return;
        }
        this.alpha = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getArgbColor() {
        return (Math.round(alpha * 255.0F) << 24) | getColorRgb();
    }

    public void setArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 255, (col >> 8) & 255, col & 255, null);
        setColor(hsb[0], hsb[1], hsb[2]);
        setAlpha(((col >>> 24) & 255) / 255.0F);
    }

    private void appendPoint(double px, double py, double pz) {
        if (pointCount >= MAX_POINTS) {
            removeFirst();
        }
        x[pointCount] = px;
        y[pointCount] = py;
        z[pointCount] = pz;
        age[pointCount] = 0;
        pointCount++;
    }

    private void ageTrail() {
        for (int i = 0; i < pointCount; i++) {
            age[i]++;
        }
        while (pointCount > 0 && age[0] > TRAIL_POINT_LIFETIME_TICKS) {
            removeFirst();
        }
    }

    private void trimTrail() {
        while (pointCount > 2 && pathLength() > maxLength) {
            removeFirst();
        }
    }

    private double pathLength() {
        double length = 0.0D;
        for (int i = 1; i < pointCount; i++) {
            double dx = x[i] - x[i - 1];
            double dy = y[i] - y[i - 1];
            double dz = z[i] - z[i - 1];
            length += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return length;
    }

    private void removeFirst() {
        if (pointCount <= 0) {
            return;
        }
        int nextCount = pointCount - 1;
        if (nextCount > 0) {
            System.arraycopy(x, 1, x, 0, nextCount);
            System.arraycopy(y, 1, y, 0, nextCount);
            System.arraycopy(z, 1, z, 0, nextCount);
            System.arraycopy(age, 1, age, 0, nextCount);
        }
        pointCount = nextCount;
    }

    private void clear() {
        pointCount = 0;
    }

    private int buildSamples(Vec3d anchor, boolean addAnchor, boolean fadeHead) {
        int controlCount = pointCount + (addAnchor ? 1 : 0);
        int sampleCount = 0;
        int segmentCount = Math.max(1, controlCount - 1);
        for (int segment = 0; segment < controlCount - 1 && sampleCount < MAX_SAMPLES; segment++) {
            double x1 = controlX(segment, anchor, addAnchor);
            double y1 = controlY(segment, anchor, addAnchor);
            double z1 = controlZ(segment, anchor, addAnchor);
            double x2 = controlX(segment + 1, anchor, addAnchor);
            double y2 = controlY(segment + 1, anchor, addAnchor);
            double z2 = controlZ(segment + 1, anchor, addAnchor);
            double distance = Math.sqrt(squaredDistance(x1, y1, z1, x2, y2, z2));
            int steps = Math.max(2, Math.min(8, (int) Math.ceil(distance * 8.0D)));
            int start = segment == 0 ? 0 : 1;
            for (int step = start; step <= steps && sampleCount < MAX_SAMPLES; step++) {
                double t = step / (double) steps;
                sampleX[sampleCount] = catmull(controlX(segment - 1, anchor, addAnchor), x1, x2, controlX(segment + 2, anchor, addAnchor), t);
                sampleY[sampleCount] = catmull(controlY(segment - 1, anchor, addAnchor), y1, y2, controlY(segment + 2, anchor, addAnchor), t);
                sampleZ[sampleCount] = catmull(controlZ(segment - 1, anchor, addAnchor), z1, z2, controlZ(segment + 2, anchor, addAnchor), t);
                float progress = (segment + (float) t) / segmentCount;
                float life = lifeAlpha(lerp(controlAge(segment, addAnchor), controlAge(segment + 1, addAnchor), t));
                float headFade = fadeHead ? 1.0F - (float) Math.pow(clamp01(progress), 5.0D) : 1.0F;
                sampleAlpha[sampleCount] = (float) Math.pow(clamp01(progress), 0.55D) * life * headFade;
                sampleCount++;
            }
        }
        return sampleCount;
    }

    private void drawSmoothRibbon(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d camera, int samples, int rgb,
                                  float opacity, boolean compactPose) {
        for (int i = 0; i < samples - 1; i++) {
            float a0 = sampleAlpha[i] * opacity;
            float a1 = sampleAlpha[i + 1] * opacity;
            if (a0 <= 0.01F && a1 <= 0.01F) {
                continue;
            }

            double dx = sampleX[i + 1] - sampleX[i];
            double dz = sampleZ[i + 1] - sampleZ[i];
            double length = Math.sqrt(dx * dx + dz * dz);
            double nx = length < 1.0E-4D ? 0.0D : -dz / length;
            double nz = length < 1.0E-4D ? 0.0D : dx / length;
            double baseWidth = compactPose ? 0.010D : 0.018D;
            double alphaWidth = compactPose ? 0.020D : 0.038D;
            double width0 = baseWidth + alphaWidth * a0;
            double width1 = baseWidth + alphaWidth * a1;
            drawVerticalQuad(consumer, entry, camera, i, nx * width0, nz * width0, -nx * width0, -nz * width0,
                    i + 1, nx * width1, nz * width1, -nx * width1, -nz * width1, rgb, a0, a1, compactPose);
        }
    }

    private void drawVerticalQuad(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d camera,
                                  int i0, double leftX0, double leftZ0, double rightX0, double rightZ0,
                                  int i1, double leftX1, double leftZ1, double rightX1, double rightZ1,
                                  int rgb, float alpha0, float alpha1, boolean compactPose) {
        float x0l = (float) (sampleX[i0] + leftX0 - camera.x);
        float x0r = (float) (sampleX[i0] + rightX0 - camera.x);
        double bottomOffset = compactPose ? 0.03D : TRAIL_BOTTOM_OFFSET;
        double topOffset = compactPose ? 0.34D : TRAIL_TOP_OFFSET;
        float y0b = (float) (sampleY[i0] + bottomOffset - camera.y);
        float y0t = (float) (sampleY[i0] + topOffset - camera.y);
        float z0l = (float) (sampleZ[i0] + leftZ0 - camera.z);
        float z0r = (float) (sampleZ[i0] + rightZ0 - camera.z);
        float x1l = (float) (sampleX[i1] + leftX1 - camera.x);
        float x1r = (float) (sampleX[i1] + rightX1 - camera.x);
        float y1b = (float) (sampleY[i1] + bottomOffset - camera.y);
        float y1t = (float) (sampleY[i1] + topOffset - camera.y);
        float z1l = (float) (sampleZ[i1] + leftZ1 - camera.z);
        float z1r = (float) (sampleZ[i1] + rightZ1 - camera.z);

        vertex(consumer, entry, x0r, y0b, z0r, rgb, alpha0 * 0.50F);
        vertex(consumer, entry, x1r, y1b, z1r, rgb, alpha1 * 0.50F);
        vertex(consumer, entry, x1l, y1t, z1l, rgb, alpha1);
        vertex(consumer, entry, x0l, y0t, z0l, rgb, alpha0);

        drawEdgeLine(consumer, entry, x0l, y0t, z0l, x1l, y1t, z1l, rgb, alpha0, alpha1);
        drawEdgeLine(consumer, entry, x0r, y0b, z0r, x1r, y1b, z1r, rgb, alpha0 * 0.72F, alpha1 * 0.72F);
    }

    private static void drawEdgeLine(VertexConsumer consumer, MatrixStack.Entry entry,
                                     float x0, float y0, float z0, float x1, float y1, float z1,
                                     int rgb, float alpha0, float alpha1) {
        float half = 0.012F;
        vertex(consumer, entry, x0, y0 - half, z0, rgb, alpha0);
        vertex(consumer, entry, x1, y1 - half, z1, rgb, alpha1);
        vertex(consumer, entry, x1, y1 + half, z1, rgb, alpha1);
        vertex(consumer, entry, x0, y0 + half, z0, rgb, alpha0);
    }

    private double controlX(int index, Vec3d anchor, boolean addAnchor) {
        int mapped = clampIndex(index, addAnchor);
        return mapped == pointCount ? anchor.x : x[mapped];
    }

    private double controlY(int index, Vec3d anchor, boolean addAnchor) {
        int mapped = clampIndex(index, addAnchor);
        return mapped == pointCount ? anchor.y : y[mapped];
    }

    private double controlZ(int index, Vec3d anchor, boolean addAnchor) {
        int mapped = clampIndex(index, addAnchor);
        return mapped == pointCount ? anchor.z : z[mapped];
    }

    private int controlAge(int index, boolean addAnchor) {
        int mapped = clampIndex(index, addAnchor);
        return mapped == pointCount ? 0 : age[mapped];
    }

    private int clampIndex(int index, boolean addAnchor) {
        int max = pointCount + (addAnchor ? 1 : 0) - 1;
        return Math.max(0, Math.min(max, index));
    }

    private double squaredDistance(int index, Vec3d pos) {
        return squaredDistance(x[index], y[index], z[index], pos.x, pos.y, pos.z);
    }

    private static Vec3d trailAnchor(PlayerEntity player, Vec3d current, float tickDelta, boolean compactPose) {
        if (compactPose) {
            Vec3d look = player.getRotationVec(tickDelta);
            if (look.lengthSquared() > 1.0E-6D) {
                double backDistance = player.isGliding() ? 0.82D : 0.54D;
                double yOffset = player.isGliding() ? 0.10D : 0.08D;
                return current.add(look.normalize().multiply(-backDistance)).add(0.0D, yOffset, 0.0D);
            }
            return current.add(0.0D, 0.08D, 0.0D);
        }

        Vec3d movement = player.getVelocity();
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (horizontal > 1.0E-4D) {
            return current.add(-movement.x / horizontal * BACK_ANCHOR_DISTANCE, 0.0D,
                    -movement.z / horizontal * BACK_ANCHOR_DISTANCE);
        }

        Vec3d look = player.getRotationVec(tickDelta);
        double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        if (lookHorizontal > 1.0E-4D) {
            return current.add(-look.x / lookHorizontal * BACK_ANCHOR_DISTANCE, 0.0D,
                    -look.z / lookHorizontal * BACK_ANCHOR_DISTANCE);
        }
        return current;
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5D * ((2.0D * p1) + (-p0 + p2) * t
                + (2.0D * p0 - 5.0D * p1 + 4.0D * p2 - p3) * t2
                + (-p0 + 3.0D * p1 - 3.0D * p2 + p3) * t3);
    }

    private static float lifeAlpha(double age) {
        float progress = clamp01(1.0F - (float) age / TRAIL_POINT_LIFETIME_TICKS);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static double lerp(double from, double to, double delta) {
        return from + (to - from) * delta;
    }

    private static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        consumer.vertex(entry, x, y, z)
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
