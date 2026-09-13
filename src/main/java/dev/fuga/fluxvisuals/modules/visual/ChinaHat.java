package dev.fuga.fluxvisuals.modules.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;
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
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class ChinaHat extends Module {
    private static final double HAT_TOP_OFFSET = 0.12D;
    private static final int SEGMENTS = 64;
    private static final int SPOKE_STEP = 8;
    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/china_hat_fill"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .build());
    private static final RenderLayer FILL_LAYER = RenderLayer.of(
            "fluxvisuals_china_hat_fill",
            1536,
            false,
            true,
            FILL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false)
    );
    private static final Map<BlockOverlay.ShaderType, RenderPipeline> SHADER_PIPELINES = new EnumMap<>(BlockOverlay.ShaderType.class);

    private float size = 0.9F;
    private float hue = 0.78F;
    private float saturation = 0.72F;
    private float value = 1.0F;
    private float shaderAlpha = 0.82F;
    private float hatAlpha = 0.85F;
    private FillMode fillMode = FillMode.FILL;
    private BlockOverlay.ShaderType shaderType = BlockOverlay.ShaderType.AURA;
    private boolean renderSelf = true;
    private boolean renderPlayers;
    private boolean renderBots;

    public ChinaHat() {
        super("China Hat", "Draws a visual cone hat above the player.", ModuleCategory.VISUALS);
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (!isEnabled() || player == null
                || context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        float tickDelta = context.tickCounter().getTickProgress(false);
        Vec3d camera = context.camera().getPos();
        if (!renderSelf && !renderPlayers && !renderBots) return;
        boolean selfBot = FluxVisualsClient.MULTI_BOT_MANAGER.isManagedBotName(player.getName().getString());
        if (client.options.getPerspective() != Perspective.FIRST_PERSON
                && (renderSelf || (renderBots && selfBot))) {
            renderPlayer(context, player, tickDelta, camera);
        }
        if (renderPlayers || renderBots) for (PlayerEntity target : client.world.getPlayers()) {
            if (target == player) continue;
            boolean bot = FluxVisualsClient.MULTI_BOT_MANAGER.isManagedBotName(target.getName().getString());
            if (bot ? !renderBots : !renderPlayers) continue;
            renderPlayer(context, target, tickDelta, camera);
        }
    }

    private void renderPlayer(WorldRenderContext context, PlayerEntity player, float tickDelta, Vec3d camera) {
        float headYaw = player.lastHeadYaw + (player.headYaw - player.lastHeadYaw) * tickDelta;
        float pitch = player.getPitch(tickDelta);
        boolean pronePose = player.isInSwimmingPose() || player.isSwimming() || player.isGliding();
        Vec3d pos = hatAnchor(player, tickDelta).subtract(camera);
        float radius = 0.52F + size * 0.33F;
        float baseY = 0.035F;
        float peakY = 0.25F + size * 0.07F;
        int color = fillMode == FillMode.SHADER ? hsvColor(Math.round(shaderAlpha * hatAlpha * 255.0F), 255) : hsvColor(Math.round(210.0F * hatAlpha), 255);
        int outline = hsvColor(Math.round(255.0F * hatAlpha), 255);

        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-headYaw));
        if (!pronePose) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        }
        matrices.translate(0.0D, HAT_TOP_OFFSET, 0.0D);
        MatrixStack.Entry entry = matrices.peek();
        RenderLayer fillLayer = fillMode == FillMode.SHADER ? shaderLayer(shaderType) : FILL_LAYER;
        VertexConsumer triangles = context.consumers().getBuffer(fillLayer);
        drawHatFill(triangles, entry, radius, baseY, peakY, color);
        drawLayer(context, fillLayer);

        RenderLayer lineLayer = RenderLayer.getDebugLineStrip(1.6D);
        VertexConsumer line = context.consumers().getBuffer(lineLayer);
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = Math.PI * 2.0D * i / SEGMENTS;
            vertex(line, entry, (float) Math.cos(a) * radius, baseY + 0.003F, (float) Math.sin(a) * radius, outline);
        }
        drawLayer(context, lineLayer);
        for (int i = 0; i < SEGMENTS; i += SPOKE_STEP) {
            double a = Math.PI * 2.0D * i / SEGMENTS;
            line = context.consumers().getBuffer(lineLayer);
            vertex(line, entry, 0.0F, peakY + 0.002F, 0.0F, outline);
            vertex(line, entry, (float) Math.cos(a) * radius, baseY + 0.003F, (float) Math.sin(a) * radius, outline);
            drawLayer(context, lineLayer);
        }
        matrices.pop();
    }

    public boolean isRenderSelf() { return renderSelf; }
    public void setRenderSelf(boolean value) { renderSelf = value; FluxVisualsClient.requestConfigSave(); }
    public boolean isRenderPlayers() { return renderPlayers; }
    public void setRenderPlayers(boolean value) { renderPlayers = value; FluxVisualsClient.requestConfigSave(); }
    public boolean isRenderBots() { return renderBots; }
    public void setRenderBots(boolean value) { renderBots = value; FluxVisualsClient.requestConfigSave(); }

    private static Vec3d hatAnchor(PlayerEntity player, float tickDelta) {
        Vec3d base = player.getLerpedPos(tickDelta);
        if (player.isGliding()) {
            return base.add(0.0D, player.getHeight() + 0.02D, 0.0D);
        }
        if (player.isInSwimmingPose() || player.isSwimming()) {
            return player.getCameraPosVec(tickDelta).add(0.0D, 0.02D, 0.0D);
        }
        return player.getCameraPosVec(tickDelta);
    }

    private static void drawHatFill(VertexConsumer consumer, MatrixStack.Entry entry, float radius, float baseY, float peakY, int color) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = Math.PI * 2.0D * i / SEGMENTS;
            double a2 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            vertex(consumer, entry, 0.0F, peakY, 0.0F, color);
            vertex(consumer, entry, (float) Math.cos(a1) * radius, baseY, (float) Math.sin(a1) * radius, color);
            vertex(consumer, entry, (float) Math.cos(a2) * radius, baseY, (float) Math.sin(a2) * radius, color);
        }
    }

    private static void drawLayer(WorldRenderContext context, RenderLayer layer) {
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    private static RenderLayer shaderLayer(BlockOverlay.ShaderType shaderType) {
        return RenderLayer.of(
                "fluxvisuals_china_hat_" + shaderType.name().toLowerCase(java.util.Locale.ROOT),
                1536,
                false,
                true,
                shaderPipeline(shaderType),
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(RenderPhase.NO_TEXTURE)
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(1.0D)))
                        .target(RenderPhase.TRANSLUCENT_TARGET)
                        .build(false)
        );
    }

    private static RenderPipeline shaderPipeline(BlockOverlay.ShaderType shaderType) {
        return SHADER_PIPELINES.computeIfAbsent(shaderType, key -> RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(Identifier.of("fluxvisuals", "pipeline/china_hat_" + key.name().toLowerCase(java.util.Locale.ROOT)))
                .withVertexShader(Identifier.of("fluxvisuals", "core/blockesp/" + key.path()))
                .withFragmentShader(Identifier.of("fluxvisuals", "core/blockesp/" + key.path()))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                .build()));
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        float clamped = Math.max(0.0F, Math.min(1.0F, size));
        if (this.size == clamped) {
            return;
        }

        this.size = clamped;
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
        float clampedHue = Math.max(0.0F, Math.min(1.0F, hue));
        float clampedSaturation = Math.max(0.0F, Math.min(1.0F, saturation));
        float clampedValue = Math.max(0.0F, Math.min(1.0F, value));
        if (this.hue == clampedHue && this.saturation == clampedSaturation && this.value == clampedValue) {
            return;
        }

        this.hue = clampedHue;
        this.saturation = clampedSaturation;
        this.value = clampedValue;
        FluxVisualsClient.requestConfigSave();
    }

    public float getHatAlpha() {
        return hatAlpha;
    }

    public void setHatAlpha(float hatAlpha) {
        float next = Math.max(0.0F, Math.min(1.0F, hatAlpha));
        if (Math.abs(this.hatAlpha - next) < 0.001F) {
            return;
        }
        this.hatAlpha = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getColorRgb() {
        return hsvColor(255, 0);
    }

    public int getArgbColor() {
        return (Math.round(hatAlpha * 255.0F) << 24) | (getColorRgb() & 0x00FFFFFF);
    }

    public void setArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        setColor(hsb[0], hsb[1], hsb[2]);
        this.hatAlpha = ((col >>> 24) & 0xFF) / 255.0F;
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

    public BlockOverlay.ShaderType getShaderType() {
        return shaderType;
    }

    public void setShaderType(BlockOverlay.ShaderType shaderType) {
        BlockOverlay.ShaderType next = shaderType == null ? BlockOverlay.ShaderType.AURA : shaderType;
        if (this.shaderType == next) {
            return;
        }

        this.shaderType = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getShaderAlpha() {
        return shaderAlpha;
    }

    public void setShaderAlpha(float shaderAlpha) {
        float next = Math.max(0.0F, Math.min(1.0F, shaderAlpha));
        if (Math.abs(this.shaderAlpha - next) < 0.001F) {
            return;
        }

        this.shaderAlpha = next;
        FluxVisualsClient.requestConfigSave();
    }

    private int hsvColor(int alpha, int alphaFallback) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, value);
        return ((alpha & 255) << 24) | (rgb & 0x00FFFFFF) | (alphaFallback & 0);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, int color) {
        int a = color >>> 24;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        consumer.vertex(entry, x, y, z).color(r, g, b, a);
    }

    public enum FillMode {
        FILL("\u041e\u0431\u044b\u0447\u043d\u0430\u044f"),
        SHADER("\u0428\u0435\u0439\u0434\u0435\u0440");

        private final String label;

        FillMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
