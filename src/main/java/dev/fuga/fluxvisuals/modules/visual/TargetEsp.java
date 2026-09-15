package dev.fuga.fluxvisuals.modules.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.render.Render2D;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class TargetEsp extends Module {
    private static final int SWITCH_LOOK_TICKS = 40;
    private static final int HIT_FLASH_TICKS = 12;
    private static final int FADE_OUT_TICKS = 10;
    private static final int MAX_GHOSTS = 12;
    private static final int NO_TARGET = Integer.MIN_VALUE;
    public static final float GHOST_SIZE_MIN = 0.45F;
    public static final float GHOST_SIZE_MAX = 3.5F;
    public static final float GHOST_LENGTH_MIN = 0.4F;
    public static final float GHOST_LENGTH_MAX = 5.0F;
    public static final float GHOST_SIZE_RANGE = GHOST_SIZE_MAX - GHOST_SIZE_MIN;
    public static final float GHOST_LENGTH_RANGE = GHOST_LENGTH_MAX - GHOST_LENGTH_MIN;
    public static final int CRYSTAL_COUNT_MIN = 1;
    public static final int CRYSTAL_COUNT_MAX = 12;
    public static final float CRYSTAL_SIZE_MIN = 0.55F;
    public static final float CRYSTAL_SIZE_MAX = 2.2F;
    public static final float CRYSTAL_SPEED_MIN = 0.2F;
    public static final float CRYSTAL_SPEED_MAX = 4.0F;
    public static final float CRYSTAL_RADIUS_MIN = 0.35F;
    public static final float CRYSTAL_RADIUS_MAX = 1.65F;
    public static final float CRYSTAL_SIZE_RANGE = CRYSTAL_SIZE_MAX - CRYSTAL_SIZE_MIN;
    public static final float CRYSTAL_SPEED_RANGE = CRYSTAL_SPEED_MAX - CRYSTAL_SPEED_MIN;
    public static final float CRYSTAL_RADIUS_RANGE = CRYSTAL_RADIUS_MAX - CRYSTAL_RADIUS_MIN;
    private static final Identifier BLOOM_SOURCE_TEXTURE = Identifier.of("fluxvisuals", "targetesp/bloom.png");
    private static final Vec3d LIGHT_DIRECTION = new Vec3d(0.58D, 0.72D, 0.38D).normalize();
    private static final Vec3d[] CRYSTAL_UNIT_POINTS = {
            new Vec3d(0.0D, 1.0D, 0.0D),
            new Vec3d(0.0D, -1.0D, 0.0D),
            new Vec3d(1.0D, 0.0D, 0.0D),
            new Vec3d(-1.0D, 0.0D, 0.0D),
            new Vec3d(0.0D, 0.0D, 1.0D),
            new Vec3d(0.0D, 0.0D, -1.0D)
    };
    private static final int[][] CRYSTAL_FACES = {
            {0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2},
            {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}
    };
    private static final RenderPipeline SPRITE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/target_sprite"))
            .withVertexShader(Identifier.of("fluxvisuals", "core/target_sprite"))
            .withFragmentShader(Identifier.of("fluxvisuals", "core/target_sprite"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());
    // Crystal shaders output straight RGB and coverage in alpha. Preserve target alpha.
    private static final BlendFunction CRYSTAL_LIGHT_BLEND = new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE);
    private static final RenderPipeline GHOST_GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/target_ghost_glow"))
            .withVertexShader(Identifier.of("fluxvisuals", "core/target_crystal_glow"))
            .withFragmentShader(Identifier.of("fluxvisuals", "core/target_crystal_glow"))
            .withBlend(CRYSTAL_LIGHT_BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());
    private static final RenderPipeline CRYSTAL_FILL_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/target_crystal_fill"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .build());
    private static final RenderPipeline CRYSTAL_GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/target_crystal_glow"))
            .withVertexShader(Identifier.of("fluxvisuals", "core/target_crystal_glow"))
            .withFragmentShader(Identifier.of("fluxvisuals", "core/target_crystal_glow"))
            .withBlend(CRYSTAL_LIGHT_BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());
    private static final RenderPipeline CRYSTAL_CORE_GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/target_crystal_core_glow"))
            .withBlend(CRYSTAL_LIGHT_BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .build());
    private static final RenderPipeline CRYSTAL_EDGE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/target_crystal_edge"))
            .withBlend(CRYSTAL_LIGHT_BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .build());
    private static final RenderLayer CRYSTAL_FILL_LAYER = RenderLayer.of(
            "fluxvisuals_target_crystal_fill",
            1536,
            false,
            true,
            CRYSTAL_FILL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false)
    );
    private static final RenderLayer CRYSTAL_GLOW_LAYER = RenderLayer.of(
            "fluxvisuals_target_crystal_glow",
            1536,
            false,
            true,
            CRYSTAL_GLOW_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(bloomTexture(), false))
                    .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                    .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false)
    );
    private static final RenderLayer CRYSTAL_CORE_GLOW_LAYER = RenderLayer.of(
            "fluxvisuals_target_crystal_core_glow",
            1536,
            false,
            true,
            CRYSTAL_CORE_GLOW_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false)
    );
    private static final RenderLayer CRYSTAL_EDGE_LAYER = RenderLayer.of(
            "fluxvisuals_target_crystal_edge",
            1536,
            false,
            true,
            CRYSTAL_EDGE_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false)
    );
    private static final Identifier[] TARGET_MASK_TEXTURES = new Identifier[Style.values().length];
    private static final RenderLayer[] TARGET_LAYERS = new RenderLayer[Style.values().length];
    private static Identifier bloomMaskTexture;
    private static RenderLayer bloomLayer;
    private static RenderLayer ghostGlowLayer;

    public enum CrystalShader {
        NEBULA("Туманность", "block_nebula_overlay"),
        PLASMA("Плазма", "block_plasma_overlay"),
        STARFIELD("Звезды", "block_starfield_overlay"),
        NONE("Без шейдера", null);

        private final String label;
        private final String path;

        CrystalShader(String label, String path) {
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

    private static final Map<CrystalShader, RenderPipeline> CRYSTAL_SHADER_PIPELINES = new EnumMap<>(CrystalShader.class);
    private static final Map<CrystalShader, RenderLayer> CRYSTAL_SHADER_LAYERS = new EnumMap<>(CrystalShader.class);

    private static RenderLayer crystalShaderLayer(CrystalShader shader) {
        if (shader == null || shader == CrystalShader.NONE || shader.path() == null) {
            return null;
        }
        return CRYSTAL_SHADER_LAYERS.computeIfAbsent(shader, s -> {
            RenderPipeline pipeline = CRYSTAL_SHADER_PIPELINES.computeIfAbsent(s, key -> RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                            .withLocation(Identifier.of("fluxvisuals", "pipeline/target_crystal_" + key.name().toLowerCase(Locale.ROOT)))
                            .withVertexShader(Identifier.of("fluxvisuals", "core/blockesp/" + key.path()))
                            .withFragmentShader(Identifier.of("fluxvisuals", "core/blockesp/" + key.path()))
                            .withBlend(CRYSTAL_LIGHT_BLEND)
                            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                            .build()
            ));
            return RenderLayer.of(
                    "fluxvisuals_target_crystal_shader_" + s.name().toLowerCase(Locale.ROOT),
                    1536,
                    false,
                    true,
                    pipeline,
                    RenderLayer.MultiPhaseParameters.builder()
                            .texture(RenderPhase.NO_TEXTURE)
                            .target(RenderPhase.TRANSLUCENT_TARGET)
                            .build(false)
            );
        });
    }

    private Style style = Style.NORMAL;
    private TargetFilter targetFilter = TargetFilter.PLAYERS;
    private CrystalShader crystalShader = CrystalShader.NEBULA;
    private float maxDistance = 8.0F;
    private float lostDelaySeconds = 1.2F;
    private float animationSpeed = 1.0F;
    private float normalSize = 1.0F;
    private float ghostSize = 1.0F;
    private float ghostLength = 1.0F;
    private int ghostCount = 5;
    private float ghostSpeed = 1.0F;
    private float crystalSize = 1.0F;
    private int crystalCount = 6;
    private float crystalSpeed = 1.0F;
    private float crystalRadius = 0.9F;
    private float hue = 0.56F;
    private float saturation = 0.86F;
    private float value = 1.0F;
    private float alpha = 1.0F;
    private boolean redOnHit = false;
    private int targetId = NO_TARGET;
    private int lookedCandidateId = -1;
    private int candidateLookTicks;
    private int ticksSinceLookAtTarget;
    private int hitFlashTicks;
    private int animationTicks;
    private int fadeOutTicks;
    private boolean fadingOut;
    private boolean ghostsInitialized;
    private int ghostTargetId = -1;
    private Vec3d lastTargetChest;
    private Vec3d smoothedTargetMotion = Vec3d.ZERO;
    private float ghostStream;
    private double ghostAnchorX;
    private double ghostAnchorY;
    private double ghostAnchorZ;
    private double ghostPrevAnchorX;
    private double ghostPrevAnchorY;
    private double ghostPrevAnchorZ;
    private int crystalTargetId = NO_TARGET;
    private long crystalAppearStartMs;

    public TargetEsp() {
        super("TargetEsp", "Draws an animated target marker on the selected entity.", ModuleCategory.VISUALS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.world == null) {
            clearTarget();
            return;
        }

        if (hitFlashTicks > 0) {
            hitFlashTicks--;
        }
        animationTicks++;

        Entity looked = lookedEntity(client);
        boolean lookingAllowed = looked != null && allows(looked, client);
        if (lookingAllowed) {
            int lookedId = looked.getId();
            if (targetId == NO_TARGET) {
                setTarget(looked, true);
            } else if (lookedId == targetId) {
                cancelFade();
                ticksSinceLookAtTarget = 0;
                lookedCandidateId = -1;
                candidateLookTicks = 0;
            } else {
                cancelFade();
                if (lookedCandidateId == lookedId) {
                    candidateLookTicks++;
                } else {
                    lookedCandidateId = lookedId;
                    candidateLookTicks = 1;
                }
                if (candidateLookTicks >= SWITCH_LOOK_TICKS) {
                    setTarget(looked, true);
                }
            }
        } else {
            lookedCandidateId = -1;
            candidateLookTicks = 0;
        }

        if (targetId == NO_TARGET) {
            ghostsInitialized = false;
            return;
        }

        Entity target = targetEntity(client);
        if (target == null || target.isRemoved() || !allows(target, client)) {
            clearTarget();
            return;
        }

        if (client.player.squaredDistanceTo(target) > maxDistance * maxDistance) {
            beginFade();
            tickFade();
            tickGhosts(target);
            return;
        }

        if (lookingAllowed && looked.getId() == targetId) {
            ticksSinceLookAtTarget = 0;
        } else if (lookingAllowed) {
            ticksSinceLookAtTarget = 0;
        } else {
            ticksSinceLookAtTarget++;
            if (ticksSinceLookAtTarget > lostDelayTicks()) {
                beginFade();
            }
        }
        tickFade();
        tickGhosts(target);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        clearTarget();
    }

    public void forceTarget(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || entity == null || client == null || !allows(entity, client)) {
            return;
        }
        setTarget(entity, false);
        hitFlashTicks = HIT_FLASH_TICKS;
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client == null || client.world == null
                || context.matrixStack() == null || context.consumers() == null || context.camera() == null) {
            return;
        }

        float tickDelta = context.tickCounter().getTickProgress(false);
        Vec3d camera = context.camera().getPos();
        int rgb = currentColor();

        Entity target = targetEntity(client);
        if (target != null && !target.isRemoved() && allows(target, client)) {
            float alpha = visibilityAlpha();
            if (alpha > 0.02F) {
                renderEntityEsp(context, target, camera, tickDelta, rgb, alpha, false);
            }
        }

    }

    private void renderEntityEsp(WorldRenderContext context, Entity target, Vec3d camera, float tickDelta, int rgb,
                                 float alpha, boolean extraStaticTarget) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d chest = chestPos(target, tickDelta);
        float renderDistance = extraStaticTarget || !fadingOut ? maxDistance : maxDistance + 4.0F;
        if (client.player != null && client.player.squaredDistanceTo(chest) > renderDistance * renderDistance) {
            return;
        }

        if (style == Style.CRYSTALS) {
            renderCrystals(context, target, chest, camera, tickDelta, rgb, alpha);
        } else if (style == Style.GHOSTS) {
            if (extraStaticTarget) {
                renderStaticGhosts(context, target, camera, tickDelta, rgb, alpha);
            } else {
                renderGhosts(context, target, chest, camera, tickDelta, rgb, alpha);
            }
        } else {
            renderNormal(context, target, chest, camera, tickDelta, rgb, alpha);
        }
    }

    private void renderNormal(WorldRenderContext context, Entity target, Vec3d chest, Vec3d camera, float tickDelta, int rgb, float alpha) {
        Vec3d drawPos = target == null ? chest : surfacePos(target, chest, camera, 0.18D);
        float time = animationSeconds(tickDelta);
        float size = normalSize * (0.78F + 0.07F * (float) Math.sin(time * 3.8F));
        float rotation = time * (1.35F + animationSpeed * 1.55F);
        MatrixStack matrices = context.matrixStack();
        RenderLayer layer = targetLayer(style);
        VertexConsumer consumer = context.consumers().getBuffer(layer);

        matrices.push();
        matrices.translate(drawPos.x - camera.x, drawPos.y - camera.y, drawPos.z - camera.z);
        matrices.multiply(context.camera().getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(rotation));
        matrices.scale(size, size, size);
        drawQuad(consumer, matrices.peek(), 1.0F, alpha, rgb);
        matrices.pop();
        drawLayer(context, layer);
    }

    private static final class ScreenPos {
        final float x;
        final float y;
        final float depth;

        ScreenPos(float x, float y, float depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    private ScreenPos projectToScreen(WorldRenderContext context, Vec3d pos, Quaternionf camRotInv, Matrix4f proj,
                                      float scaledWidth, float scaledHeight) {
        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();

        float relX = (float) (pos.x - camPos.x);
        float relY = (float) (pos.y - camPos.y);
        float relZ = (float) (pos.z - camPos.z);

        Vector3f eye = new Vector3f(relX, relY, relZ);
        eye.rotate(camRotInv);

        Vector4f clip = new Vector4f(eye.x, eye.y, eye.z, 1.0F);
        proj.transform(clip);

        if (clip.w <= 0.05F) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        float screenX = (ndcX + 1.0F) * 0.5F * scaledWidth;
        float screenY = (1.0F - ndcY) * 0.5F * scaledHeight;

        if (screenX < -250.0F || screenX > scaledWidth + 250.0F || screenY < -250.0F || screenY > scaledHeight + 250.0F) {
            return null;
        }

        return new ScreenPos(screenX, screenY, clip.w);
    }

    private void renderCrystals(WorldRenderContext context, Entity target, Vec3d chest, Vec3d camera, float tickDelta, int rgb, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (target == null || client == null || client.getWindow() == null) {
            return;
        }
        ensureCrystalState(target);
        long nowMs = System.currentTimeMillis();
        Vec3d center = chest;
        double distance = client.player == null ? 0.0D : Math.sqrt(client.player.getPos().squaredDistanceTo(target.getPos()));
        float appear = smoothstep(0.0F, 1.0F, Math.min(1.0F, (nowMs - crystalAppearStartMs) / 520.0F));
        float distanceAlpha = (float) (1.0D / (1.0D + 0.12D * distance));
        double time = ((animationTicks + tickDelta) / 20.0D) * (0.45D + crystalSpeed * 0.95D);
        int count = clampedCrystalCount();

        MatrixStack matrices = context.matrixStack();
        MatrixStack.Entry entry = matrices.peek();
        float bodyAlpha = alpha * appear * (0.70F + distanceAlpha * 0.30F);
        float edgeAlpha = alpha * appear * distanceAlpha;

        if (bodyAlpha <= 0.004F) return;
        CrystalPose[] poses = new CrystalPose[count];
        for (int i = 0; i < count; i++) {
            poses[i] = crystalPose(center, i, count, time);
        }
        // One depth-tested world-space batch; halo size follows perspective.
        VertexConsumer glow = context.consumers().getBuffer(CRYSTAL_GLOW_LAYER);
        for (int i = 0; i < count; i++) {
            drawGlowSprite(context, matrices, glow, poses[i].center(), camera,
                    0.0F, 0.95F * crystalSize, bodyAlpha * 0.72F, rgb);
        }
        drawLayer(context, CRYSTAL_GLOW_LAYER);
        // Pass 2: Translucent Shaded Crystal Faces (Rich Gemstone Color)
        VertexConsumer fill = context.consumers().getBuffer(CRYSTAL_FILL_LAYER);
        for (int i = 0; i < count; i++) {
            CrystalPose pose = poses[i];
            drawCrystalFaces(fill, entry, camera, pose.center(), 0.175D * crystalSize, pose.yaw(), pose.pitch(), pose.roll(),
                    bodyAlpha * 0.88F, rgb, nowMs, i);
            drawCrystalInnerDepth(fill, entry, camera, pose.center(), 0.175D * crystalSize, pose.yaw(), pose.pitch(), pose.roll(),
                    bodyAlpha * 0.65F, rgb, nowMs, i);
        }
        drawLayer(context, CRYSTAL_FILL_LAYER);

        // Pass 3: Procedural Animated Shader Pass on Crystal Geometry (Nebula / Plasma / Starfield)
        RenderLayer shaderLayer = crystalShaderLayer(crystalShader);
        if (shaderLayer != null) {
            VertexConsumer shaderConsumer = context.consumers().getBuffer(shaderLayer);
            for (int i = 0; i < count; i++) {
                CrystalPose pose = poses[i];
                drawCrystalFaces(shaderConsumer, entry, camera, pose.center(), 0.175D * crystalSize,
                        pose.yaw(), pose.pitch(), pose.roll(), bodyAlpha * 0.75F, rgb, nowMs, i);
            }
            drawLayer(context, shaderLayer);
        }

        // Pass 4: Inner Core Gem Accent
        VertexConsumer coreConsumer = context.consumers().getBuffer(CRYSTAL_CORE_GLOW_LAYER);
        int coreColor = mixRgb(rgb, 0x00FFFFFF, 0.35F);
        for (int i = 0; i < count; i++) {
            CrystalPose pose = poses[i];
            drawCrystalCore(coreConsumer, entry, camera, pose.center(), 0.06D * crystalSize, pose.yaw(), pose.pitch(), pose.roll(),
                    bodyAlpha * 0.70F, coreColor, nowMs, i);
        }
        drawLayer(context, CRYSTAL_CORE_GLOW_LAYER);

        // Pass 5: Volumetric Antialiased Glowing Edges
        VertexConsumer edgeConsumer = context.consumers().getBuffer(CRYSTAL_EDGE_LAYER);
        for (int i = 0; i < count; i++) {
            CrystalPose pose = poses[i];
            drawCrystalSmoothEdges(edgeConsumer, entry, camera, pose.center(), 0.175D * crystalSize, pose.yaw(), pose.pitch(), pose.roll(),
                    edgeAlpha, rgb, nowMs, i);
        }
        drawLayer(context, CRYSTAL_EDGE_LAYER);
    }

    private void ensureCrystalState(Entity target) {
        if (crystalTargetId == target.getId()) {
            return;
        }
        resetCrystalState(target.getId());
    }

    private void resetCrystalState(int targetId) {
        crystalTargetId = targetId;
        crystalAppearStartMs = System.currentTimeMillis();
    }

    private CrystalPose crystalPose(Vec3d center, int index, int count, double time) {
        double angle = time + index * (Math.PI * 2.0D / Math.max(1, count));
        double yWave = 0.22D * crystalSize * Math.sin(time * 2.0D + index * 0.7D);
        Vec3d pos = center.add(Math.cos(angle) * crystalRadius, yWave, Math.sin(angle) * crystalRadius);
        double yaw = angle + Math.PI / 4.0D;
        double pitch = 0.40D * Math.sin(time * 1.6D + index);
        double roll = 0.25D * Math.cos(time * 1.1D + index * 0.3D);
        return new CrystalPose(pos, yaw, pitch, roll);
    }

    private void drawCrystalFaces(VertexConsumer fill, MatrixStack.Entry entry, Vec3d camera, Vec3d center, double size,
                                  double yaw, double pitch, double roll, float faceAlpha, int rgb, long nowMs, int seed) {
        if (faceAlpha <= 0.004F) {
            return;
        }
        for (int[] face : CRYSTAL_FACES) {
            Vec3d l0 = liquidDeform(crystalLocalPoint(face[0], size), nowMs, seed + face[0] * 13);
            Vec3d l1 = liquidDeform(crystalLocalPoint(face[1], size), nowMs, seed + face[1] * 13 + 7);
            Vec3d l2 = liquidDeform(crystalLocalPoint(face[2], size), nowMs, seed + face[2] * 13 + 17);
            Vec3d v0 = worldify(center, l0, yaw, pitch, roll);
            Vec3d v1 = worldify(center, l1, yaw, pitch, roll);
            Vec3d v2 = worldify(center, l2, yaw, pitch, roll);
            Vec3d normal = normalizeSafe(cross(v1.subtract(v0), v2.subtract(v0)), new Vec3d(0.0D, 1.0D, 0.0D));
            float diffuse = (float) Math.max(0.0D, dot(normal, LIGHT_DIRECTION));
            float lighting = 0.72F + diffuse * 0.28F;
            int faceColor = shadeArgb(colorWithAlpha(rgb, faceAlpha), lighting);
            drawTriangle(fill, entry, camera, v0, v1, v2, faceColor);
        }
    }

    private void drawCrystalInnerDepth(VertexConsumer fill, MatrixStack.Entry entry, Vec3d camera, Vec3d center, double size,
                                       double yaw, double pitch, double roll, float alpha, int rgb, long nowMs, int seed) {
        if (alpha <= 0.004F) {
            return;
        }
        double innerSize = size * 0.58D;
        Vec3d view = normalizeSafe(camera.subtract(center), new Vec3d(0.0D, 0.0D, 1.0D));
        int innerRgb = shadeArgb(rgb, 0.85F);
        for (int[] face : CRYSTAL_FACES) {
            Vec3d l0 = liquidDeform(crystalLocalPoint(face[0], innerSize), nowMs, seed + face[0] * 17 + 3);
            Vec3d l1 = liquidDeform(crystalLocalPoint(face[1], innerSize), nowMs, seed + face[1] * 17 + 11);
            Vec3d l2 = liquidDeform(crystalLocalPoint(face[2], innerSize), nowMs, seed + face[2] * 17 + 23);
            Vec3d v0 = worldify(center, l0, yaw, pitch, roll);
            Vec3d v1 = worldify(center, l1, yaw, pitch, roll);
            Vec3d v2 = worldify(center, l2, yaw, pitch, roll);
            Vec3d normal = normalizeSafe(cross(v1.subtract(v0), v2.subtract(v0)), new Vec3d(0.0D, 1.0D, 0.0D));
            float facing = clamp01((float) (dot(normal, view) * 0.5D + 0.5D));
            int faceColor = colorWithAlpha(innerRgb, alpha * (0.35F + facing * 0.35F));
            drawTriangle(fill, entry, camera, v0, v1, v2, faceColor);
        }
    }

    private void drawCrystalCore(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d camera, Vec3d center, double size,
                                 double yaw, double pitch, double roll, float alpha, int rgb, long nowMs, int seed) {
        if (alpha <= 0.004F) {
            return;
        }
        int color = colorWithAlpha(rgb, alpha);
        for (int[] face : CRYSTAL_FACES) {
            Vec3d l0 = crystalLocalPoint(face[0], size);
            Vec3d l1 = crystalLocalPoint(face[1], size);
            Vec3d l2 = crystalLocalPoint(face[2], size);
            Vec3d v0 = worldify(center, l0, yaw, pitch, roll);
            Vec3d v1 = worldify(center, l1, yaw, pitch, roll);
            Vec3d v2 = worldify(center, l2, yaw, pitch, roll);
            drawTriangle(consumer, entry, camera, v0, v1, v2, color);
        }
    }

    private void drawCrystalSmoothEdges(VertexConsumer edgeConsumer, MatrixStack.Entry entry, Vec3d camera, Vec3d center, double size,
                                        double yaw, double pitch, double roll, float edgeAlpha, int rgb, long nowMs, int seed) {
        if (edgeAlpha <= 0.004F) {
            return;
        }
        int outerColor = colorWithAlpha(mixRgb(rgb, 0x00FFFFFF, 0.15F), edgeAlpha * 0.85F);
        int innerColor = colorWithAlpha(mixRgb(rgb, 0x00FFFFFF, 0.45F), edgeAlpha * 0.95F);
        double outerThickness = 0.0038D * (crystalSize + 0.35D);
        double innerThickness = 0.0016D * (crystalSize + 0.35D);

        Vec3d[] points = new Vec3d[CRYSTAL_UNIT_POINTS.length];
        for (int i = 0; i < points.length; i++) {
            points[i] = worldify(center, crystalLocalPoint(i, size), yaw, pitch, roll);
        }
        // Opposite pairs share an axis; every other pair is one unique edge.
        for (int a = 0; a < points.length; a++) {
            for (int b = a + 1; b < points.length; b++) {
                if (a / 2 == b / 2) continue;
                drawSmoothEdge(edgeConsumer, entry, camera, points[a], points[b], outerColor, outerThickness);
                drawSmoothEdge(edgeConsumer, entry, camera, points[a], points[b], innerColor, innerThickness);
            }
        }
    }
    private static void drawSmoothEdge(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d camera, Vec3d from, Vec3d to, int color, double thickness) {
        if ((color >>> 24) <= 0 || thickness <= 0.0D) {
            return;
        }
        Vec3d edge = to.subtract(from);
        if (edge.lengthSquared() < 1.0E-8D) {
            return;
        }
        Vec3d mid = from.add(to).multiply(0.5D);
        Vec3d view = camera.subtract(mid);
        Vec3d perp = cross(edge, view);
        if (perp.lengthSquared() < 1.0E-8D) {
            perp = new Vec3d(0.0D, thickness, 0.0D);
        } else {
            perp = perp.normalize().multiply(thickness);
        }

        Vec3d p0 = from.subtract(perp).subtract(camera);
        Vec3d p1 = from.add(perp).subtract(camera);
        Vec3d p2 = to.add(perp).subtract(camera);
        Vec3d p3 = to.subtract(perp).subtract(camera);

        colorVertex(consumer, entry, p0, color);
        colorVertex(consumer, entry, p1, color);
        colorVertex(consumer, entry, p2, color);

        colorVertex(consumer, entry, p0, color);
        colorVertex(consumer, entry, p2, color);
        colorVertex(consumer, entry, p3, color);
    }

    private Vec3d crystalLocalPoint(int index, double size) {
        Vec3d unit = CRYSTAL_UNIT_POINTS[index];
        return new Vec3d(unit.x * size * 0.86D, unit.y * size * 1.28D, unit.z * size * 0.86D);
    }

    private void renderGhosts(WorldRenderContext context, Entity target, Vec3d chest, Vec3d camera, float tickDelta, int rgb, float alpha) {
        MatrixStack matrices = context.matrixStack();
        Vec3d targetBase = target.getLerpedPos(tickDelta);
        Vec3d motion = new Vec3d(smoothedTargetMotion.x, 0.0D, smoothedTargetMotion.z);
        float stream = ghostStream;
        Vec3d anchoredBase = ghostAnchor(tickDelta, targetBase);
        Vec3d base = anchoredBase.add(targetBase.subtract(anchoredBase).multiply(stream * 0.14D));
        double height = target.getHeight();
        Vec3d drag = motion.lengthSquared() > 1.0E-6D
                ? motion.normalize().multiply(-(0.08D + Math.min(1.05D, motion.length() * (7.4D + ghostSpeed * 2.0D))) * stream)
                : Vec3d.ZERO;
        double time = (animationTicks + tickDelta) * (0.12D + ghostSpeed * 0.065D);
        int count = Math.max(1, Math.min(MAX_GHOSTS, ghostCount));
        int baseTailSteps = Math.max(12, Math.min(36, Math.round(12.0F + ghostLength * 12.0F)));
        int tailSteps = Math.max(baseTailSteps, Math.min(76, Math.round(baseTailSteps * 2.15F)));
        double baseTailSpacing = 0.028D + ghostLength * 0.018D;
        double tailSpacing = baseTailSpacing * (baseTailSteps - 1.0D) / Math.max(1.0D, tailSteps - 1.0D);
        float sizeDrop = 0.040F + ghostLength * 0.020F;
        float glowScale = 1.70F + ghostLength * 0.18F;
        float trailDrag = 0.50F + ghostLength * 0.36F;
        int glowRgb = ghostGlowColor(rgb);
        RenderLayer glowLayer = ghostGlowLayer();
        VertexConsumer glowConsumer = context.consumers().getBuffer(glowLayer);
        // Outer broad bloom halo (crystal style additive lighting)
        renderGhostSprites(context, matrices, glowConsumer, camera, base, height, drag, time, count, tailSteps,
                tailSpacing, sizeDrop, trailDrag, glowScale, 0.28F, stream, alpha, glowRgb);
        // Inner dense glowing core trail (crystal style additive lighting)
        renderGhostSprites(context, matrices, glowConsumer, camera, base, height, drag, time, count, tailSteps,
                tailSpacing, sizeDrop, trailDrag, 1.0F, 0.42F, stream, alpha, rgb);
        drawLayer(context, glowLayer);
    }

    private void renderStaticGhosts(WorldRenderContext context, Entity target, Vec3d camera, float tickDelta, int rgb, float alpha) {
        MatrixStack matrices = context.matrixStack();
        Vec3d base = target.getLerpedPos(tickDelta);
        double height = target.getHeight();
        double time = (animationTicks + tickDelta) * (0.12D + ghostSpeed * 0.065D);
        int count = Math.max(1, Math.min(MAX_GHOSTS, ghostCount));
        int baseTailSteps = Math.max(12, Math.min(36, Math.round(12.0F + ghostLength * 12.0F)));
        int tailSteps = Math.max(baseTailSteps, Math.min(76, Math.round(baseTailSteps * 2.15F)));
        double baseTailSpacing = 0.028D + ghostLength * 0.018D;
        double tailSpacing = baseTailSpacing * (baseTailSteps - 1.0D) / Math.max(1.0D, tailSteps - 1.0D);
        float sizeDrop = 0.040F + ghostLength * 0.020F;
        float glowScale = 1.70F + ghostLength * 0.18F;
        float trailDrag = 0.50F + ghostLength * 0.36F;
        int glowRgb = ghostGlowColor(rgb);

        RenderLayer glowLayer = ghostGlowLayer();
        VertexConsumer glowConsumer = context.consumers().getBuffer(glowLayer);
        // Outer broad bloom halo (crystal style additive lighting)
        renderGhostSprites(context, matrices, glowConsumer, camera, base, height, Vec3d.ZERO, time, count,
                tailSteps, tailSpacing, sizeDrop, trailDrag, glowScale, 0.28F, 0.0F, alpha, glowRgb);
        // Inner dense glowing core trail (crystal style additive lighting)
        renderGhostSprites(context, matrices, glowConsumer, camera, base, height, Vec3d.ZERO, time, count,
                tailSteps, tailSpacing, sizeDrop, trailDrag, 1.0F, 0.42F, 0.0F, alpha, rgb);
        drawLayer(context, glowLayer);
    }

    private void renderGhostSprites(WorldRenderContext context, MatrixStack matrices, VertexConsumer consumer, Vec3d camera,
                                    Vec3d entityBase, double entityHeight, Vec3d drag, double time, int count, int tailSteps,
                                    double tailSpacing, float sizeDrop, float trailDrag, float sizeScale, float spriteAlpha,
                                    float stream, float alpha, int rgb) {
        for (int i = 0; i < count; i++) {
            // Distinct angular phase around 360 deg
            double phase = (Math.PI * 2.0D * i) / count;
            // Distinct height level along the entity body
            double heightFraction = (count <= 1) ? 0.5D : ((double) i / (count - 1));
            double yOffset = 0.22D + entityHeight * (0.15D + 0.65D * heightFraction);
            Vec3d ghostCenter = entityBase.add(0.0D, yOffset, 0.0D);

            // Staggered orbital plane inclination (tilted 3D ribbons that never cross)
            double tiltPitch = Math.sin(i * 1.75D + 0.35D) * 0.32D;
            double tiltRoll = Math.cos(i * 1.75D + 0.35D) * 0.32D;
            // Distinct clearance radius
            double ghostRadius = 0.44D + ((i % 3) * 0.08D) + (count > 6 ? (i * 0.02D) : 0.0D);
            float strandScale = 0.94F + (i % 3) * 0.035F;

            for (int j = 0; j < tailSteps; j++) {
                float progress = j / (float) (tailSteps - 1);
                double trailTime = time + phase - j * tailSpacing;
                float smooth = 1.0F - progress;
                float shimmer = 0.92F + 0.08F * (float) Math.sin(trailTime * 1.45D + phase * 0.65D);
                float ghostAlpha = alpha * smooth * smooth * spriteAlpha * shimmer;
                if (ghostAlpha <= 0.006F) {
                    continue;
                }

                Vec3d pos = ghostPathPoint(ghostCenter, drag, trailTime, phase, ghostRadius, tiltPitch, tiltRoll, progress, trailDrag, stream);
                Vec3d next = ghostPathPoint(ghostCenter, drag, trailTime - tailSpacing, phase, ghostRadius, tiltPitch, tiltRoll,
                        Math.min(1.0F, progress + 1.0F / Math.max(1, tailSteps - 1)), trailDrag, stream);
                Vec3d motion = pos.subtract(next);
                float sizeProgress = smoothstep(0.0F, 1.0F, Math.max(0.12F, progress));
                float sizePulse = 0.96F + 0.04F * (float) Math.sin(trailTime * 1.1D + phase);
                float streamTaper = 1.0F - stream * progress * 0.20F;
                float spriteSize = ghostSize * Math.max(0.045F, 0.128F - sizeProgress * sizeDrop)
                        * sizeScale * strandScale * sizePulse * streamTaper * (count > 8 ? 0.82F : 1.0F);
                float rotation = (float) Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z))
                        + (float) Math.toRadians(j * 2.4F + i * 18.0F);
                drawGhostSprite(context, matrices, consumer, pos.add(towardCamera(pos, camera, 0.12D)), camera,
                        rotation, spriteSize, ghostAlpha, rgb);
            }
        }
    }

    private Vec3d ghostPathPoint(Vec3d center, Vec3d drag, double trailTime, double phase,
                                 double radius, double tiltPitch, double tiltRoll,
                                 float progress, float trailDrag, float stream) {
        double sin = Math.sin(trailTime);
        double cos = Math.cos(trailTime);
        double weave = Math.sin(trailTime * 1.35D + phase * 0.8D) * 0.020D * (1.0D - progress * 0.45D);
        double yWave = Math.sin(trailTime * 0.95D + phase * 1.4D) * 0.09D * (1.0D - progress * 0.35D);

        double ox = cos * radius - sin * weave;
        double oz = sin * radius + cos * weave;
        double oy = yWave + ox * tiltPitch + oz * tiltRoll;

        Vec3d lag = drag.multiply(progress * trailDrag * 0.95D);
        Vec3d orbit = center.add(ox, oy, oz).add(lag);
        if (stream <= 0.001F || drag.lengthSquared() < 1.0E-6D) {
            return orbit;
        }

        Vec3d back = drag.normalize();
        Vec3d side = new Vec3d(-back.z, 0.0D, back.x);
        double phaseSide = Math.sin(phase);
        double phaseHeight = Math.cos(phase);
        double lane = (phaseSide * (0.18D + (radius - 0.44D) * 0.70D)
                + Math.sin(phase + trailTime * 0.22D) * 0.020D);
        double laneFade = 1.0D - progress * 0.62D;
        double streamWave = Math.sin(trailTime * 0.80D + phase) * 0.024D * (1.0D - progress * 0.35D);
        Vec3d streamPos = center
                .add(side.multiply(lane * laneFade))
                .add(0.0D, yWave * 0.13D + phaseHeight * 0.16D * laneFade + streamWave, 0.0D)
                .add(drag.multiply(progress * (0.55D + trailDrag * 0.58D)));
        return mixVec(orbit, streamPos, stream * 0.82F);
    }

    private void drawGhostSprite(WorldRenderContext context, MatrixStack matrices, VertexConsumer consumer, Vec3d pos,
                                 Vec3d camera, float rotation, float size, float alpha, int rgb) {
        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        matrices.multiply(context.camera().getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(rotation));
        matrices.scale(size, size, size);
        drawQuad(consumer, matrices.peek(), 1.0F, alpha, rgb);
        matrices.pop();
    }

    private Vec3d chestPos(Entity target, float tickDelta) {
        Vec3d pos = target.getLerpedPos(tickDelta);
        return pos.add(0.0D, target.getHeight() * 0.56D, 0.0D);
    }

    private Vec3d chestPos(Entity target) {
        return target.getPos().add(0.0D, target.getHeight() * 0.56D, 0.0D);
    }

    private Vec3d surfacePos(Entity target, Vec3d chest, Vec3d camera, double extra) {
        double offset = Math.max(0.36D, target.getWidth() * 0.68D + extra);
        return chest.add(towardCamera(chest, camera, offset));
    }

    private void tickGhosts(Entity target) {
        if (style != Style.GHOSTS || target == null || targetId == NO_TARGET) {
            ghostsInitialized = false;
            lastTargetChest = null;
            smoothedTargetMotion = Vec3d.ZERO;
            ghostStream = 0.0F;
            return;
        }

        Vec3d chest = chestPos(target);
        Vec3d base = target.getPos();
        if (!ghostsInitialized || ghostTargetId != target.getId() || lastTargetChest == null) {
            ghostTargetId = target.getId();
            ghostsInitialized = true;
            lastTargetChest = chest;
            smoothedTargetMotion = Vec3d.ZERO;
            ghostStream = 0.0F;
            setGhostAnchor(base);
            return;
        }

        Vec3d rawMotion = chest.subtract(lastTargetChest);
        smoothedTargetMotion = smoothedTargetMotion.multiply(0.76D).add(rawMotion.multiply(0.24D));
        double anchorLag = horizontalDistance(new Vec3d(ghostAnchorX, ghostAnchorY, ghostAnchorZ), base);
        float targetStream = movementStreamAmount(smoothedTargetMotion, anchorLag);
        float streamSpeed = targetStream > ghostStream ? 0.105F : 0.070F;
        ghostStream += (targetStream - ghostStream) * streamSpeed;
        if (ghostStream < 0.01F) {
            ghostStream = 0.0F;
        }
        lastTargetChest = chest;
        tickGhostAnchor(base);
    }

    private Vec3d ghostAnchor(float tickDelta, Vec3d fallback) {
        if (!ghostsInitialized) {
            return fallback;
        }
        return new Vec3d(
                ghostPrevAnchorX + (ghostAnchorX - ghostPrevAnchorX) * tickDelta,
                ghostPrevAnchorY + (ghostAnchorY - ghostPrevAnchorY) * tickDelta,
                ghostPrevAnchorZ + (ghostAnchorZ - ghostPrevAnchorZ) * tickDelta
        );
    }

    private void setGhostAnchor(Vec3d pos) {
        ghostAnchorX = pos.x;
        ghostAnchorY = pos.y;
        ghostAnchorZ = pos.z;
        ghostPrevAnchorX = pos.x;
        ghostPrevAnchorY = pos.y;
        ghostPrevAnchorZ = pos.z;
    }

    private void tickGhostAnchor(Vec3d targetBase) {
        Vec3d current = new Vec3d(ghostAnchorX, ghostAnchorY, ghostAnchorZ);
        if (current.squaredDistanceTo(targetBase) > 144.0D) {
            setGhostAnchor(targetBase);
            return;
        }

        ghostPrevAnchorX = ghostAnchorX;
        ghostPrevAnchorY = ghostAnchorY;
        ghostPrevAnchorZ = ghostAnchorZ;
        double follow = 0.24D + ghostSpeed * 0.045D + ghostStream * 0.12D;
        ghostAnchorX += (targetBase.x - ghostAnchorX) * follow;
        ghostAnchorY += (targetBase.y - ghostAnchorY) * follow;
        ghostAnchorZ += (targetBase.z - ghostAnchorZ) * follow;
    }

    private Vec3d towardCamera(Vec3d pos, Vec3d camera, double amount) {
        Vec3d delta = camera.subtract(pos);
        double length = delta.length();
        if (length < 1.0E-4D) {
            return Vec3d.ZERO;
        }
        return delta.multiply(amount / length);
    }

    private Vec3d worldify(Vec3d origin, Vec3d local, double yaw, double pitch, double roll) {
        Vec3d value = rotateY(local, yaw);
        value = rotateX(value, pitch);
        value = rotateZ(value, roll);
        return origin.add(value);
    }

    private static Vec3d rotateX(Vec3d value, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3d(value.x, value.y * cos - value.z * sin, value.y * sin + value.z * cos);
    }

    private static Vec3d rotateY(Vec3d value, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3d(value.x * cos + value.z * sin, value.y, -value.x * sin + value.z * cos);
    }

    private static Vec3d rotateZ(Vec3d value, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3d(value.x * cos - value.y * sin, value.x * sin + value.y * cos, value.z);
    }

    private static Vec3d liquidDeform(Vec3d value, long nowMs, int seed) {
        double time = nowMs / 1000.0D;
        double x = value.x * (1.0D + 0.030D * Math.sin(time * 1.7D + seed * 0.9D));
        double y = value.y * (1.0D + 0.030D * Math.cos(time * 1.3D + seed * 0.7D));
        double z = value.z * (1.0D + 0.030D * Math.sin(time * 2.0D + seed * 1.1D));
        double ripple = 0.055D * Math.sin(time * 2.2D + x * 8.0D + y * 9.0D + z * 7.0D + seed);
        return new Vec3d(x, y, z).multiply(1.0D + ripple);
    }

    private static Vec3d cross(Vec3d first, Vec3d second) {
        return new Vec3d(
                first.y * second.z - first.z * second.y,
                first.z * second.x - first.x * second.z,
                first.x * second.y - first.y * second.x
        );
    }

    private static double dot(Vec3d first, Vec3d second) {
        return first.x * second.x + first.y * second.y + first.z * second.z;
    }

    private static Vec3d normalizeSafe(Vec3d value, Vec3d fallback) {
        return value.lengthSquared() < 1.0E-8D ? fallback : value.normalize();
    }

    private static float movementStreamAmount(Vec3d motion, double anchorLag) {
        float speed = (float) motion.length();
        float speedStream = smootherstep01((speed - 0.135F) / 0.120F);
        float lagStream = smootherstep01(((float) anchorLag - 0.85F) / 1.15F);
        return Math.max(speedStream, lagStream);
    }

    private static double horizontalDistance(Vec3d first, Vec3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3d mixVec(Vec3d from, Vec3d to, float delta) {
        double t = clamp01(delta);
        return from.add(to.subtract(from).multiply(t));
    }

    private Entity lookedEntity(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY) {
            return hit.getEntity();
        }

        Entity fakePlayer = FluxVisualsClient.MODULE_MANAGER.getFakePlayer().getEntity();
        if (!allows(fakePlayer, client) || client.player == null) {
            return null;
        }

        Vec3d start = client.player.getCameraPosVec(1.0F);
        Vec3d end = start.add(client.player.getRotationVec(1.0F).multiply(maxDistance));
        return fakePlayer.getBoundingBox().expand(0.18D).raycast(start, end)
                .filter(pos -> !isBlockedByCrosshairBlock(client, start, pos))
                .map(pos -> fakePlayer)
                .orElse(null);
    }

    private boolean isBlockedByCrosshairBlock(MinecraftClient client, Vec3d start, Vec3d entityHitPos) {
        return client.crosshairTarget != null
                && client.crosshairTarget.getType() == HitResult.Type.BLOCK
                && client.crosshairTarget.getPos().squaredDistanceTo(start) + 0.0001D < entityHitPos.squaredDistanceTo(start);
    }

    private Entity targetEntity(MinecraftClient client) {
        if (targetId == NO_TARGET || client == null || client.world == null) {
            return null;
        }
        Entity target = client.world.getEntityById(targetId);
        if (target != null) {
            return target;
        }
        Entity fakePlayer = FluxVisualsClient.MODULE_MANAGER.getFakePlayer().getEntity();
        return fakePlayer != null && fakePlayer.getId() == targetId ? fakePlayer : null;
    }

    private boolean allows(Entity entity, MinecraftClient client) {
        if (entity == null || client == null || entity == client.player) {
            return false;
        }
        if (entity.isRemoved() || !entity.canHit()) {
            return false;
        }
        if (entity.isInvisible()) {
            return false;
        }
        return switch (targetFilter) {
            case PLAYERS -> entity instanceof PlayerEntity;
            case MOBS -> entity instanceof MobEntity;
            case ALL -> entity instanceof LivingEntity;
        };
    }

    private void setTarget(Entity entity, boolean fromLook) {
        boolean changed = targetId != entity.getId();
        targetId = entity.getId();
        lookedCandidateId = -1;
        candidateLookTicks = 0;
        ticksSinceLookAtTarget = fromLook ? 0 : Math.min(ticksSinceLookAtTarget, lostDelayTicks() / 2);
        cancelFade();
        if (changed) {
            ghostsInitialized = false;
            ghostTargetId = -1;
            lastTargetChest = null;
            smoothedTargetMotion = Vec3d.ZERO;
            ghostStream = 0.0F;
            if (style == Style.CRYSTALS) {
                resetCrystalState(entity.getId());
            }
        }
    }

    private void clearTarget() {
        targetId = NO_TARGET;
        lookedCandidateId = -1;
        candidateLookTicks = 0;
        ticksSinceLookAtTarget = 0;
        hitFlashTicks = 0;
        fadeOutTicks = 0;
        fadingOut = false;
        ghostsInitialized = false;
        ghostTargetId = -1;
        lastTargetChest = null;
        smoothedTargetMotion = Vec3d.ZERO;
        ghostStream = 0.0F;
        crystalTargetId = NO_TARGET;
    }

    private void beginFade() {
        if (!fadingOut) {
            fadingOut = true;
            fadeOutTicks = 0;
        }
    }

    private void cancelFade() {
        fadingOut = false;
        fadeOutTicks = 0;
    }

    private void tickFade() {
        if (!fadingOut) {
            return;
        }
        fadeOutTicks++;
        if (fadeOutTicks >= FADE_OUT_TICKS) {
            clearTarget();
        }
    }

    private int lostDelayTicks() {
        return Math.max(1, Math.round(lostDelaySeconds * 20.0F));
    }

    private float visibilityAlpha() {
        float baseAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        float fade = fadingOut ? Math.max(0.0F, 1.0F - fadeOutTicks / (float) FADE_OUT_TICKS) : 1.0F;
        int delay = lostDelayTicks();
        if (ticksSinceLookAtTarget <= 0 || delay <= 1) {
            return baseAlpha * fade;
        }
        return baseAlpha * fade * Math.max(0.18F, 1.0F - ticksSinceLookAtTarget / (float) delay * 0.62F);
    }

    private float animationSeconds(float tickDelta) {
        return ((animationTicks + tickDelta) / 20.0F) * Math.max(0.1F, animationSpeed);
    }

    private int currentColor() {
        int base = colorRgb();
        if (!redOnHit || hitFlashTicks <= 0) {
            return base;
        }
        float mix = hitFlashTicks / (float) HIT_FLASH_TICKS;
        int red = (base >> 16) & 255;
        int green = (base >> 8) & 255;
        int blue = base & 255;
        int nextRed = Math.round(red + (255 - red) * mix);
        int nextGreen = Math.round(green * (1.0F - 0.72F * mix));
        int nextBlue = Math.round(blue * (1.0F - 0.72F * mix));
        return (nextRed << 16) | (nextGreen << 8) | nextBlue;
    }

    private int ghostGlowColor(int rgb) {
        if (!redOnHit || hitFlashTicks <= 0) {
            return rgb;
        }
        float mix = smoothstep(0.0F, 1.0F, hitFlashTicks / (float) HIT_FLASH_TICKS);
        return mixRgb(rgb, 0x00FF2020, Math.min(1.0F, mix * 0.78F));
    }

    private static int mixRgb(int from, int to, float delta) {
        float t = clamp01(delta);
        int fromRed = (from >> 16) & 255;
        int fromGreen = (from >> 8) & 255;
        int fromBlue = from & 255;
        int toRed = (to >> 16) & 255;
        int toGreen = (to >> 8) & 255;
        int toBlue = to & 255;
        int red = Math.round(fromRed + (toRed - fromRed) * t);
        int green = Math.round(fromGreen + (toGreen - fromGreen) * t);
        int blue = Math.round(fromBlue + (toBlue - fromBlue) * t);
        return (red << 16) | (green << 8) | blue;
    }

    private int colorRgb() {
        if (saturation <= 0.08F && value >= 0.92F) {
            return 0x00FFFFFF;
        }
        return java.awt.Color.HSBtoRGB(hue, saturation, value) & 0x00FFFFFF;
    }

    private static RenderLayer targetLayer(Style style) {
        Style next = style == null || style.texture() == null ? Style.NORMAL : style;
        int index = next.ordinal();
        RenderLayer cached = TARGET_LAYERS[index];
        if (cached == null) {
            cached = spriteLayer("fluxvisuals_target_sprite_" + next.name().toLowerCase(Locale.ROOT), targetTexture(next));
            TARGET_LAYERS[index] = cached;
        }
        return cached;
    }

    private static RenderLayer bloomLayer() {
        if (bloomLayer == null) {
            bloomLayer = spriteLayer("fluxvisuals_target_ghost_sprite", bloomTexture());
        }
        return bloomLayer;
    }

    private static RenderLayer ghostGlowLayer() {
        if (ghostGlowLayer == null) {
            ghostGlowLayer = RenderLayer.of(
                    "fluxvisuals_target_ghost_glow",
                    1536,
                    false,
                    true,
                    GHOST_GLOW_PIPELINE,
                    RenderLayer.MultiPhaseParameters.builder()
                            .texture(new RenderPhase.Texture(bloomTexture(), false))
                            .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                            .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                            .target(RenderPhase.TRANSLUCENT_TARGET)
                            .build(false)
            );
        }
        return ghostGlowLayer;
    }

    private static RenderLayer spriteLayer(String name, Identifier texture) {
        return RenderLayer.of(
                name,
                1536,
                false,
                true,
                SPRITE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(texture, false))
                        .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                        .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                        .target(RenderPhase.TRANSLUCENT_TARGET)
                        .build(false)
        );
    }

    private static Identifier targetTexture(Style style) {
        Style next = style == null || style.texture() == null ? Style.NORMAL : style;
        int index = next.ordinal();
        Identifier cached = TARGET_MASK_TEXTURES[index];
        if (cached == null) {
            cached = maskTexture(next.texture(), next.dynamicName(), true);
            TARGET_MASK_TEXTURES[index] = cached;
        }
        return cached;
    }

    private static Identifier bloomTexture() {
        if (bloomMaskTexture == null) {
            bloomMaskTexture = maskTexture(BLOOM_SOURCE_TEXTURE, "bloom", false);
        }
        return bloomMaskTexture;
    }

    private static Identifier maskTexture(Identifier source, String name, boolean hardAlpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        try (var stream = client.getResourceManager().open(source)) {
            NativeImage image = NativeImage.read(stream);
            makeWhiteAlphaMask(image, hardAlpha);
            Identifier id = Identifier.of("fluxvisuals", "dynamic/targetesp_" + name + "_mask");
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
            texture.setFilter(!hardAlpha, false);
            texture.setClamp(true);
            client.getTextureManager().registerTexture(id, texture);
            texture.upload();
            return id;
        } catch (IOException | RuntimeException ignored) {
            return source;
        }
    }

    private static void makeWhiteAlphaMask(NativeImage image, boolean hardAlpha) {
        boolean alphaMasked = hasUsefulTransparency(image);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColorArgb(x, y);
                int alpha = color >>> 24;
                int red = (color >> 16) & 255;
                int green = (color >> 8) & 255;
                int blue = color & 255;
                int brightness = Math.max(red, Math.max(green, blue));
                int shapedAlpha = alphaMasked ? alpha : Math.round(alpha * (brightness / 255.0F));
                int flatAlpha = hardAlpha ? (shapedAlpha < 18 ? 0 : 255) : (shapedAlpha < 5 ? 0 : shapedAlpha);
                image.setColorArgb(x, y, (flatAlpha << 24) | 0x00FFFFFF);
            }
        }
    }

    private static boolean hasUsefulTransparency(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int transparentPixels = 0;
        int totalPixels = Math.max(1, width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getColorArgb(x, y) >>> 24) < 245) {
                    transparentPixels++;
                }
            }
        }
        return transparentPixels > totalPixels / 100;
    }

    private static void drawLayer(WorldRenderContext context, RenderLayer layer) {
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    private void drawGlowSprite(WorldRenderContext context, MatrixStack matrices, VertexConsumer consumer, Vec3d pos,
                                Vec3d camera, float rotation, float size, float alpha, int rgb) {
        if (alpha <= 0.004F || size <= 0.0F) {
            return;
        }
        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        matrices.multiply(context.camera().getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(rotation));
        matrices.scale(size, size, size);
        drawQuad(consumer, matrices.peek(), 1.0F, alpha, rgb);
        matrices.pop();
    }

    private static void drawTriangle(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d camera,
                                     Vec3d first, Vec3d second, Vec3d third, int color) {
        colorVertex(consumer, entry, first.subtract(camera), color);
        colorVertex(consumer, entry, second.subtract(camera), color);
        colorVertex(consumer, entry, third.subtract(camera), color);
    }

    private static void drawLine(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d camera, Vec3d from, Vec3d to, int color) {
        if ((color >>> 24) <= 0) {
            return;
        }
        Vec3d normal = normalizeSafe(to.subtract(from), new Vec3d(0.0D, 1.0D, 0.0D));
        Vec3d start = from.subtract(camera);
        Vec3d end = to.subtract(camera);
        lineVertex(consumer, entry, start, normal, color);
        lineVertex(consumer, entry, end, normal, color);
    }

    private static void colorVertex(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d pos, int color) {
        consumer.vertex(entry, (float) pos.x, (float) pos.y, (float) pos.z)
                .color((color >> 16) & 255, (color >> 8) & 255, color & 255, color >>> 24);
    }

    private static void lineVertex(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d pos, Vec3d normal, int color) {
        consumer.vertex(entry, (float) pos.x, (float) pos.y, (float) pos.z)
                .color((color >> 16) & 255, (color >> 8) & 255, color & 255, color >>> 24)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void drawQuad(VertexConsumer consumer, MatrixStack.Entry entry, float size, float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        float half = size * 0.5F;
        vertex(consumer, entry, -half, -half, 0.0F, 0.0F, 1.0F, a, light, rgb);
        vertex(consumer, entry, half, -half, 0.0F, 1.0F, 1.0F, a, light, rgb);
        vertex(consumer, entry, half, half, 0.0F, 1.0F, 0.0F, a, light, rgb);
        vertex(consumer, entry, -half, half, 0.0F, 0.0F, 0.0F, a, light, rgb);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z,
                               float u, float v, int alpha, int light, int rgb) {
        consumer.vertex(entry, x, y, z)
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0F, 1.0F, 0.0F);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float smootherstep01(float value) {
        float t = clamp01(value);
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    private static float easeOutBack(float value, float overshoot) {
        float t = clamp01(value) - 1.0F;
        float s = overshoot <= 0.0F ? 1.70158F : overshoot;
        return t * t * ((s + 1.0F) * t + s) + 1.0F;
    }

    private static int colorWithAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static int shadeArgb(int argb, float brightness) {
        int alpha = argb >>> 24;
        int red = Math.min(255, Math.round(((argb >> 16) & 255) * brightness));
        int green = Math.min(255, Math.round(((argb >> 8) & 255) * brightness));
        int blue = Math.min(255, Math.round((argb & 255) * brightness));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        Style next = style == null ? Style.NORMAL : style;
        if (this.style == next) {
            return;
        }
        this.style = next;
        ghostsInitialized = false;
        lastTargetChest = null;
        smoothedTargetMotion = Vec3d.ZERO;
        ghostStream = 0.0F;
        if (next != Style.CRYSTALS) {
            crystalTargetId = NO_TARGET;
        }
        FluxVisualsClient.requestConfigSave();
    }

    public TargetFilter getTargetFilter() {
        return targetFilter;
    }

    public void setTargetFilter(TargetFilter targetFilter) {
        TargetFilter next = targetFilter == null ? TargetFilter.PLAYERS : targetFilter;
        if (this.targetFilter == next) {
            return;
        }
        this.targetFilter = next;
        clearTarget();
        FluxVisualsClient.requestConfigSave();
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(float maxDistance) {
        float next = 2.0F + clamp01((maxDistance - 2.0F) / 30.0F) * 30.0F;
        if (Math.abs(this.maxDistance - next) < 0.001F) {
            return;
        }
        this.maxDistance = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getLostDelaySeconds() {
        return lostDelaySeconds;
    }

    public void setLostDelaySeconds(float lostDelaySeconds) {
        float next = 0.2F + clamp01((lostDelaySeconds - 0.2F) / 3.8F) * 3.8F;
        if (Math.abs(this.lostDelaySeconds - next) < 0.001F) {
            return;
        }
        this.lostDelaySeconds = next;
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

    public float getNormalSize() {
        return normalSize;
    }

    public void setNormalSize(float normalSize) {
        float next = 0.45F + clamp01((normalSize - 0.45F) / 1.55F) * 1.55F;
        if (Math.abs(this.normalSize - next) < 0.001F) {
            return;
        }
        this.normalSize = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getGhostSize() {
        return ghostSize;
    }

    public void setGhostSize(float ghostSize) {
        float next = GHOST_SIZE_MIN + clamp01((ghostSize - GHOST_SIZE_MIN) / GHOST_SIZE_RANGE) * GHOST_SIZE_RANGE;
        if (Math.abs(this.ghostSize - next) < 0.001F) {
            return;
        }
        this.ghostSize = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getGhostCount() {
        return ghostCount;
    }

    public void setGhostCount(int ghostCount) {
        int next = Math.max(1, Math.min(MAX_GHOSTS, ghostCount));
        if (this.ghostCount == next) {
            return;
        }
        this.ghostCount = next;
        ghostsInitialized = false;
        FluxVisualsClient.requestConfigSave();
    }

    public float getGhostLength() {
        return ghostLength;
    }

    public void setGhostLength(float ghostLength) {
        float next = GHOST_LENGTH_MIN + clamp01((ghostLength - GHOST_LENGTH_MIN) / GHOST_LENGTH_RANGE) * GHOST_LENGTH_RANGE;
        if (Math.abs(this.ghostLength - next) < 0.001F) {
            return;
        }
        this.ghostLength = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getGhostSpeed() {
        return ghostSpeed;
    }

    public void setGhostSpeed(float ghostSpeed) {
        float next = 0.2F + clamp01((ghostSpeed - 0.2F) / 4.8F) * 4.8F;
        if (Math.abs(this.ghostSpeed - next) < 0.001F) {
            return;
        }
        this.ghostSpeed = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getCrystalSize() {
        return crystalSize;
    }

    public void setCrystalSize(float crystalSize) {
        float next = CRYSTAL_SIZE_MIN + clamp01((crystalSize - CRYSTAL_SIZE_MIN) / CRYSTAL_SIZE_RANGE) * CRYSTAL_SIZE_RANGE;
        if (Math.abs(this.crystalSize - next) < 0.001F) {
            return;
        }
        this.crystalSize = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getCrystalCount() {
        return crystalCount;
    }

    public void setCrystalCount(int crystalCount) {
        int next = Math.max(CRYSTAL_COUNT_MIN, Math.min(CRYSTAL_COUNT_MAX, crystalCount));
        if (this.crystalCount == next) {
            return;
        }
        this.crystalCount = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getCrystalSpeed() {
        return crystalSpeed;
    }

    public void setCrystalSpeed(float crystalSpeed) {
        float next = CRYSTAL_SPEED_MIN + clamp01((crystalSpeed - CRYSTAL_SPEED_MIN) / CRYSTAL_SPEED_RANGE) * CRYSTAL_SPEED_RANGE;
        if (Math.abs(this.crystalSpeed - next) < 0.001F) {
            return;
        }
        this.crystalSpeed = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getCrystalRadius() {
        return crystalRadius;
    }

    public void setCrystalRadius(float crystalRadius) {
        float next = CRYSTAL_RADIUS_MIN + clamp01((crystalRadius - CRYSTAL_RADIUS_MIN) / CRYSTAL_RADIUS_RANGE) * CRYSTAL_RADIUS_RANGE;
        if (Math.abs(this.crystalRadius - next) < 0.001F) {
            return;
        }
        this.crystalRadius = next;
        FluxVisualsClient.requestConfigSave();
    }

    public CrystalShader getCrystalShader() {
        return crystalShader;
    }

    public void setCrystalShader(CrystalShader crystalShader) {
        this.crystalShader = crystalShader == null ? CrystalShader.NEBULA : crystalShader;
        FluxVisualsClient.requestConfigSave();
    }

    private int clampedCrystalCount() {
        return Math.max(CRYSTAL_COUNT_MIN, Math.min(CRYSTAL_COUNT_MAX, crystalCount));
    }

    public boolean isRedOnHit() {
        return redOnHit;
    }

    public void setRedOnHit(boolean redOnHit) {
        if (this.redOnHit == redOnHit) {
            return;
        }
        this.redOnHit = redOnHit;
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

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        float next = clamp01(alpha);
        if (Math.abs(this.alpha - next) < 0.001F) {
            return;
        }
        this.alpha = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getColorRgb() {
        return colorRgb();
    }

    public int getArgbColor() {
        return (Math.round(alpha * 255.0F) << 24) | (colorRgb() & 0x00FFFFFF);
    }

    public void setArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        setColor(hsb[0], hsb[1], hsb[2]);
        this.alpha = ((col >>> 24) & 0xFF) / 255.0F;
        FluxVisualsClient.requestConfigSave();
    }

    private record CrystalPose(Vec3d center, double yaw, double pitch, double roll) {
    }

    public enum Style {
        NORMAL("\u041e\u0431\u044b\u0447\u043d\u044b\u0439", "target.png", "target"),
        ROUND("\u041a\u0440\u0443\u0433\u043b\u044b\u0439", "target1.png", "target1"),
        TARGET_PRO("\u041a\u0440\u0443\u0442\u043e\u0439", "targetpro.png", "targetpro"),
        ROUNDED("\u0417\u0430\u043a\u0440\u0443\u0433\u043b\u0435\u043d\u043d\u044b\u0439", "zxcvbn.png", "zxcvbn"),
        CRYSTALS("\u041a\u0440\u0438\u0441\u0442\u0430\u043b\u043b\u044b", null, "crystals"),
        GHOSTS("\u041f\u0440\u0438\u0437\u0440\u0430\u043a\u0438", null, "ghosts");

        private final String label;
        private final Identifier texture;
        private final String dynamicName;

        Style(String label, String fileName, String dynamicName) {
            this.label = label;
            this.texture = fileName == null ? null : Identifier.of("fluxvisuals", "targetesp/" + fileName);
            this.dynamicName = dynamicName;
        }

        public String label() {
            return label;
        }

        private Identifier texture() {
            return texture;
        }

        private String dynamicName() {
            return dynamicName;
        }
    }

    public enum TargetFilter {
        PLAYERS("\u0418\u0433\u0440\u043e\u043a\u0438"),
        MOBS("\u041c\u043e\u0431\u044b"),
        ALL("\u0412\u0441\u0435");

        private final String label;

        TargetFilter(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
