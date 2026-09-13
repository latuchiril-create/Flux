package dev.fuga.fluxvisuals.modules.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public final class BlockOverlay extends Module {
    private static final double EDGE_OFFSET = 0.003D;
    private static final Map<ShaderType, RenderPipeline> SHADER_DEPTH_PIPELINES = new EnumMap<>(ShaderType.class);
    private static final Map<ShaderType, RenderPipeline> SHADER_NO_DEPTH_PIPELINES = new EnumMap<>(ShaderType.class);
    private static final Map<String, RenderLayer> SHADER_LAYERS = new java.util.HashMap<>();
    private static final RenderPipeline DEPTH_COLOR_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/block_overlay_depth"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .build());
    private static final RenderPipeline NO_DEPTH_COLOR_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/block_overlay_no_depth"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .build());
    private static final RenderLayer DEPTH_COLOR_LAYER = RenderLayer.of(
            "fluxvisuals_block_overlay_depth",
            1536,
            false,
            true,
            DEPTH_COLOR_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false)
    );
    private static final RenderLayer NO_DEPTH_COLOR_LAYER = RenderLayer.of(
            "fluxvisuals_block_overlay_no_depth",
            1536,
            false,
            true,
            NO_DEPTH_COLOR_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false)
    );

    private float outlineHue = 0.57F;
    private float outlineSaturation = 0.88F;
    private float outlineValue = 1.0F;
    private float outlineAlpha = 1.0F;
    private float fillHue = 0.57F;
    private float fillSaturation = 0.78F;
    private float fillValue = 1.0F;
    private float fillAlpha = 0.18F;
    private float lineThickness = 1.6F;
    private boolean fillEnabled = true;
    private boolean throughWalls;
    private boolean smoothSwitch = true;
    private FillMode fillMode = FillMode.FILL;
    private ShaderType shaderType = ShaderType.AURA;
    private float animationSpeed = 1.0F;
    private double visualX;
    private double visualY;
    private double visualZ;
    private BlockPos visualTargetPos;
    private long lastRenderNanos;

    public BlockOverlay() {
        super("BlockOverlay", "Draws a configurable overlay on the targeted block.", ModuleCategory.VISUALS);
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client == null || client.world == null || client.crosshairTarget == null
                || context.matrixStack() == null || context.consumers() == null) {
            return;
        }
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        VoxelShape shape = state.getOutlineShape(client.world, pos);
        if (shape.isEmpty()) {
            shape = state.getCollisionShape(client.world, pos);
        }
        if (shape.isEmpty()) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        Vec3d visualPos = visualPos(pos);
        MatrixStack matrices = context.matrixStack();
        List<Box> boxes = shape.getBoundingBoxes();

        if (fillEnabled) {
            RenderLayer fillLayer = fillLayer();
            VertexConsumer fillConsumer = context.consumers().getBuffer(fillLayer);
            for (Box local : boxes) {
                Box box = local.offset(visualPos).offset(-camera.x, -camera.y, -camera.z).expand(EDGE_OFFSET);
                drawFilledBox(matrices, fillConsumer, box, getFillArgbColor());
            }
            drawLayer(context, fillLayer);
        }

        RenderLayer lineLayer = lineLayer();
        VertexConsumer lineConsumer = context.consumers().getBuffer(lineLayer);
        for (Box local : boxes) {
            Box box = local.offset(visualPos).offset(-camera.x, -camera.y, -camera.z).expand(EDGE_OFFSET);
            drawBoxEdges(matrices, lineConsumer, box.expand(EDGE_OFFSET), getOutlineArgbColor(), lineRadius());
        }
        drawLayer(context, lineLayer);
    }

    private Vec3d visualPos(BlockPos pos) {
        long now = System.nanoTime();
        double targetX = pos.getX();
        double targetY = pos.getY();
        double targetZ = pos.getZ();
        if (!smoothSwitch || visualTargetPos == null || lastRenderNanos == 0L) {
            visualX = targetX;
            visualY = targetY;
            visualZ = targetZ;
            visualTargetPos = pos;
            lastRenderNanos = now;
            return new Vec3d(visualX, visualY, visualZ);
        }

        double dt = Math.min(0.08D, Math.max(0.0D, (now - lastRenderNanos) / 1_000_000_000.0D));
        lastRenderNanos = now;
        if (!pos.equals(visualTargetPos)) {
            visualTargetPos = pos;
        }

        double factor = 1.0D - Math.exp(-14.0D * dt);
        visualX += (targetX - visualX) * factor;
        visualY += (targetY - visualY) * factor;
        visualZ += (targetZ - visualZ) * factor;
        if (Math.abs(targetX - visualX) + Math.abs(targetY - visualY) + Math.abs(targetZ - visualZ) < 0.001D) {
            visualX = targetX;
            visualY = targetY;
            visualZ = targetZ;
        }
        return new Vec3d(visualX, visualY, visualZ);
    }

    private static void drawLayer(WorldRenderContext context, RenderLayer layer) {
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    private RenderLayer fillLayer() {
        if (fillMode == FillMode.SHADER) {
            return shaderLayer(shaderType, throughWalls, animationSpeed);
        }
        return throughWalls ? NO_DEPTH_COLOR_LAYER : DEPTH_COLOR_LAYER;
    }

    private RenderLayer lineLayer() {
        return throughWalls ? NO_DEPTH_COLOR_LAYER : DEPTH_COLOR_LAYER;
    }

    private static RenderLayer shaderLayer(ShaderType type, boolean noDepth, float animationSpeed) {
        RenderPipeline pipeline = shaderPipeline(type, noDepth);
        float width = Math.max(0.1F, animationSpeed);
        String cacheKey = type.name() + ':' + noDepth + ':' + Float.floatToIntBits(width);
        return SHADER_LAYERS.computeIfAbsent(cacheKey, ignored -> RenderLayer.of(
                "fluxvisuals_block_overlay_" + type.name().toLowerCase(java.util.Locale.ROOT) + (noDepth ? "_no_depth" : ""),
                1536,
                false,
                true,
                pipeline,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(RenderPhase.NO_TEXTURE)
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(width)))
                        .target(RenderPhase.TRANSLUCENT_TARGET)
                        .build(false)
        ));
    }

    private static RenderPipeline shaderPipeline(ShaderType type, boolean noDepth) {
        Map<ShaderType, RenderPipeline> map = noDepth ? SHADER_NO_DEPTH_PIPELINES : SHADER_DEPTH_PIPELINES;
        return map.computeIfAbsent(type, key -> RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(Identifier.of("fluxvisuals", "pipeline/block_overlay_" + key.name().toLowerCase(java.util.Locale.ROOT) + (noDepth ? "_no_depth" : "")))
                .withVertexShader(Identifier.of("fluxvisuals", "core/blockesp/" + key.path()))
                .withFragmentShader(Identifier.of("fluxvisuals", "core/blockesp/" + key.path()))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(noDepth ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .build()));
    }

    private void drawBoxEdges(MatrixStack matrices, VertexConsumer consumer, Box box, int color, double radius) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        drawFilledBox(matrices, consumer, new Box(x1, y1 - radius, z1 - radius, x2, y1 + radius, z1 + radius), color);
        drawFilledBox(matrices, consumer, new Box(x1, y1 - radius, z2 - radius, x2, y1 + radius, z2 + radius), color);
        drawFilledBox(matrices, consumer, new Box(x1, y2 - radius, z1 - radius, x2, y2 + radius, z1 + radius), color);
        drawFilledBox(matrices, consumer, new Box(x1, y2 - radius, z2 - radius, x2, y2 + radius, z2 + radius), color);

        drawFilledBox(matrices, consumer, new Box(x1 - radius, y1, z1 - radius, x1 + radius, y2, z1 + radius), color);
        drawFilledBox(matrices, consumer, new Box(x1 - radius, y1, z2 - radius, x1 + radius, y2, z2 + radius), color);
        drawFilledBox(matrices, consumer, new Box(x2 - radius, y1, z1 - radius, x2 + radius, y2, z1 + radius), color);
        drawFilledBox(matrices, consumer, new Box(x2 - radius, y1, z2 - radius, x2 + radius, y2, z2 + radius), color);

        drawFilledBox(matrices, consumer, new Box(x1 - radius, y1 - radius, z1, x1 + radius, y1 + radius, z2), color);
        drawFilledBox(matrices, consumer, new Box(x1 - radius, y2 - radius, z1, x1 + radius, y2 + radius, z2), color);
        drawFilledBox(matrices, consumer, new Box(x2 - radius, y1 - radius, z1, x2 + radius, y1 + radius, z2), color);
        drawFilledBox(matrices, consumer, new Box(x2 - radius, y2 - radius, z1, x2 + radius, y2 + radius, z2), color);
    }

    private static void drawFilledBox(MatrixStack matrices, VertexConsumer consumer, Box box, int color) {
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        float a = (color >>> 24) / 255.0F;
        MatrixStack.Entry entry = matrices.peek();
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        vertex(consumer, entry, x1, y1, z1, r, g, b, a);
        vertex(consumer, entry, x2, y1, z1, r, g, b, a);
        vertex(consumer, entry, x2, y1, z2, r, g, b, a);
        vertex(consumer, entry, x1, y1, z2, r, g, b, a);

        vertex(consumer, entry, x1, y2, z1, r, g, b, a);
        vertex(consumer, entry, x1, y2, z2, r, g, b, a);
        vertex(consumer, entry, x2, y2, z2, r, g, b, a);
        vertex(consumer, entry, x2, y2, z1, r, g, b, a);

        vertex(consumer, entry, x1, y1, z1, r, g, b, a);
        vertex(consumer, entry, x1, y2, z1, r, g, b, a);
        vertex(consumer, entry, x2, y2, z1, r, g, b, a);
        vertex(consumer, entry, x2, y1, z1, r, g, b, a);

        vertex(consumer, entry, x1, y1, z2, r, g, b, a);
        vertex(consumer, entry, x2, y1, z2, r, g, b, a);
        vertex(consumer, entry, x2, y2, z2, r, g, b, a);
        vertex(consumer, entry, x1, y2, z2, r, g, b, a);

        vertex(consumer, entry, x1, y1, z1, r, g, b, a);
        vertex(consumer, entry, x1, y1, z2, r, g, b, a);
        vertex(consumer, entry, x1, y2, z2, r, g, b, a);
        vertex(consumer, entry, x1, y2, z1, r, g, b, a);

        vertex(consumer, entry, x2, y1, z1, r, g, b, a);
        vertex(consumer, entry, x2, y2, z1, r, g, b, a);
        vertex(consumer, entry, x2, y2, z2, r, g, b, a);
        vertex(consumer, entry, x2, y1, z2, r, g, b, a);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z,
                               float r, float g, float b, float a) {
        consumer.vertex(entry, x, y, z).color(r, g, b, a);
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

    public void setLineThickness(float lineThickness) {
        float next = 1.0F + clamp01((lineThickness - 1.0F) / 4.0F) * 4.0F;
        if (Math.abs(this.lineThickness - next) < 0.001F) {
            return;
        }
        this.lineThickness = next;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isFillEnabled() {
        return fillEnabled;
    }

    public void setFillEnabled(boolean fillEnabled) {
        if (this.fillEnabled == fillEnabled) {
            return;
        }
        this.fillEnabled = fillEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isThroughWalls() {
        return throughWalls;
    }

    public void setThroughWalls(boolean throughWalls) {
        if (this.throughWalls == throughWalls) {
            return;
        }
        this.throughWalls = throughWalls;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isSmoothSwitch() {
        return smoothSwitch;
    }

    public void setSmoothSwitch(boolean smoothSwitch) {
        if (this.smoothSwitch == smoothSwitch) {
            return;
        }
        this.smoothSwitch = smoothSwitch;
        visualTargetPos = null;
        lastRenderNanos = 0L;
        FluxVisualsClient.requestConfigSave();
    }

    public FillMode getFillMode() {
        return fillMode;
    }

    public void setFillMode(FillMode fillMode) {
        FillMode next = fillMode == null ? FillMode.FILL : fillMode;
        if (this.fillMode == next) {
            return;
        }
        this.fillMode = next;
        FluxVisualsClient.requestConfigSave();
    }

    public ShaderType getShaderType() {
        return shaderType;
    }

    public void setShaderType(ShaderType shaderType) {
        ShaderType next = shaderType == null ? ShaderType.AURA : shaderType;
        if (this.shaderType == next) {
            return;
        }
        this.shaderType = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getAnimationSpeed() {
        return animationSpeed;
    }

    public void setAnimationSpeed(float animationSpeed) {
        float next = 0.1F + clamp01((animationSpeed - 0.1F) / 3.9F) * 3.9F;
        if (Math.abs(this.animationSpeed - next) < 0.001F) {
            return;
        }
        this.animationSpeed = next;
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
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 255, (col >> 8) & 255, col & 255, null);
        setOutlineColor(hsb[0], hsb[1], hsb[2]);
        this.outlineAlpha = ((col >>> 24) & 255) / 255.0F;
        FluxVisualsClient.requestConfigSave();
    }

    public int getFillArgbColor() {
        return (Math.round(fillAlpha * 255.0F) << 24) | getFillColorRgb();
    }

    public void setFillArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 255, (col >> 8) & 255, col & 255, null);
        setFillColor(hsb[0], hsb[1], hsb[2]);
        this.fillAlpha = ((col >>> 24) & 255) / 255.0F;
        FluxVisualsClient.requestConfigSave();
    }

    private double lineRadius() {
        return 0.0035D + (lineThickness - 1.0F) * 0.0038D;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public enum FillMode {
        FILL("\u0417\u0430\u043b\u0438\u0432\u043a\u0430"),
        SHADER("\u0428\u0435\u0439\u0434\u0435\u0440");

        private final String label;

        FillMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum ShaderType {
        AURA("\u041c\u044f\u0433\u043a\u0438\u0439", "aura"),
        COBWEB("\u041f\u0430\u0443\u0442\u0438\u043d\u0430", "block_cobweb_overlay"),
        NEBULA("\u0422\u0443\u043c\u0430\u043d\u043d\u043e\u0441\u0442\u044c", "block_nebula_overlay"),
        PLASMA("\u041f\u043b\u0430\u0437\u043c\u0430", "block_plasma_overlay"),
        STARFIELD("\u0417\u0432\u0435\u0437\u0434\u044b", "block_starfield_overlay");

        private final String label;
        private final String path;

        ShaderType(String label, String path) {
            this.label = label;
            this.path = path;
        }

        public String label() {
            return label;
        }

        public String path() {
            return path;
        }
    }
}
