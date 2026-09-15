package dev.fuga.fluxvisuals.modules.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.EnumSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class Particles extends Module {
    private static final int HARD_CAP = 768;
    private static final int WORLD_SOFT_CAP_MULTIPLIER = 5;
    private static final double RENDER_DISTANCE_SQ = 28.0D * 28.0D;
    private static final double SIMULATION_DISTANCE_SQ = 36.0D * 36.0D;
    private static final double WORLD_RANGE_XZ = 16.0D;
    private static final double WORLD_RANGE_Y = 15.0D;
    private static final double COLLISION_RADIUS = 0.08D;
    private static final float GRAVITY = 0.018F;
    private static final float DRAG = 0.982F;
    private static final float WORLD_DRIFT_DRAG = 0.935F;
    private static final float BOUNCE = 0.70F;
    private static final RenderLayer[] MAIN_LAYERS = new RenderLayer[TextureType.values().length];
    private static final TextureType[] TEXTURE_TYPES = TextureType.values();
    private static final SpawnMode[] SPAWN_MODES = SpawnMode.values();

    private static final RenderPipeline PARTICLE_SPRITE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/particle_shape_sprite"))
            .withVertexShader(Identifier.of("fluxvisuals", "core/target_sprite"))
            .withFragmentShader(Identifier.of("fluxvisuals", "core/particle_sprite"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

    private static final RenderPipeline PARTICLE_GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/particle_glow"))
            .withVertexShader(Identifier.of("fluxvisuals", "core/target_crystal_glow"))
            .withFragmentShader(Identifier.of("fluxvisuals", "core/target_crystal_glow"))
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE))
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());
    private static final RenderLayer PARTICLE_GLOW_LAYER = RenderLayer.of(
            "fluxvisuals_particle_glow", 32768, false, false, PARTICLE_GLOW_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                    .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                    .target(RenderPhase.TRANSLUCENT_TARGET)
                    .build(false));

    // Reused frame data: visibility, interpolation and size are computed once.
    private final int[] frameHead = new int[TEXTURE_TYPES.length];
    private final int[] frameNext = new int[HARD_CAP];
    private final float[] frameX = new float[HARD_CAP];
    private final float[] frameY = new float[HARD_CAP];
    private final float[] frameZ = new float[HARD_CAP];
    private final float[] frameSize = new float[HARD_CAP];
    private final float[] frameAlpha = new float[HARD_CAP];
    private final Random random = new Random();
    private final EnumSet<SpawnMode> spawnModes = EnumSet.of(SpawnMode.IN_WORLD);
    private final BlockPos.Mutable collisionPos = new BlockPos.Mutable();

    private final double[] x = new double[HARD_CAP];
    private final double[] y = new double[HARD_CAP];
    private final double[] z = new double[HARD_CAP];
    private final double[] prevX = new double[HARD_CAP];
    private final double[] prevY = new double[HARD_CAP];
    private final double[] prevZ = new double[HARD_CAP];
    private final double[] velocityX = new double[HARD_CAP];
    private final double[] velocityY = new double[HARD_CAP];
    private final double[] velocityZ = new double[HARD_CAP];
    private final float[] scale = new float[HARD_CAP];
    private final float[] gravityScale = new float[HARD_CAP];
    private final float[] angle = new float[HARD_CAP];
    private final float[] angularVelocity = new float[HARD_CAP];
    private final int[] age = new int[HARD_CAP];
    private final int[] maxAge = new int[HARD_CAP];
    private final byte[] texture = new byte[HARD_CAP];
    private final byte[] mode = new byte[HARD_CAP];
    private final byte[] collisionHits = new byte[HARD_CAP];
    private final boolean[] burst = new boolean[HARD_CAP];

    private TextureType previewTexture = TextureType.STAR;
    private int activeCount;
    private float worldSpawnDebt;
    private float runSpawnDebt;
    private int amount = 32;
    private float lifeSeconds = 2.4F;
    private float size = 0.18F;
    private float hue = 0.0F;
    private float saturation = 0.0F;
    private float value = 1.0F;
    private float particleAlpha = 1.0F;
    private boolean outlineEnabled = true;

    public Particles() {
        super("Particles", "Optimized textured particles with optional outline.", ModuleCategory.VISUALS);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        clear();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.world == null) {
            clear();
            return;
        }

        World world = client.world;
        Vec3d playerPos = client.player.getPos();
        for (int i = activeCount - 1; i >= 0; i--) {
            age[i]++;
            if (age[i] >= maxAge[i] || (!burst[i] && distanceSquared(i, playerPos) > SIMULATION_DISTANCE_SQ)) {
                removeAt(i);
                continue;
            }

            prevX[i] = x[i];
            prevY[i] = y[i];
            prevZ[i] = z[i];
            angle[i] += angularVelocity[i];

            SpawnMode spawnMode = SPAWN_MODES[mode[i]];
            if (spawnMode == SpawnMode.IN_WORLD && !burst[i]) {
                velocityX[i] *= WORLD_DRIFT_DRAG;
                velocityY[i] *= WORLD_DRIFT_DRAG;
                velocityZ[i] *= WORLD_DRIFT_DRAG;
                x[i] += velocityX[i];
                y[i] += velocityY[i];
                z[i] += velocityZ[i];
            } else {
                velocityY[i] -= GRAVITY * gravityScale[i];
                velocityX[i] *= DRAG;
                velocityY[i] *= DRAG;
                velocityZ[i] *= DRAG;
                moveWithCollision(world, i);
            }
        }

        spawnAmbient(client);
        spawnRunning(client);
        trimToBudget();
    }

    public void render(WorldRenderContext context) {
        if (!isEnabled() || activeCount == 0 || context.matrixStack() == null || context.consumers() == null
                || context.camera() == null) {
            return;
        }

        float tickDelta = context.tickCounter().getTickProgress(false);
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        int rgb = colorRgb();
        Arrays.fill(frameHead, -1);
        for (int i = 0; i < activeCount; i++) {
            double distanceSq = distanceSquaredTo(i, camera);
            if (distanceSq > RENDER_DISTANCE_SQ) continue;
            float progress = (age[i] + tickDelta) / Math.max(1.0F, maxAge[i]);
            float opacity = alpha(i, progress) * particleAlpha;
            if (opacity <= 0.01F) continue;
            float distanceFade = burst[i] ? 1.0F : (float) Math.max(0.62D, 1.0D - distanceSq / (RENDER_DISTANCE_SQ * 1.35D));
            float drawSize = size * scale[i] * distanceFade;
            if (SPAWN_MODES[mode[i]] == SpawnMode.IN_WORLD && !burst[i]) {
                drawSize *= 0.72F + opacity * 0.58F;
            }
            frameX[i] = (float) (lerp(prevX[i], x[i], tickDelta) - camera.x);
            frameY[i] = (float) (lerp(prevY[i], y[i], tickDelta) - camera.y);
            frameZ[i] = (float) (lerp(prevZ[i], z[i], tickDelta) - camera.z);
            frameSize[i] = drawSize;
            frameAlpha[i] = opacity;
            int type = texture[i] & 255;
            frameNext[i] = frameHead[type];
            frameHead[type] = i;
        }
        if (outlineEnabled) {
            VertexConsumer glow = null;
            for (int head : frameHead) {
                for (int i = head; i >= 0; i = frameNext[i]) {
                    if (glow == null) glow = context.consumers().getBuffer(PARTICLE_GLOW_LAYER);
                    drawFrameParticle(context, matrices, glow, i, tickDelta, rgb, true);
                }
            }
            if (glow != null) flushLayer(context, PARTICLE_GLOW_LAYER);
        }
        for (TextureType type : TEXTURE_TYPES) {
            int head = frameHead[type.ordinal()];
            if (head < 0) continue;
            RenderLayer layer = mainLayer(type);
            VertexConsumer consumer = context.consumers().getBuffer(layer);
            for (int i = head; i >= 0; i = frameNext[i]) {
                drawFrameParticle(context, matrices, consumer, i, tickDelta, rgb, false);
            }
            flushLayer(context, layer);
        }
    }

    private void drawFrameParticle(WorldRenderContext context, MatrixStack matrices, VertexConsumer consumer,
                                   int i, float tickDelta, int rgb, boolean glow) {
        matrices.push();
        matrices.translate(frameX[i], frameY[i], frameZ[i]);
        matrices.multiply(context.camera().getRotation());
        if (!glow) matrices.multiply(RotationAxis.POSITIVE_Z.rotation(angle[i] + angularVelocity[i] * tickDelta));
        float extent = frameSize[i] * (glow ? 2.4F : 1.0F);
        matrices.scale(extent, extent, extent);
        drawQuad(consumer, matrices.peek(), frameAlpha[i] * (glow ? 0.48F : 1.0F), rgb);
        matrices.pop();
    }

    private static void flushLayer(WorldRenderContext context, RenderLayer layer) {
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }
    public TextureType getPreviewTexture() {
        return previewTexture;
    }

    public void setPreviewTexture(TextureType previewTexture) {
        TextureType next = previewTexture == null ? TextureType.STAR : previewTexture;
        if (this.previewTexture == next) {
            return;
        }
        this.previewTexture = next;
        byte textureId = (byte) next.ordinal();
        for (int i = 0; i < activeCount; i++) {
            texture[i] = textureId;
        }
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isModeEnabled(SpawnMode mode) {
        return mode != null && spawnModes.contains(mode);
    }

    public void setModeEnabled(SpawnMode mode, boolean enabled) {
        if (mode == null) {
            return;
        }
        if (enabled) {
            spawnModes.add(mode);
        } else if (spawnModes.size() > 1) {
            spawnModes.remove(mode);
        }
        FluxVisualsClient.requestConfigSave();
    }

    public String enabledModeNames() {
        StringBuilder builder = new StringBuilder();
        for (SpawnMode spawnMode : SpawnMode.values()) {
            if (spawnModes.contains(spawnMode)) {
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(spawnMode.name());
            }
        }
        return builder.toString();
    }

    public void setEnabledModeNames(String names) {
        EnumSet<SpawnMode> next = EnumSet.noneOf(SpawnMode.class);
        if (names != null) {
            for (String raw : names.split(",")) {
                try {
                    next.add(SpawnMode.valueOf(raw.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Ignore old config values.
                }
            }
        }
        if (next.isEmpty()) {
            next.add(SpawnMode.IN_WORLD);
        }
        spawnModes.clear();
        spawnModes.addAll(next);
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        int next = Math.max(1, Math.min(120, amount));
        if (this.amount == next) {
            return;
        }
        this.amount = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getLifeSeconds() {
        return lifeSeconds;
    }

    public void setLifeSeconds(float lifeSeconds) {
        float next = Math.max(0.4F, Math.min(6.0F, lifeSeconds));
        if (this.lifeSeconds == next) {
            return;
        }
        this.lifeSeconds = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        float next = Math.max(0.04F, Math.min(0.55F, size));
        if (this.size == next) {
            return;
        }
        this.size = next;
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

    public float getParticleAlpha() {
        return particleAlpha;
    }

    public void setParticleAlpha(float alpha) {
        float next = clamp01(alpha);
        if (Math.abs(this.particleAlpha - next) < 0.001F) {
            return;
        }
        this.particleAlpha = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getColorRgb() {
        return colorRgb();
    }

    public int getArgbColor() {
        return (Math.round(particleAlpha * 255.0F) << 24) | (colorRgb() & 0x00FFFFFF);
    }

    public void setArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        setColor(hsb[0], hsb[1], hsb[2]);
        this.particleAlpha = ((col >>> 24) & 0xFF) / 255.0F;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isOutlineEnabled() {
        return outlineEnabled;
    }

    public void setOutlineEnabled(boolean outlineEnabled) {
        if (this.outlineEnabled == outlineEnabled) {
            return;
        }
        this.outlineEnabled = outlineEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public void setColor(float hue, float saturation, float value) {
        float nextHue = clamp01(hue);
        float nextSaturation = clamp01(saturation);
        float nextValue = clamp01(value);
        if (nextSaturation < 0.08F) {
            nextSaturation = 0.0F;
        }
        if (nextValue > 0.96F) {
            nextValue = 1.0F;
        }
        if (this.hue == nextHue && this.saturation == nextSaturation && this.value == nextValue) {
            return;
        }
        this.hue = nextHue;
        this.saturation = nextSaturation;
        this.value = nextValue;
        FluxVisualsClient.requestConfigSave();
    }

    public void spawnCritBurst(Entity target) {
        spawnCritBurst(target, target == null ? null : target.getPos().add(0.0D, target.getHeight() * 0.55D, 0.0D));
    }

    public void spawnCritBurst(Entity target, Vec3d hitPos) {
        if (!isEnabled() || !spawnModes.contains(SpawnMode.ON_CRIT) || target == null || !isCriticalHit(MinecraftClient.getInstance())) {
            return;
        }
        Vec3d center = hitPos == null ? target.getPos().add(0.0D, target.getHeight() * 0.55D, 0.0D) : hitPos;
        int count = Math.min(140, Math.max(10, amount));
        for (int i = 0; i < count; i++) {
            spawnBurst(center, SpawnMode.ON_CRIT, 0.08D + random.nextDouble() * 0.12D, 0.72F);
        }
        trimHard();
    }

    public void spawnTotemBurst(Entity target) {
        spawnTotemBurst(target, false);
    }

    public void spawnForcedTotemBurst(Entity target) {
        spawnTotemBurst(target, true);
    }

    public void spawnFakeHitBurst(Entity target, Vec3d hitPos) {
        if (!isEnabled() || target == null) {
            return;
        }
        Vec3d center = hitPos == null ? target.getPos().add(0.0D, target.getHeight() * 0.55D, 0.0D) : hitPos;
        int count = Math.min(72, Math.max(10, amount / 2));
        for (int i = 0; i < count; i++) {
            spawnBurst(center, SpawnMode.ON_CRIT, 0.07D + random.nextDouble() * 0.10D, 0.65F);
        }
        trimHard();
    }

    private void spawnTotemBurst(Entity target, boolean force) {
        if (!isEnabled() || (!force && !spawnModes.contains(SpawnMode.ON_TOTEM_POP)) || target == null) {
            return;
        }
        Vec3d center = target.getPos().add(0.0D, target.getHeight() * 0.6D, 0.0D);
        int count = Math.min(160, Math.max(amount + 18, amount * 2));
        for (int i = 0; i < count; i++) {
            spawnBurst(center, SpawnMode.ON_TOTEM_POP, 0.13D + random.nextDouble() * 0.23D, 0.52F);
        }
        trimHard();
    }

    private void spawnAmbient(MinecraftClient client) {
        int target = spawnModes.contains(SpawnMode.IN_WORLD) ? worldCap() : 0;
        if (target <= 0) {
            worldSpawnDebt = 0.0F;
            return;
        }

        float lifetimeTicks = Math.max(8.0F, lifeSeconds * 20.0F);
        worldSpawnDebt += Math.max(0.12F, target / lifetimeTicks / 3.0F);
        int groups = Math.min(10, (int) worldSpawnDebt);
        worldSpawnDebt -= groups;
        for (int i = 0; i < groups; i++) {
            spawnWorldGroup(client);
        }
    }

    private void spawnRunning(MinecraftClient client) {
        if (!shouldSpawnRunning(client)) {
            runSpawnDebt = 0.0F;
            return;
        }

        int target = Math.max(6, amount * 2);
        runSpawnDebt += Math.max(0.25F, target / Math.max(10.0F, lifeSeconds * 20.0F));
        int count = Math.min(client.player.isGliding() ? 10 : 8, (int) runSpawnDebt);
        runSpawnDebt -= count;
        for (int i = 0; i < count; i++) {
            spawnRunParticle(client);
        }
    }

    private void spawnWorldGroup(MinecraftClient client) {
        Vec3d player = client.player.getPos();
        double spawnX = player.x + (random.nextDouble() - 0.5D) * WORLD_RANGE_XZ * 2.0D;
        double spawnY = player.y + 0.25D + random.nextDouble() * WORLD_RANGE_Y;
        double spawnZ = player.z + (random.nextDouble() - 0.5D) * WORLD_RANGE_XZ * 2.0D;
        Vec3d spawn = findOpenSpawn(client.world, spawnX, spawnY, spawnZ);
        if (spawn == null) {
            return;
        }

        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double speed = 0.035D + random.nextDouble() * 0.055D;
            int index = add(spawn.x, spawn.y, spawn.z, previewTexture, SpawnMode.IN_WORLD, false);
            if (index < 0) {
                return;
            }
            velocityX[index] = Math.cos(angle) * speed;
            velocityY[index] = (random.nextDouble() - 0.5D) * 0.025D;
            velocityZ[index] = Math.sin(angle) * speed;
            gravityScale[index] = 0.0F;
            scale[index] = 0.7F + random.nextFloat() * 0.72F;
            maxAge[index] = Math.max(8, Math.round(lifeSeconds * 20.0F));
        }
    }

    private void spawnRunParticle(MinecraftClient client) {
        Vec3d movement = client.player.getVelocity();
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        double yaw = Math.toRadians(client.player.getYaw());
        double forwardX = horizontal > 1.0E-4D ? movement.x / horizontal : -Math.sin(yaw);
        double forwardZ = horizontal > 1.0E-4D ? movement.z / horizontal : Math.cos(yaw);
        double sideX = -forwardZ;
        double sideZ = forwardX;
        double backX = -forwardX;
        double backZ = -forwardZ;
        boolean gliding = client.player.isGliding();
        boolean swimming = client.player.isSwimming();
        boolean compactPose = gliding || swimming;
        double side = (random.nextDouble() - 0.5D) * (compactPose ? 0.22D : 0.38D);
        double backDistance = compactPose
                ? (gliding ? 0.62D : 0.46D)
                : 0.42D + random.nextDouble() * 0.38D;
        double heightOffset = compactPose ? 0.10D + random.nextDouble() * 0.18D : 0.48D + random.nextDouble() * 0.58D;
        Vec3d base = client.player.getPos().add(
                sideX * side + backX * backDistance,
                heightOffset,
                sideZ * side + backZ * backDistance
        );
        Vec3d spawn = findOpenSpawn(client.world, base.x, base.y, base.z);
        if (spawn == null) {
            return;
        }

        int index = add(spawn.x, spawn.y, spawn.z, previewTexture, SpawnMode.WHILE_RUNNING, false);
        if (index < 0) {
            return;
        }
        double drift = compactPose ? 0.025D + random.nextDouble() * 0.045D : 0.075D + random.nextDouble() * 0.11D;
        double sideVelocity = (random.nextDouble() - 0.5D) * (compactPose ? 0.055D : 0.14D);
        double carry = gliding ? 0.90D : (swimming ? 0.42D : 0.13D);
        velocityX[index] = backX * drift + sideX * sideVelocity + movement.x * carry;
        velocityY[index] = (gliding ? -0.012D : (swimming ? 0.002D : 0.022D))
                + (random.nextDouble() - 0.5D) * (compactPose ? 0.030D : 0.09D);
        velocityZ[index] = backZ * drift + sideZ * sideVelocity + movement.z * carry;
        gravityScale[index] = gliding ? 0.22F : (swimming ? 0.10F : 1.12F);
        scale[index] = compactPose ? 0.36F + random.nextFloat() * 0.30F : 0.75F + random.nextFloat() * 0.65F;
        maxAge[index] = Math.max(8, Math.round(lifeSeconds * 20.0F));
    }

    private void spawnBurst(Vec3d center, SpawnMode spawnMode, double speed, float gravity) {
        Vec3d offset = randomUnitVector().multiply(0.04D + random.nextDouble() * 0.34D);
        Vec3d direction = randomUnitVector();
        int index = add(center.x + offset.x, center.y + offset.y, center.z + offset.z, previewTexture, spawnMode, true);
        if (index < 0) {
            return;
        }
        double velocity = speed * (0.75D + random.nextDouble() * 0.9D);
        velocityX[index] = direction.x * velocity;
        velocityY[index] = direction.y * velocity + (spawnMode == SpawnMode.ON_TOTEM_POP ? 0.025D : 0.0D);
        velocityZ[index] = direction.z * velocity;
        gravityScale[index] = gravity;
        scale[index] = 0.85F + random.nextFloat() * (spawnMode == SpawnMode.ON_TOTEM_POP ? 0.95F : 0.75F);
        maxAge[index] = Math.max(8, Math.round(lifeSeconds * 20.0F * (spawnMode == SpawnMode.ON_TOTEM_POP ? 1.15F : 1.0F)));
    }

    private int add(double spawnX, double spawnY, double spawnZ, TextureType textureType, SpawnMode spawnMode, boolean burstParticle) {
        if (activeCount >= HARD_CAP && !removeOldestAmbient()) {
            removeAt(0);
        }
        if (activeCount >= HARD_CAP) {
            return -1;
        }

        int index = activeCount++;
        x[index] = spawnX;
        y[index] = spawnY;
        z[index] = spawnZ;
        prevX[index] = spawnX;
        prevY[index] = spawnY;
        prevZ[index] = spawnZ;
        velocityX[index] = 0.0D;
        velocityY[index] = 0.0D;
        velocityZ[index] = 0.0D;
        age[index] = 0;
        maxAge[index] = Math.max(8, Math.round(lifeSeconds * 20.0F));
        scale[index] = 1.0F;
        gravityScale[index] = 1.0F;
        angle[index] = random.nextFloat() * (float) Math.PI * 2.0F;
        angularVelocity[index] = (random.nextFloat() - 0.5F) * 0.08F;
        texture[index] = (byte) textureType.ordinal();
        mode[index] = (byte) spawnMode.ordinal();
        collisionHits[index] = 0;
        burst[index] = burstParticle;
        return index;
    }

    private void moveWithCollision(World world, int index) {
        double maxVelocity = Math.max(Math.abs(velocityX[index]), Math.max(Math.abs(velocityY[index]), Math.abs(velocityZ[index])));
        int steps = Math.max(1, (int) Math.ceil(maxVelocity / 0.09D));
        double step = 1.0D / steps;
        for (int i = 0; i < steps; i++) {
            moveAxis(world, index, step, 0);
            moveAxis(world, index, step, 1);
            moveAxis(world, index, step, 2);
        }
    }

    private void moveAxis(World world, int index, double step, int axis) {
        double nextX = x[index] + (axis == 0 ? velocityX[index] * step : 0.0D);
        double nextY = y[index] + (axis == 1 ? velocityY[index] * step : 0.0D);
        double nextZ = z[index] + (axis == 2 ? velocityZ[index] * step : 0.0D);
        if (collides(world, nextX, nextY, nextZ)) {
            collisionHits[index] = (byte) Math.min(127, (collisionHits[index] & 255) + 1);
            if (axis == 0) {
                velocityX[index] = -velocityX[index] * BOUNCE;
            } else if (axis == 1) {
                velocityY[index] = -velocityY[index] * BOUNCE;
                velocityX[index] *= 0.88D;
                velocityZ[index] *= 0.88D;
            } else {
                velocityZ[index] = -velocityZ[index] * BOUNCE;
            }
            return;
        }
        x[index] = nextX;
        y[index] = nextY;
        z[index] = nextZ;
    }

    private boolean collides(World world, double px, double py, double pz) {
        return collidesPoint(world, px, py, pz)
                || collidesPoint(world, px + COLLISION_RADIUS, py, pz)
                || collidesPoint(world, px - COLLISION_RADIUS, py, pz)
                || collidesPoint(world, px, py + COLLISION_RADIUS, pz)
                || collidesPoint(world, px, py - COLLISION_RADIUS, pz)
                || collidesPoint(world, px, py, pz + COLLISION_RADIUS)
                || collidesPoint(world, px, py, pz - COLLISION_RADIUS);
    }

    private boolean collidesPoint(World world, double px, double py, double pz) {
        collisionPos.set(px, py, pz);
        BlockState state = world.getBlockState(collisionPos);
        return !state.isAir() && !state.getCollisionShape(world, collisionPos).isEmpty();
    }

    private Vec3d findOpenSpawn(World world, double px, double py, double pz) {
        for (int i = 0; i < 8; i++) {
            double candidateY = py + i * 0.32D;
            if (!collides(world, px, candidateY, pz)) {
                return new Vec3d(px, candidateY, pz);
            }
        }
        return null;
    }

    private void trimToBudget() {
        int ambientCap = Math.max(amount + 18, worldCap() + (shouldKeepRunningCap() ? amount * 2 : 0));
        while (ambientCount() > Math.min(HARD_CAP, ambientCap) && removeOldestAmbient()) {
            // Compact excessive ambient particles without touching burst particles.
        }
        trimHard();
    }

    private void trimHard() {
        while (activeCount > HARD_CAP) {
            removeAt(0);
        }
    }

    private int worldCap() {
        return Math.min(HARD_CAP, Math.max(24, amount * WORLD_SOFT_CAP_MULTIPLIER));
    }

    private boolean shouldKeepRunningCap() {
        return spawnModes.contains(SpawnMode.WHILE_RUNNING);
    }

    private int ambientCount() {
        int count = 0;
        for (int i = 0; i < activeCount; i++) {
            if (!burst[i]) {
                count++;
            }
        }
        return count;
    }

    private boolean removeOldestAmbient() {
        int oldest = -1;
        int oldestAge = -1;
        for (int i = 0; i < activeCount; i++) {
            if (!burst[i] && age[i] > oldestAge) {
                oldest = i;
                oldestAge = age[i];
            }
        }
        if (oldest < 0) {
            return false;
        }
        removeAt(oldest);
        return true;
    }

    private void removeAt(int index) {
        int last = activeCount - 1;
        if (index != last) {
            x[index] = x[last];
            y[index] = y[last];
            z[index] = z[last];
            prevX[index] = prevX[last];
            prevY[index] = prevY[last];
            prevZ[index] = prevZ[last];
            velocityX[index] = velocityX[last];
            velocityY[index] = velocityY[last];
            velocityZ[index] = velocityZ[last];
            scale[index] = scale[last];
            gravityScale[index] = gravityScale[last];
            angle[index] = angle[last];
            angularVelocity[index] = angularVelocity[last];
            age[index] = age[last];
            maxAge[index] = maxAge[last];
            texture[index] = texture[last];
            mode[index] = mode[last];
            collisionHits[index] = collisionHits[last];
            burst[index] = burst[last];
        }
        activeCount = Math.max(0, activeCount - 1);
    }

    private void clear() {
        activeCount = 0;
        worldSpawnDebt = 0.0F;
        runSpawnDebt = 0.0F;
    }

    private boolean shouldSpawnRunning(MinecraftClient client) {
        if (!spawnModes.contains(SpawnMode.WHILE_RUNNING) || client.player == null) {
            return false;
        }
        if (client.player.isGliding()) {
            return true;
        }
        Vec3d velocity = client.player.getVelocity();
        return client.player.isSprinting() || client.player.isTouchingWater() || velocity.horizontalLengthSquared() > 0.0036D;
    }

    private float alpha(int index, float progress) {
        float clamped = clamp01(progress);
        if (SpawnMode.values()[mode[index]] == SpawnMode.IN_WORLD && !burst[index]) {
            if (clamped < 0.28F) {
                return cubicOut(clamped / 0.28F);
            }
            if (clamped > 0.72F) {
                return 1.0F - cubicIn((clamped - 0.72F) / 0.28F);
            }
            return 1.0F;
        }
        return 1.0F - cubicIn(clamped);
    }

    private double distanceSquared(int index, Vec3d pos) {
        return distanceSquaredTo(index, pos);
    }

    private double distanceSquaredTo(int index, Vec3d pos) {
        double dx = x[index] - pos.x;
        double dy = y[index] - pos.y;
        double dz = z[index] - pos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private Vec3d randomUnitVector() {
        double z = -1.0D + random.nextDouble() * 2.0D;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
        return new Vec3d(Math.cos(angle) * radius, z, Math.sin(angle) * radius);
    }

    private int colorRgb() {
        if (saturation <= 0.08F && value >= 0.96F) {
            return 0x00FFFFFF;
        }
        return java.awt.Color.HSBtoRGB(hue, saturation, value) & 0x00FFFFFF;
    }

    private static RenderLayer mainLayer(TextureType textureType) {
        int index = textureType.ordinal();
        RenderLayer cached = MAIN_LAYERS[index];
        if (cached != null) {
            return cached;
        }
        Identifier texture = particleTexture(textureType);
        cached = RenderLayer.of(
                "fluxvisuals_particle_shape_sprite_" + textureType.name().toLowerCase(Locale.ROOT),
                1536,
                false,
                true,
                PARTICLE_SPRITE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(texture, false))
                        .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                        .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                        .target(RenderPhase.TRANSLUCENT_TARGET)
                        .build(false)
        );
        MAIN_LAYERS[index] = cached;
        return cached;
    }

    private static Identifier particleTexture(TextureType textureType) {
        return textureType.resourceId();
    }

    private static boolean isCriticalHit(MinecraftClient client) {
        return client != null && client.player != null
                && client.player.fallDistance > 0.0F
                && !client.player.isOnGround()
                && !client.player.isTouchingWater()
                && client.player.getAttackCooldownProgress(0.5F) > 0.84F;
    }

    private static void drawQuad(VertexConsumer consumer, MatrixStack.Entry entry, float alpha, int rgb) {
        int a = Math.round(255.0F * clamp01(alpha));
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        vertex(consumer, entry, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, a, light, rgb);
        vertex(consumer, entry, 0.5F, -0.5F, 0.0F, 1.0F, 1.0F, a, light, rgb);
        vertex(consumer, entry, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F, a, light, rgb);
        vertex(consumer, entry, -0.5F, 0.5F, 0.0F, 0.0F, 0.0F, a, light, rgb);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float px, float py, float pz, float u, float v, int alpha, int light, int rgb) {
        consumer.vertex(entry, px, py, pz)
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0F, 1.0F, 0.0F);
    }

    private static double lerp(double from, double to, float delta) {
        return from + (to - from) * delta;
    }

    private static float cubicOut(float value) {
        float inverse = 1.0F - clamp01(value);
        return 1.0F - inverse * inverse * inverse;
    }

    private static float cubicIn(float value) {
        float clamped = clamp01(value);
        return clamped * clamped * clamped;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public enum TextureType {
        STAR("Звезда"),
        SPARKLE("Искры"),
        SNOWFLAKE("Снежинка"),
        HEART("Сердце"),
        GLOW("Свечение"),
        DOLLAR("Доллар");

        private final String label;
        private final Identifier id;

        TextureType(String label) {
            this.label = label;
            this.id = Identifier.of("fluxvisuals", "particles/" + name().toLowerCase(Locale.ROOT) + ".png");
        }

        public String label() {
            return label;
        }

        public Identifier resourceId() {
            return id;
        }

        public Identifier id() {
            return id;
        }
    }

    public enum SpawnMode {
        WHILE_RUNNING("\u041f\u0440\u0438 \u0431\u0435\u0433\u0435"),
        IN_WORLD("\u0412 \u043c\u0438\u0440\u0435"),
        ON_CRIT("\u041f\u0440\u0438 \u043a\u0440\u0438\u0442\u0435"),
        ON_TOTEM_POP("\u041f\u0440\u0438 \u0441\u043d\u043e\u0441\u0435 \u0442\u043e\u0442\u0435\u043c\u0430");

        private final String label;

        SpawnMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
