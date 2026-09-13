package dile.ru.api.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import dile.ru.api.events.annotation.SubscribeEvent;
import dile.ru.api.events.impl.WorldRenderEvent;
import dile.ru.api.module.Module;
import dile.ru.api.module.ModuleCategory;
import dile.ru.api.settings.impl.BooleanSetting;
import dile.ru.api.settings.impl.ColorSetting;
import dile.ru.api.settings.impl.ModeSetting;
import dile.ru.api.settings.impl.MultiModeSetting;
import dile.ru.api.settings.impl.NumberSetting;
import dile.ru.utils.render.Render3D;
import dile.ru.utils.render.pipeline.ClientPipelines;
import dile.ru.utils.render.world.wingsshader.WingsShaderRenderer;
import dile.ru.utils.repository.friend.FriendUtils;
import org.joml.Matrix4f;

import java.awt.Color;

public final class Wings extends Module {
    private static Wings instance;

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

    private final ModeSetting wingType = register(new ModeSetting("Wing Type", "Type of wings to render.", "Angelic",
            "Angelic", "Dragon", "Butterfly", "Phoenix", "Crystal", "Mechanical", "Fairy", "Demon"));
    private final ModeSetting fillType = register(new ModeSetting("Заливка", "Тип заливки крыльев.", "Обычный",
            "Обычный", "Шейдерные"));
    private final MultiModeSetting targets = register(new MultiModeSetting("Targets", "Who can see the wings.",
            new String[]{"Self", "Friends", "Players"}, "Self"));
    private final NumberSetting wingScale = register(new NumberSetting("Scale", "Wing size.", 1.0, 0.3, 3.0, 0.1));
    private final NumberSetting height = register(new NumberSetting("Height", "Wing height on the back.", 1.5, 0.8, 2.5, 0.05));
    private final NumberSetting depthOffset = register(new NumberSetting("Depth", "How far wings stick out from back.", 0.15, 0.0, 0.5, 0.01));
    private final BooleanSetting flapping = register(new BooleanSetting("Flapping", "Enable wing flapping animation.", true));
    private final NumberSetting flapStrength = register(new NumberSetting("Flap Strength", "Strength of the flap bend.", 30.0, 5.0, 60.0, 1.0));
    private final NumberSetting flapSpeed = register(new NumberSetting("Flap Speed", "Speed of flapping.", 3.0, 0.5, 8.0, 0.5));
    private final BooleanSetting throughWalls = register(new BooleanSetting("Through Walls", "Render wings through walls.", false));
    private final ColorSetting wingColor = register(new ColorSetting("Color", "Wing color.", new Color(255, 255, 255, 220)));

    private float selfBodyYaw;
    private boolean selfBodyYawInitialized;

    public Wings() {
        super("Wings", "Renders wings on player backs.", ModuleCategory.VISUAL);
        instance = this;
        flapStrength.visibleWhen(() -> flapping.getValue());
        flapSpeed.visibleWhen(() -> flapping.getValue());
    }

    public static Wings getInstance() {
        return instance;
    }

    private WingPoint[] getCurrentShape() {
        return switch (wingType.getValue()) {
            case "Dragon" -> SHAPES[DRAGON];
            case "Butterfly" -> SHAPES[BUTTERFLY];
            case "Phoenix" -> SHAPES[PHOENIX];
            case "Crystal" -> SHAPES[CRYSTAL];
            case "Mechanical" -> SHAPES[MECHANICAL];
            case "Fairy" -> SHAPES[FAIRY];
            case "Demon" -> SHAPES[DEMON];
            default -> SHAPES[ANGELIC];
        };
    }

    private int[] getCurrentRibIndices(WingPoint[] shape) {
        return shape.length <= 10 ? RIBS_SMALL : RIBS_DEFAULT;
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) return;

        float tickDelta = event.getTickDelta();
        Vec3 camera = Render3D.lastCameraPos;
        PoseStack stack = event.getStack();

        if (camera == null) return;

        if (targets.isSelected("Self") && !mc.options.getCameraType().isFirstPerson()
                && mc.player.isAlive() && !hasElytra(mc.player)) {
            try { renderWings(stack, mc.player, tickDelta, camera); } catch (Exception ignored) {}
        }

        if (targets.isSelected("Friends") || targets.isSelected("Players")) {
            for (Player player : mc.level.players()) {
                if (player == mc.player) continue;
                if (!player.isAlive() || hasElytra(player)) continue;
                if (!shouldRender(player)) continue;
                try { renderWings(stack, player, tickDelta, camera); } catch (Exception ignored) {}
            }
        }
    }

    private void renderWings(PoseStack stack, Player player, float tickDelta, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();

        double x = Mth.lerp(tickDelta, player.xo, player.getX()) - camera.x;
        double y = Mth.lerp(tickDelta, player.yo, player.getY()) - camera.y;
        double z = Mth.lerp(tickDelta, player.zo, player.getZ()) - camera.z;

        float bodyYaw = resolveBodyYaw(player, tickDelta);
        float move = Mth.clamp((float) player.getDeltaMovement().horizontalDistance() * 10f, 0f, 1f);

        WingPose pose = resolvePose(player, tickDelta);
        if (pose == null) return;

        float flap = 0f;
        if (flapping.getValue()) {
            float speedMul = flapSpeed.getFloat() / 3.0f;
            float ampMul = flapStrength.getFloat() / 30.0f;
            float effectiveSpeed = pose.flapSpeed * speedMul;
            float effectiveAmp = pose.flapAmplitude * ampMul;
            flap = (float) Math.sin((player.tickCount + tickDelta) * effectiveSpeed) * effectiveAmp;
        }
        float open = (DEFAULT_SPREAD + flap + move * pose.motionSpreadBoost) * pose.openMultiplier;
        float ws = wingScale.getFloat() * pose.scaleMultiplier;

        WingPoint[] shape = getCurrentShape();
        int[] ribIndices = getCurrentRibIndices(shape);

        int baseColor = wingColor.getValue().getRGB();
        int glowColor = interpolateColor(baseColor, 0xFFFFFFFF, 0.28f);
        int coreColor = interpolateColor(baseColor, 0xFFFFFFFF, 0.55f);
        int outlineColor = baseColor;

        boolean depth = !throughWalls.getValue();

        stack.pushPose();
        stack.translate(x, y, z);
        stack.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        if (pose.preTranslateY != 0f || pose.preTranslateZ != 0f)
            stack.translate(0f, pose.preTranslateY, pose.preTranslateZ);
        if (pose.pitchRotation != 0f)
            stack.mulPose(Axis.XP.rotationDegrees(pose.pitchRotation));
        if (pose.rollRotation != 0f)
            stack.mulPose(Axis.ZP.rotationDegrees(pose.rollRotation));
        stack.translate(0f, pose.anchorY, pose.anchorZ);
        stack.scale(ws, ws, ws);

        renderWingSide(stack, provider, -1f, open, baseColor, glowColor, coreColor, outlineColor, pose, shape, ribIndices, depth);
        renderWingSide(stack, provider, 1f, open, baseColor, glowColor, coreColor, outlineColor, pose, shape, ribIndices, depth);

        stack.popPose();

        provider.endBatch(ClientPipelines.WINGS_GLOW);
        provider.endBatch(ClientPipelines.WINGS_GLOW_DEPTH);
        provider.endBatch(ClientPipelines.WINGS_FILLED);
        provider.endBatch(ClientPipelines.WINGS_FILLED_NOTHROUGH);
        provider.endBatch(ClientPipelines.WINGS_OUTLINE);
        provider.endBatch(ClientPipelines.WINGS_OUTLINE_DEPTH);
        provider.endBatch(ClientPipelines.WINGS_RIBS);
        provider.endBatch(ClientPipelines.WINGS_RIBS_DEPTH);
    }

    private void renderWingSide(PoseStack stack, MultiBufferSource.BufferSource provider,
                                float side, float open, int baseColor, int glowColor, int coreColor, int outlineColor,
                                WingPose pose, WingPoint[] shape, int[] ribIndices, boolean depth) {
        stack.pushPose();
        stack.translate(side * pose.sideOffset, pose.sideYOffset, pose.sideZOffset);
        stack.mulPose(Axis.YP.rotationDegrees(side * open));
        stack.mulPose(Axis.ZP.rotationDegrees(side * pose.sideRoll));
        stack.mulPose(Axis.XP.rotationDegrees(pose.sidePitch));

        RenderType glowType = depth ? ClientPipelines.WINGS_GLOW_DEPTH : ClientPipelines.WINGS_GLOW;
        drawWingLayer(provider, stack, side, 1.22f, setAlpha(glowColor, (int) (DEFAULT_ALPHA * 0.22f)), setAlpha(glowColor, 0), glowType, shape);
        drawWingLayer(provider, stack, side, 0.84f, setAlpha(coreColor, (int) (DEFAULT_ALPHA * 0.26f)), setAlpha(coreColor, 0), glowType, shape);

        if (fillType.isSelected("Шейдерные")) {
            Matrix4f modelView = new Matrix4f(stack.last().pose());
            WingsShaderRenderer.begin();
            int rootColor = setAlpha(baseColor, DEFAULT_ALPHA);
            int edgeColor = setAlpha(baseColor, 10);
            for (int i = 0; i < shape.length; i++) {
                WingPoint cur = shape[i];
                WingPoint next = shape[(i + 1) % shape.length];
                WingsShaderRenderer.addVertex(0f, 0f, 0f, rootColor);
                WingsShaderRenderer.addVertex(side * cur.x, cur.y, 0f, applyPointAlpha(edgeColor, cur.alphaMul));
                WingsShaderRenderer.addVertex(side * next.x, next.y, 0f, applyPointAlpha(edgeColor, next.alphaMul));
            }
            WingsShaderRenderer.render(modelView, depth);
        } else {
            RenderType baseType = depth ? ClientPipelines.WINGS_FILLED : ClientPipelines.WINGS_FILLED_NOTHROUGH;
            drawWingLayer(provider, stack, side, 1.0f, setAlpha(baseColor, DEFAULT_ALPHA), setAlpha(baseColor, 10), baseType, shape);
        }

        RenderType outlineType = depth ? ClientPipelines.WINGS_OUTLINE_DEPTH : ClientPipelines.WINGS_OUTLINE;
        drawWingOutline(provider, stack, side, 1.0f, setAlpha(outlineColor, (int) (DEFAULT_ALPHA * 0.62f)), outlineType, shape);

        RenderType ribsType = depth ? ClientPipelines.WINGS_RIBS_DEPTH : ClientPipelines.WINGS_RIBS;
        drawWingRibs(provider, stack, side, 0.96f, setAlpha(glowColor, (int) (DEFAULT_ALPHA * 0.20f)), ribsType, shape, ribIndices);

        stack.popPose();
    }

    private void drawWingLayer(MultiBufferSource.BufferSource provider, PoseStack stack,
                               float side, float scale, int rootColor, int edgeColor, RenderType renderType, WingPoint[] shape) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        PoseStack.Pose entry = stack.last();
        for (int i = 0; i < shape.length; i++) {
            WingPoint cur = shape[i];
            WingPoint next = shape[(i + 1) % shape.length];
            consumer.addVertex(entry, 0f, 0f, 0f).setColor(rootColor);
            consumer.addVertex(entry, side * cur.x * scale, cur.y * scale, 0f).setColor(applyPointAlpha(edgeColor, cur.alphaMul));
            consumer.addVertex(entry, side * next.x * scale, next.y * scale, 0f).setColor(applyPointAlpha(edgeColor, next.alphaMul));
        }
    }

    private void drawWingOutline(MultiBufferSource.BufferSource provider, PoseStack stack,
                                  float side, float scale, int color, RenderType renderType, WingPoint[] shape) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        PoseStack.Pose entry = stack.last();
        for (WingPoint point : shape) {
            consumer.addVertex(entry, side * point.x * scale, point.y * scale, 0f).setColor(color);
        }
        consumer.addVertex(entry, side * shape[0].x * scale, shape[0].y * scale, 0f).setColor(color);
    }

    private void drawWingRibs(MultiBufferSource.BufferSource provider, PoseStack stack,
                               float side, float scale, int color, RenderType renderType, WingPoint[] shape, int[] ribIndices) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        PoseStack.Pose entry = stack.last();
        for (int idx : ribIndices) {
            if (idx >= shape.length) continue;
            WingPoint point = shape[idx];
            consumer.addVertex(entry, 0f, 0f, 0f).setColor(setAlpha(color, Math.max(8, (int) (alpha(color) * 0.75f))));
            consumer.addVertex(entry, side * point.x * scale, point.y * scale, 0f).setColor(applyPointAlpha(color, point.alphaMul));
        }
    }

    private int applyPointAlpha(int color, float multiplier) {
        return setAlpha(color, Math.max(0, Math.min(255, (int) (alpha(color) * multiplier))));
    }

    private static int setAlpha(int color, int a) {
        return (Mth.clamp(a, 0, 255) << 24) | (color & 0x00FFFFFF);
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

    private boolean shouldRender(Player player) {
        if (FriendUtils.isFriend(player)) return targets.isSelected("Friends");
        return targets.isSelected("Players");
    }

    private boolean hasElytra(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private float resolveBodyYaw(Player player, float tickDelta) {
        float target = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        if (player != Minecraft.getInstance().player) return target;
        if (!selfBodyYawInitialized || player.tickCount < 2) {
            selfBodyYaw = target;
            selfBodyYawInitialized = true;
            return selfBodyYaw;
        }
        selfBodyYaw = approachDegrees(selfBodyYaw, target, 14f);
        return selfBodyYaw;
    }

    private static float approachDegrees(float current, float target, float maxDelta) {
        float delta = Mth.wrapDegrees(target - current);
        delta = Mth.clamp(delta, -maxDelta, maxDelta);
        return current + delta;
    }

    private WingPose resolvePose(Player player, float tickDelta) {
        float pitch = Mth.lerp(tickDelta, player.xRotO, player.getXRot());

        if (player.isFallFlying()) {
            float flightTicks = (float) player.getFallFlyingTicks() + tickDelta;
            float flightProgress = Mth.clamp(flightTicks * flightTicks / 100f, 0f, 1f);
            float pitchRotation = flightProgress * (-90f - pitch);
            return new WingPose(0.34f, 0.46f, 0f, 0f, pitchRotation, 0f,
                    0.76f, 0.92f, 0.10f, 0.58f, 0.05f, 0.06f, -5f, -2f, 0.13f);
        }

        if (player.isInWater()) return null;

        if (player.isShiftKeyDown()) {
            return new WingPose(0f, 0f, 0.96f, 0.10f, 18f, 0f,
                    1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
        }

        return new WingPose(0f, 0f, 1.38f, 0.10f, 0f, 0f,
                1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
    }

    @Override
    public void onDisable() {
        selfBodyYawInitialized = false;
        super.onDisable();
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
