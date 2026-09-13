package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.awt.Color;

public final class Wings extends Module {
    private static Wings instance;

    private static final RenderLayer WINGS_FILLED = layer("wings_filled", VertexFormat.DrawMode.TRIANGLES, true);
    private static final RenderLayer WINGS_SHADER = shaderLayer();
    private static final RenderLayer WINGS_GLOW = layer("wings_glow", VertexFormat.DrawMode.TRIANGLES, false);
    private static final RenderLayer WINGS_OUTLINE = RenderLayer.getDebugLineStrip(1.4D);
    private static final RenderLayer WINGS_RIBS = RenderLayer.getDebugLineStrip(1.0D);

    private static RenderLayer layer(String name, VertexFormat.DrawMode mode, boolean depth) {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(net.minecraft.util.Identifier.of("fluxvisuals", "pipeline/" + name))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(depth ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false).withCull(false)
                .withVertexFormat(VertexFormats.POSITION_COLOR, mode).build());
        return RenderLayer.of("fluxvisuals_" + name, 8192, false, true, pipeline,
                RenderLayer.MultiPhaseParameters.builder().texture(RenderPhase.NO_TEXTURE)
                        .target(RenderPhase.TRANSLUCENT_TARGET).build(false));
    }

    private static RenderLayer shaderLayer() {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(net.minecraft.util.Identifier.of("fluxvisuals", "pipeline/wings_shader"))
                .withVertexShader(net.minecraft.util.Identifier.of("fluxvisuals", "core/wings_shader"))
                .withFragmentShader(net.minecraft.util.Identifier.of("fluxvisuals", "core/wings_shader"))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false).withCull(false)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES).build());
        return RenderLayer.of("fluxvisuals_wings_shader", 8192, false, true, pipeline,
                RenderLayer.MultiPhaseParameters.builder().texture(RenderPhase.NO_TEXTURE)
                        .target(RenderPhase.TRANSLUCENT_TARGET).build(false));
    }

    private static final float DEFAULT_SPREAD = 8.0f;
    private static final int DEFAULT_ALPHA = 220;

    private static final WingPoint[][] SHAPES = new WingPoint[8][];
    private static final int ANGELIC = 0, DRAGON = 1, BUTTERFLY = 2, PHOENIX = 3,
            CRYSTAL = 4, MECHANICAL = 5, FAIRY = 6, DEMON = 7;

    static {
        SHAPES[ANGELIC] = new WingPoint[]{
                new WingPoint(0.08f, 0.10f, 0.88f),
                new WingPoint(0.28f, 0.34f, 0.78f),
                new WingPoint(0.56f, 0.82f, 0.62f),
                new WingPoint(0.86f, 0.30f, 0.52f),
                new WingPoint(1.14f, 0.46f, 0.40f),
                new WingPoint(1.24f, 0.04f, 0.30f),
                new WingPoint(1.02f, -0.18f, 0.28f),
                new WingPoint(1.18f, -0.64f, 0.22f),
                new WingPoint(0.86f, -0.46f, 0.20f),
                new WingPoint(0.80f, -0.98f, 0.14f),
                new WingPoint(0.54f, -0.74f, 0.16f),
                new WingPoint(0.30f, -1.16f, 0.12f),
                new WingPoint(0.10f, -0.54f, 0.18f)
        };

        SHAPES[DRAGON] = new WingPoint[]{
                new WingPoint(0.10f, 0.12f, 0.90f),
                new WingPoint(0.22f, 0.40f, 0.80f),
                new WingPoint(0.48f, 0.72f, 0.65f),
                new WingPoint(0.80f, 0.60f, 0.55f),
                new WingPoint(1.10f, 0.70f, 0.42f),
                new WingPoint(1.30f, 0.30f, 0.35f),
                new WingPoint(1.20f, -0.10f, 0.30f),
                new WingPoint(1.05f, -0.50f, 0.25f),
                new WingPoint(0.70f, -0.35f, 0.22f),
                new WingPoint(0.50f, -0.70f, 0.18f),
                new WingPoint(0.20f, -0.50f, 0.15f),
                new WingPoint(0.05f, -0.30f, 0.20f)
        };

        SHAPES[BUTTERFLY] = new WingPoint[]{
                new WingPoint(0.12f, 0.15f, 0.92f),
                new WingPoint(0.30f, 0.50f, 0.85f),
                new WingPoint(0.50f, 0.90f, 0.70f),
                new WingPoint(0.70f, 0.80f, 0.60f),
                new WingPoint(0.85f, 0.55f, 0.50f),
                new WingPoint(0.75f, 0.20f, 0.45f),
                new WingPoint(0.55f, -0.15f, 0.40f),
                new WingPoint(0.40f, -0.60f, 0.30f),
                new WingPoint(0.25f, -0.85f, 0.20f),
                new WingPoint(0.12f, -0.65f, 0.25f),
                new WingPoint(0.06f, -0.35f, 0.30f)
        };

        SHAPES[PHOENIX] = new WingPoint[]{
                new WingPoint(0.10f, 0.14f, 0.90f),
                new WingPoint(0.25f, 0.45f, 0.82f),
                new WingPoint(0.52f, 0.78f, 0.68f),
                new WingPoint(0.82f, 0.50f, 0.55f),
                new WingPoint(1.15f, 0.55f, 0.42f),
                new WingPoint(1.28f, 0.15f, 0.32f),
                new WingPoint(1.20f, -0.25f, 0.28f),
                new WingPoint(1.10f, -0.55f, 0.24f),
                new WingPoint(1.25f, -0.85f, 0.18f),
                new WingPoint(0.90f, -0.65f, 0.16f),
                new WingPoint(0.60f, -0.90f, 0.14f),
                new WingPoint(0.30f, -0.70f, 0.12f),
                new WingPoint(0.08f, -0.40f, 0.16f)
        };

        SHAPES[CRYSTAL] = new WingPoint[]{
                new WingPoint(0.15f, 0.10f, 0.85f),
                new WingPoint(0.40f, 0.35f, 0.75f),
                new WingPoint(0.70f, 0.60f, 0.60f),
                new WingPoint(1.00f, 0.40f, 0.50f),
                new WingPoint(0.85f, 0.10f, 0.45f),
                new WingPoint(1.10f, -0.15f, 0.35f),
                new WingPoint(0.90f, -0.45f, 0.30f),
                new WingPoint(0.65f, -0.30f, 0.25f),
                new WingPoint(0.45f, -0.60f, 0.20f),
                new WingPoint(0.20f, -0.40f, 0.22f),
                new WingPoint(0.08f, -0.15f, 0.30f)
        };

        SHAPES[MECHANICAL] = new WingPoint[]{
                new WingPoint(0.08f, 0.08f, 0.90f),
                new WingPoint(0.20f, 0.25f, 0.82f),
                new WingPoint(0.45f, 0.40f, 0.70f),
                new WingPoint(0.70f, 0.35f, 0.58f),
                new WingPoint(0.95f, 0.25f, 0.48f),
                new WingPoint(0.90f, 0.00f, 0.40f),
                new WingPoint(1.10f, -0.15f, 0.32f),
                new WingPoint(0.80f, -0.30f, 0.28f),
                new WingPoint(0.55f, -0.20f, 0.25f),
                new WingPoint(0.40f, -0.45f, 0.20f),
                new WingPoint(0.15f, -0.30f, 0.22f),
                new WingPoint(0.05f, -0.10f, 0.28f)
        };

        SHAPES[FAIRY] = new WingPoint[]{
                new WingPoint(0.10f, 0.12f, 0.90f),
                new WingPoint(0.25f, 0.38f, 0.82f),
                new WingPoint(0.42f, 0.65f, 0.68f),
                new WingPoint(0.55f, 0.70f, 0.58f),
                new WingPoint(0.60f, 0.45f, 0.50f),
                new WingPoint(0.50f, 0.15f, 0.42f),
                new WingPoint(0.38f, -0.10f, 0.35f),
                new WingPoint(0.30f, -0.35f, 0.28f),
                new WingPoint(0.18f, -0.45f, 0.22f),
                new WingPoint(0.08f, -0.25f, 0.26f)
        };

        SHAPES[DEMON] = new WingPoint[]{
                new WingPoint(0.10f, 0.12f, 0.88f),
                new WingPoint(0.25f, 0.38f, 0.80f),
                new WingPoint(0.55f, 0.65f, 0.65f),
                new WingPoint(0.85f, 0.50f, 0.52f),
                new WingPoint(1.15f, 0.55f, 0.40f),
                new WingPoint(1.25f, 0.20f, 0.32f),
                new WingPoint(1.10f, -0.10f, 0.28f),
                new WingPoint(1.30f, -0.45f, 0.22f),
                new WingPoint(1.15f, -0.70f, 0.18f),
                new WingPoint(0.85f, -0.55f, 0.16f),
                new WingPoint(0.55f, -0.85f, 0.14f),
                new WingPoint(0.25f, -0.65f, 0.12f),
                new WingPoint(0.08f, -0.35f, 0.18f)
        };
    }

    private static final int[] RIBS_DEFAULT = {2, 4, 7, 9, 11};
    private static final int[] RIBS_SMALL = {2, 4, 6, 8};

    private WingType wingType = WingType.ANGELIC;
    private boolean shaderFill = true;
    private boolean renderSelf = true;
    private boolean renderPlayers;
    private boolean renderBots;
    private float wingScale = 1.0f, height = 1.5f, depthOffset = 0.15f;
    private boolean flapping = true, throughWalls;
    private float flapStrength = 30.0f, flapSpeed = 3.0f;
    private int wingColor = new Color(255, 255, 255, 220).getRGB();

    private float selfBodyYaw;
    private boolean selfBodyYawInitialized;

    public Wings() {
        super("Wings", "Renders the original prototype wings on player backs.", ModuleCategory.VISUALS);
        instance = this;
    }

    public static Wings getInstance() {
        return instance;
    }

    public int getWingColor() { return wingColor; }
    public void setWingColor(int color) { this.wingColor = color; }
    public int getArgbColor() { return wingColor; }
    public void setArgbColor(int color) { this.wingColor = color; FluxVisualsClient.requestConfigSave(); }

    private WingPoint[] getCurrentShape() {
        return switch (wingType) {
            case DRAGON -> SHAPES[DRAGON];
            case BUTTERFLY -> SHAPES[BUTTERFLY];
            case PHOENIX -> SHAPES[PHOENIX];
            case CRYSTAL -> SHAPES[CRYSTAL];
            case MECHANICAL -> SHAPES[MECHANICAL];
            case FAIRY -> SHAPES[FAIRY];
            case DEMON -> SHAPES[DEMON];
            default -> SHAPES[ANGELIC];
        };
    }

    private int[] getCurrentRibIndices(WingPoint[] shape) {
        return shape.length <= 10 ? RIBS_SMALL : RIBS_DEFAULT;
    }

    public void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isEnabled() || mc.world == null || mc.player == null || context.matrixStack() == null || context.camera() == null) return;
        float tickDelta = context.tickCounter().getTickProgress(false);
        Vec3d camera = context.camera().getPos();
        MatrixStack stack = context.matrixStack();

        boolean selfIsBot = FluxVisualsClient.MULTI_BOT_MANAGER.isManagedBotName(mc.player.getName().getString());
        if ((renderSelf || (renderBots && selfIsBot)) && !mc.options.getPerspective().isFirstPerson()
                && mc.player.isAlive() && !hasElytra(mc.player)) {
            try { renderWings(context, stack, mc.player, tickDelta, camera); } catch (Exception ignored) {}
        }

        if (renderPlayers || renderBots) for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (!player.isAlive() || hasElytra(player)) continue;
                boolean bot = FluxVisualsClient.MULTI_BOT_MANAGER.isManagedBotName(player.getName().getString());
                if (bot ? !renderBots : !renderPlayers) continue;
                try { renderWings(context, stack, player, tickDelta, camera); } catch (Exception ignored) {}
            }
    }

    private void renderWings(WorldRenderContext context, MatrixStack stack, PlayerEntity player, float tickDelta, Vec3d camera) {
        VertexConsumerProvider.Immediate provider = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        double x = MathHelper.lerp(tickDelta, player.lastX, player.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, player.lastY, player.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, player.lastZ, player.getZ()) - camera.z;

        float bodyYaw = resolveBodyYaw(player, tickDelta);
        float move = MathHelper.clamp((float) player.getVelocity().horizontalLength() * 10f, 0f, 1f);

        WingPose pose = resolvePose(player, tickDelta);
        if (pose == null) return;

        float flap = 0f;
        if (flapping) {
            float speedMul = flapSpeed / 3.0f;
            float ampMul = flapStrength / 30.0f;
            float effectiveSpeed = pose.flapSpeed * speedMul;
            float effectiveAmp = pose.flapAmplitude * ampMul;
            flap = (float) Math.sin((player.age + tickDelta) * effectiveSpeed) * effectiveAmp;
        }
        float open = (DEFAULT_SPREAD + flap + move * pose.motionSpreadBoost) * pose.openMultiplier;
        float ws = wingScale * pose.scaleMultiplier;

        WingPoint[] shape = getCurrentShape();
        int[] ribIndices = getCurrentRibIndices(shape);

        int baseColor = wingColor;
        int glowColor = interpolateColor(baseColor, 0xFFFFFFFF, 0.28f);
        int coreColor = interpolateColor(baseColor, 0xFFFFFFFF, 0.55f);
        int outlineColor = baseColor;

        boolean depth = !throughWalls;

        stack.push();
        stack.translate(x, y, z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        if (pose.preTranslateY != 0f || pose.preTranslateZ != 0f)
            stack.translate(0f, pose.preTranslateY, pose.preTranslateZ);
        if (pose.pitchRotation != 0f)
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.pitchRotation));
        if (pose.rollRotation != 0f)
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.rollRotation));
        stack.translate(0f, pose.anchorY, pose.anchorZ);
        stack.scale(ws, ws, ws);

        renderWingSide(stack, provider, -1f, open, baseColor, glowColor, coreColor, outlineColor, pose, shape, ribIndices, depth);
        renderWingSide(stack, provider, 1f, open, baseColor, glowColor, coreColor, outlineColor, pose, shape, ribIndices, depth);

        stack.pop();

        provider.draw();
    }

    private void renderWingSide(MatrixStack stack, VertexConsumerProvider.Immediate provider,
                                float side, float open, int baseColor, int glowColor, int coreColor, int outlineColor,
                                WingPose pose, WingPoint[] shape, int[] ribIndices, boolean depth) {
        stack.push();
        stack.translate(side * pose.sideOffset, pose.sideYOffset, pose.sideZOffset);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * open));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * pose.sideRoll));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.sidePitch));

        float userAlphaMul = Math.max(0.05f, ((wingColor >>> 24) & 0xFF) / 255.0f);
        int curAlpha = Math.round(DEFAULT_ALPHA * userAlphaMul);

        RenderLayer glowType = WINGS_GLOW;
        drawWingLayer(provider, stack, side, 1.22f, setAlpha(glowColor, (int) (curAlpha * 0.22f)), setAlpha(glowColor, 0), glowType, shape);
        drawWingLayer(provider, stack, side, 0.84f, setAlpha(coreColor, (int) (curAlpha * 0.26f)), setAlpha(coreColor, 0), glowType, shape);

        RenderLayer baseType = shaderFill ? WINGS_SHADER : WINGS_FILLED;
        drawWingLayer(provider, stack, side, 1.0f, setAlpha(baseColor, curAlpha), setAlpha(baseColor, Math.round(10 * userAlphaMul)), baseType, shape);

        RenderLayer outlineType = WINGS_OUTLINE;
        drawWingOutline(provider, stack, side, 1.0f, setAlpha(outlineColor, (int) (curAlpha * 0.62f)), outlineType, shape);

        RenderLayer ribsType = WINGS_RIBS;
        drawWingRibs(provider, stack, side, 0.96f, setAlpha(glowColor, (int) (curAlpha * 0.20f)), ribsType, shape, ribIndices);

        stack.pop();
    }

    private void drawWingLayer(VertexConsumerProvider.Immediate provider, MatrixStack stack,
                               float side, float scale, int rootColor, int edgeColor, RenderLayer renderType, WingPoint[] shape) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        MatrixStack.Entry entry = stack.peek();
        for (int i = 0; i < shape.length; i++) {
            WingPoint cur = shape[i];
            WingPoint next = shape[(i + 1) % shape.length];
            vertex(consumer, entry, 0f, 0f, 0f, rootColor);
            vertex(consumer, entry, side * cur.x * scale, cur.y * scale, 0f, applyPointAlpha(edgeColor, cur.alphaMul));
            vertex(consumer, entry, side * next.x * scale, next.y * scale, 0f, applyPointAlpha(edgeColor, next.alphaMul));
        }
    }

    private void drawWingOutline(VertexConsumerProvider.Immediate provider, MatrixStack stack,
                                  float side, float scale, int color, RenderLayer renderType, WingPoint[] shape) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        MatrixStack.Entry entry = stack.peek();
        for (WingPoint point : shape) {
            vertex(consumer, entry, side * point.x * scale, point.y * scale, 0f, color);
        }
        vertex(consumer, entry, side * shape[0].x * scale, shape[0].y * scale, 0f, color);
    }

    private void drawWingRibs(VertexConsumerProvider.Immediate provider, MatrixStack stack,
                               float side, float scale, int color, RenderLayer renderType, WingPoint[] shape, int[] ribIndices) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        MatrixStack.Entry entry = stack.peek();
        for (int idx : ribIndices) {
            if (idx >= shape.length) continue;
            WingPoint point = shape[idx];
            vertex(consumer, entry, 0f, 0f, 0f, setAlpha(color, Math.max(8, (int) (alpha(color) * 0.75f))));
            vertex(consumer, entry, side * point.x * scale, point.y * scale, 0f, applyPointAlpha(color, point.alphaMul));
        }
    }

    private int applyPointAlpha(int color, float multiplier) {
        return setAlpha(color, Math.max(0, Math.min(255, (int) (alpha(color) * multiplier))));
    }

    private static int setAlpha(int color, int a) {
        return (MathHelper.clamp(a, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int alpha(int color) { return (color >> 24) & 0xFF; }
    private static int red(int color) { return (color >> 16) & 0xFF; }
    private static int green(int color) { return (color >> 8) & 0xFF; }
    private static int blue(int color) { return color & 0xFF; }

    private static int getColor(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int interpolateColor(int color1, int color2, float t) {
        int r1 = red(color1), g1 = green(color1), b1 = blue(color1), a1 = alpha(color1);
        int r2 = red(color2), g2 = green(color2), b2 = blue(color2), a2 = alpha(color2);
        return getColor(
                (int) (r1 + (r2 - r1) * t),
                (int) (g1 + (g2 - g1) * t),
                (int) (b1 + (b2 - b1) * t),
                (int) (a1 + (a2 - a1) * t)
        );
    }

    private boolean hasElytra(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private float resolveBodyYaw(PlayerEntity player, float tickDelta) {
        float target = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);
        if (player != MinecraftClient.getInstance().player) return target;
        if (!selfBodyYawInitialized || player.age < 2) {
            selfBodyYaw = target;
            selfBodyYawInitialized = true;
            return selfBodyYaw;
        }
        selfBodyYaw = approachDegrees(selfBodyYaw, target, 14f);
        return selfBodyYaw;
    }

    private static float approachDegrees(float current, float target, float maxDelta) {
        float delta = MathHelper.wrapDegrees(target - current);
        delta = MathHelper.clamp(delta, -maxDelta, maxDelta);
        return current + delta;
    }

    private WingPose resolvePose(PlayerEntity player, float tickDelta) {
        float pitch = MathHelper.lerp(tickDelta, player.lastPitch, player.getPitch());

        if (player.isGliding()) {
            float flightTicks = 10.0f + tickDelta;
            float flightProgress = MathHelper.clamp(flightTicks * flightTicks / 100f, 0f, 1f);
            float pitchRotation = flightProgress * (-90f - pitch);
            return new WingPose(0.34f, 0.46f, 0f, 0f, pitchRotation, 0f,
                    0.76f, 0.92f, 0.10f, 0.58f, 0.05f, 0.06f, -5f, -2f, 0.13f);
        }

        if (player.isTouchingWater()) return null;

        if (player.isSneaking()) {
            return new WingPose(0f, 0f, 0.96f, 0.10f, 18f, 0f,
                    1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
        }

        return new WingPose(0f, 0f, 1.38f, 0.10f, 0f, 0f,
                1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        selfBodyYawInitialized = false;
        super.onDisable(client);
    }

    public WingType getWingType() { return wingType; }
    public void setWingType(WingType value) { wingType = value == null ? WingType.ANGELIC : value; FluxVisualsClient.requestConfigSave(); }
    public boolean isShaderFill() { return shaderFill; }
    public void setShaderFill(boolean value) { shaderFill = value; FluxVisualsClient.requestConfigSave(); }
    public boolean isRenderSelf() { return renderSelf; }
    public void setRenderSelf(boolean value) { renderSelf = value; FluxVisualsClient.requestConfigSave(); }
    public boolean isRenderPlayers() { return renderPlayers; }
    public void setRenderPlayers(boolean value) { renderPlayers = value; FluxVisualsClient.requestConfigSave(); }
    public boolean isRenderBots() { return renderBots; }
    public void setRenderBots(boolean value) { renderBots = value; FluxVisualsClient.requestConfigSave(); }
    public java.util.Set<String> getTargets() {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (renderSelf) set.add("Self");
        if (renderPlayers) set.add("Players");
        if (renderBots) set.add("Bots");
        return set;
    }
    public void setTargets(java.util.Set<String> targets) {
        if (targets == null) return;
        this.renderSelf = targets.contains("Self");
        this.renderPlayers = targets.contains("Players");
        this.renderBots = targets.contains("Bots");
        FluxVisualsClient.requestConfigSave();
    }
    public float getScale() { return wingScale; }
    public void setScale(float value) { wingScale = MathHelper.clamp(value, 0.3f, 3.0f); FluxVisualsClient.requestConfigSave(); }
    public float getFlapStrength() { return flapStrength; }
    public void setFlapStrength(float value) { flapStrength = MathHelper.clamp(value, 5.0f, 60.0f); FluxVisualsClient.requestConfigSave(); }
    public float getFlapSpeed() { return flapSpeed; }
    public void setFlapSpeed(float value) { flapSpeed = MathHelper.clamp(value, 0.5f, 8.0f); FluxVisualsClient.requestConfigSave(); }
    public boolean isFlapping() { return flapping; }
    public void setFlapping(boolean value) { flapping = value; FluxVisualsClient.requestConfigSave(); }

    public enum WingType {
        ANGELIC("Angelic"), DRAGON("Dragon"), BUTTERFLY("Butterfly"), PHOENIX("Phoenix"),
        CRYSTAL("Crystal"), MECHANICAL("Mechanical"), FAIRY("Fairy"), DEMON("Demon");
        private final String label;
        WingType(String label) { this.label = label; }
        public String label() { return label; }
        public WingType next() { WingType[] values = values(); return values[(ordinal() + 1) % values.length]; }
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, int color) {
        consumer.vertex(entry, x, y, z).color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >> 24) & 255);
    }

    private static final class WingPoint {
        final float x, y, alphaMul;
        WingPoint(float x, float y, float alphaMul) { this.x = x; this.y = y; this.alphaMul = alphaMul; }
    }

    private static final class WingPose {
        final float preTranslateY, preTranslateZ;
        final float anchorY, anchorZ;
        final float pitchRotation, rollRotation;
        final float openMultiplier, scaleMultiplier;
        final float motionSpreadBoost, flapAmplitude;
        final float sideOffset, sideYOffset, sideZOffset;
        final float sideRoll, sidePitch, flapSpeed;

        WingPose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                 float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                 float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideZOffset,
                 float sideRoll, float sidePitch, float flapSpeed) {
            this(preTranslateY, preTranslateZ, anchorY, anchorZ, pitchRotation, rollRotation,
                    openMultiplier, scaleMultiplier, motionSpreadBoost, flapAmplitude,
                    sideOffset, 0f, sideZOffset, sideRoll, sidePitch, flapSpeed);
        }

        WingPose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                 float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                 float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideYOffset,
                 float sideZOffset, float sideRoll, float sidePitch, float flapSpeed) {
            this.preTranslateY = preTranslateY;
            this.preTranslateZ = preTranslateZ;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.pitchRotation = pitchRotation;
            this.rollRotation = rollRotation;
            this.openMultiplier = openMultiplier;
            this.scaleMultiplier = scaleMultiplier;
            this.motionSpreadBoost = motionSpreadBoost;
            this.flapAmplitude = flapAmplitude;
            this.sideOffset = sideOffset;
            this.sideYOffset = sideYOffset;
            this.sideZOffset = sideZOffset;
            this.sideRoll = sideRoll;
            this.sidePitch = sidePitch;
            this.flapSpeed = flapSpeed;
        }
    }
}
