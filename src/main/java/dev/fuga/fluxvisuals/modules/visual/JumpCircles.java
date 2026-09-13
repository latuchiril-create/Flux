package dev.fuga.fluxvisuals.modules.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class JumpCircles extends Module {
    private static final int MAX_CIRCLES = 24;
    private static final int MAX_PARTICLES = 512;
    private static final int MAX_BLOCK_PULSES = 256;
    private static final double JUMP_VELOCITY_THRESHOLD = 0.05D;
    private static final RenderLayer[] LAYERS = new RenderLayer[TextureType.values().length];
    private static final RenderLayer[] PARTICLE_LAYERS = new RenderLayer[Particles.TextureType.values().length];
    private static final RenderPipeline SPRITE_TEXTURE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of("fluxvisuals", "pipeline/jumpcircle_texture_sprite"))
            .withVertexShader(Identifier.of("fluxvisuals", "core/target_sprite"))
            .withFragmentShader(Identifier.of("fluxvisuals", "core/jumpcircle_sprite"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

    private final Random random = new Random();
    private final List<CircleEffect> circles = new ArrayList<>();
    private final List<BlockPulse> blockPulses = new ArrayList<>();
    private final double[] px = new double[MAX_PARTICLES];
    private final double[] py = new double[MAX_PARTICLES];
    private final double[] pz = new double[MAX_PARTICLES];
    private final double[] prevPx = new double[MAX_PARTICLES];
    private final double[] prevPy = new double[MAX_PARTICLES];
    private final double[] prevPz = new double[MAX_PARTICLES];
    private final double[] pvx = new double[MAX_PARTICLES];
    private final double[] pvy = new double[MAX_PARTICLES];
    private final double[] pvz = new double[MAX_PARTICLES];
    private final float[] pSize = new float[MAX_PARTICLES];
    private final float[] pAngle = new float[MAX_PARTICLES];
    private final float[] pAngularVelocity = new float[MAX_PARTICLES];
    private final int[] pAge = new int[MAX_PARTICLES];
    private final int[] pMaxAge = new int[MAX_PARTICLES];

    private Mode mode = Mode.NORMAL;
    private TextureType textureType = TextureType.CIRCLE;
    private Particles.TextureType particleTexture = Particles.TextureType.STAR;
    private boolean particlesEnabled = true;
    private int amount = 22;
    private float lifeSeconds = 1.15F;
    private float circleSize = 1.45F;
    private float particleSize = 1.0F;
    private float spread = 0.55F;
    private float hue = 0.58F;
    private float saturation = 0.78F;
    private float value = 1.0F;
    private float circleAlpha = 1.0F;
    private boolean wasOnGround;
    private boolean wasJumpKeyDown;
    private int jumpCooldown;
    private int particleCount;

    public JumpCircles() {
        super("JumpCircles", "Draws jump rings, particles, and block pulses.", ModuleCategory.VISUALS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.world == null) {
            clear();
            wasOnGround = false;
            return;
        }

        tickParticles();
        tickCircles();
        tickBlockPulses();
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }

        boolean onGround = client.player.isOnGround();
        boolean jumpKeyDown = client.options.jumpKey.isPressed();
        boolean jumpedOffGround = wasOnGround && !onGround && jumpCooldown <= 0
                && (client.player.getVelocity().y > JUMP_VELOCITY_THRESHOLD || jumpKeyDown);
        boolean crampedJump = onGround && jumpKeyDown && !wasJumpKeyDown && jumpCooldown <= 0;
        if (jumpedOffGround || crampedJump) {
            Vec3d pos = client.player.getPos();
            Vec3d ground = groundPos(client.world, pos);
            spawnJump(client.world, ground);
            jumpCooldown = 6;
        }
        wasOnGround = onGround;
        wasJumpKeyDown = jumpKeyDown;
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        clear();
    }

    public void render(WorldRenderContext context) {
        if (!isEnabled() || context.matrixStack() == null || context.consumers() == null || context.camera() == null) {
            return;
        }

        float tickDelta = context.tickCounter().getTickProgress(false);
        Vec3d camera = context.camera().getPos();
        int rgb = colorRgb();
        renderCircles(context, camera, tickDelta, rgb);
        renderBlockPulses(context, camera, tickDelta, rgb);
        renderParticles(context, camera, tickDelta, rgb);
    }

    private void spawnJump(World world, Vec3d ground) {
        if (mode == Mode.BLOCKS) {
            spawnBlockWave(world, ground);
        } else {
            circles.add(new CircleEffect(ground, mode, textureType));
            while (circles.size() > MAX_CIRCLES) {
                circles.remove(0);
            }
        }

        if (particlesEnabled) {
            spawnParticles(ground);
        }
    }

    private void renderCircles(WorldRenderContext context, Vec3d camera, float tickDelta, int rgb) {
        MatrixStack matrices = context.matrixStack();
        for (TextureType type : TextureType.values()) {
            VertexConsumer consumer = null;
            for (CircleEffect circle : circles) {
                if (circle.texture != type) {
                    continue;
                }

                float progress = (circle.age + tickDelta) / Math.max(1.0F, circle.maxAge);
                float alpha = circleAlpha(progress) * circleAlpha;
                if (alpha <= 0.01F) {
                    continue;
                }

                boolean fluxTexture = circle.texture == TextureType.FLUX;
                float pulse = fluxTexture
                        ? 1.0F + 0.05F * (float) Math.sin(progress * Math.PI * 6.0D)
                        : 1.0F;
                float growth = fluxTexture
                        ? 0.48F + progress * 0.48F
                        : 0.62F + progress * 0.72F;
                float textureScale = fluxTexture ? 0.78F : 1.0F;
                float drawSize = circleSize * pulse * growth * textureScale;
                if (consumer == null) {
                    consumer = context.consumers().getBuffer(layer(type));
                }

                matrices.push();
                matrices.translate(circle.pos.x - camera.x, circle.pos.y - camera.y + 0.025D, circle.pos.z - camera.z);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotation(circle.rotation + progress * 0.45F));
                matrices.scale(drawSize, drawSize, drawSize);
                float drawAlpha = alpha;
                drawQuad(consumer, matrices.peek(), drawAlpha, rgb);
                matrices.pop();
            }
        }
    }

    private void renderParticles(WorldRenderContext context, Vec3d camera, float tickDelta, int rgb) {
        if (particleCount <= 0) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        VertexConsumer consumer = context.consumers().getBuffer(particleLayer(particleTexture));
        for (int i = 0; i < particleCount; i++) {
            float progress = (pAge[i] + tickDelta) / Math.max(1.0F, pMaxAge[i]);
            float alpha = (1.0F - cubicIn(progress)) * circleAlpha;
            if (alpha <= 0.01F) {
                continue;
            }

            double x = lerp(prevPx[i], px[i], tickDelta) - camera.x;
            double y = lerp(prevPy[i], py[i], tickDelta) - camera.y;
            double z = lerp(prevPz[i], pz[i], tickDelta) - camera.z;
            float drawSize = pSize[i] * (0.72F + alpha * 0.36F);

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(context.camera().getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(pAngle[i] + pAngularVelocity[i] * tickDelta));
            matrices.scale(drawSize, drawSize, drawSize);
            drawQuad(consumer, matrices.peek(), alpha, rgb);
            matrices.pop();
        }
    }

    private void renderBlockPulses(WorldRenderContext context, Vec3d camera, float tickDelta, int rgb) {
        if (blockPulses.isEmpty()) {
            return;
        }

        RenderLayer layer = RenderLayer.getDebugFilledBox();
        VertexConsumer consumer = context.consumers().getBuffer(layer);
        MatrixStack matrices = context.matrixStack();
        for (BlockPulse pulse : blockPulses) {
            float localAge = pulse.age + tickDelta - pulse.delay;
            if (localAge < 0.0F) {
                continue;
            }
            float progress = localAge / Math.max(1.0F, pulse.maxAge);
            if (progress >= 1.0F) {
                continue;
            }

            float alpha = (float) Math.sin(progress * Math.PI) * 0.23F * circleAlpha;
            float expand = 0.018F + 0.055F * (float) Math.sin(progress * Math.PI * 2.0D) * (1.0F - progress);
            Box box = new Box(pulse.pos).expand(expand).offset(-camera.x, -camera.y, -camera.z);
            drawFilledBox(matrices, consumer, box, rgb, alpha);
        }
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    private void spawnParticles(Vec3d center) {
        int count = Math.max(1, amount);
        for (int i = 0; i < count; i++) {
            if (particleCount >= MAX_PARTICLES) {
                removeParticle(0);
            }
            int index = particleCount++;
            double angle = Math.PI * 2.0D * (i / (double) count) + random.nextDouble() * 0.25D;
            double speed = (0.045D + random.nextDouble() * 0.055D) * (0.25D + spread * 1.55D);
            double radius = 0.18D + random.nextDouble() * 0.18D;
            px[index] = center.x + Math.cos(angle) * radius;
            py[index] = center.y + 0.10D + random.nextDouble() * 0.10D;
            pz[index] = center.z + Math.sin(angle) * radius;
            prevPx[index] = px[index];
            prevPy[index] = py[index];
            prevPz[index] = pz[index];
            pvx[index] = Math.cos(angle) * speed;
            pvy[index] = 0.025D + random.nextDouble() * 0.055D;
            pvz[index] = Math.sin(angle) * speed;
            pSize[index] = particleSize * (0.13F + random.nextFloat() * 0.07F);
            pAngle[index] = random.nextFloat() * (float) Math.PI * 2.0F;
            pAngularVelocity[index] = (random.nextFloat() - 0.5F) * 0.18F;
            pAge[index] = 0;
            pMaxAge[index] = Math.max(6, Math.round(lifeSeconds * 20.0F));
        }
    }

    private void spawnBlockWave(World world, Vec3d center) {
        BlockPos origin = BlockPos.ofFloored(center);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 4.25D) {
                    continue;
                }

                BlockPos selected = null;
                for (int dy = 0; dy >= -4; dy--) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir() && !state.getCollisionShape(world, pos).isEmpty()) {
                        selected = pos.toImmutable();
                        break;
                    }
                }
                if (selected == null) {
                    continue;
                }

                blockPulses.add(new BlockPulse(selected, (int) Math.round(distance * 5.0D)));
                while (blockPulses.size() > MAX_BLOCK_PULSES) {
                    blockPulses.remove(0);
                }
            }
        }
    }

    private void tickCircles() {
        Iterator<CircleEffect> iterator = circles.iterator();
        while (iterator.hasNext()) {
            CircleEffect effect = iterator.next();
            effect.age++;
            if (effect.age >= effect.maxAge) {
                iterator.remove();
            }
        }
    }

    private void tickBlockPulses() {
        Iterator<BlockPulse> iterator = blockPulses.iterator();
        while (iterator.hasNext()) {
            BlockPulse pulse = iterator.next();
            pulse.age++;
            if (pulse.age - pulse.delay >= pulse.maxAge) {
                iterator.remove();
            }
        }
    }

    private void tickParticles() {
        for (int i = particleCount - 1; i >= 0; i--) {
            pAge[i]++;
            if (pAge[i] >= pMaxAge[i]) {
                removeParticle(i);
                continue;
            }
            prevPx[i] = px[i];
            prevPy[i] = py[i];
            prevPz[i] = pz[i];
            pvy[i] -= 0.006D;
            pvx[i] *= 0.94D;
            pvy[i] *= 0.94D;
            pvz[i] *= 0.94D;
            px[i] += pvx[i];
            py[i] += pvy[i];
            pz[i] += pvz[i];
            pAngle[i] += pAngularVelocity[i];
        }
    }

    private void removeParticle(int index) {
        int last = particleCount - 1;
        if (index != last) {
            px[index] = px[last];
            py[index] = py[last];
            pz[index] = pz[last];
            prevPx[index] = prevPx[last];
            prevPy[index] = prevPy[last];
            prevPz[index] = prevPz[last];
            pvx[index] = pvx[last];
            pvy[index] = pvy[last];
            pvz[index] = pvz[last];
            pSize[index] = pSize[last];
            pAngle[index] = pAngle[last];
            pAngularVelocity[index] = pAngularVelocity[last];
            pAge[index] = pAge[last];
            pMaxAge[index] = pMaxAge[last];
        }
        particleCount = Math.max(0, particleCount - 1);
    }

    private void clear() {
        circles.clear();
        blockPulses.clear();
        particleCount = 0;
    }

    private static Vec3d groundPos(World world, Vec3d pos) {
        BlockPos start = BlockPos.ofFloored(pos.x, pos.y + 0.2D, pos.z);
        for (int i = 0; i < 6; i++) {
            BlockPos candidate = start.down(i);
            BlockState state = world.getBlockState(candidate);
            if (!state.isAir() && !state.getCollisionShape(world, candidate).isEmpty()) {
                return new Vec3d(pos.x, candidate.getY() + 1.01D, pos.z);
            }
        }
        return new Vec3d(pos.x, pos.y + 0.01D, pos.z);
    }

    private int colorRgb() {
        if (saturation <= 0.08F && value >= 0.92F) {
            return 0x00FFFFFF;
        }
        return java.awt.Color.HSBtoRGB(hue, saturation, value) & 0x00FFFFFF;
    }

    private static RenderLayer layer(TextureType textureType) {
        int index = textureType.ordinal();
        RenderLayer cached = LAYERS[index];
        if (cached != null) {
            return cached;
        }
        cached = RenderLayer.of(
                "fluxvisuals_jumpcircle_sprite_" + textureType.name().toLowerCase(Locale.ROOT),
                1536,
                false,
                true,
                SPRITE_TEXTURE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(texture(textureType), false))
                        .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                        .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                        .target(RenderPhase.TRANSLUCENT_TARGET)
                        .build(false)
        );
        LAYERS[index] = cached;
        return cached;
    }

    private static RenderLayer particleLayer(Particles.TextureType textureType) {
        int index = textureType.ordinal();
        RenderLayer cached = PARTICLE_LAYERS[index];
        if (cached != null) {
            return cached;
        }
        cached = RenderLayer.of(
                "fluxvisuals_jumpcircle_particle_sprite_" + textureType.name().toLowerCase(Locale.ROOT),
                1536,
                false,
                true,
                SPRITE_TEXTURE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(particleTexture(textureType), false))
                        .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                        .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                        .target(RenderPhase.TRANSLUCENT_TARGET)
                        .build(false)
        );
        PARTICLE_LAYERS[index] = cached;
        return cached;
    }

    private static Identifier particleTexture(Particles.TextureType textureType) {
        return textureType.resourceId();
    }

    private static Identifier texture(TextureType textureType) {
        return textureType.resourceId();
    }

    private static void drawQuad(VertexConsumer consumer, MatrixStack.Entry entry, float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        vertex(consumer, entry, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, a, light, rgb);
        vertex(consumer, entry, 0.5F, -0.5F, 0.0F, 1.0F, 1.0F, a, light, rgb);
        vertex(consumer, entry, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F, a, light, rgb);
        vertex(consumer, entry, -0.5F, 0.5F, 0.0F, 0.0F, 0.0F, a, light, rgb);
    }

    private static void drawFilledBox(MatrixStack matrices, VertexConsumer consumer, Box box, int rgb, float alpha) {
        float r = ((rgb >> 16) & 255) / 255.0F;
        float g = ((rgb >> 8) & 255) / 255.0F;
        float b = (rgb & 255) / 255.0F;
        float a = Math.max(0.0F, Math.min(1.0F, alpha));
        VertexRendering.drawFilledBox(matrices, consumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
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

    private static float circleAlpha(float progress) {
        float clamped = clamp01(progress);
        if (clamped < 0.16F) {
            return clamped / 0.16F;
        }
        return 1.0F - cubicIn((clamped - 0.16F) / 0.84F);
    }

    private static double lerp(double from, double to, float delta) {
        return from + (to - from) * delta;
    }

    private static float cubicIn(float value) {
        float clamped = clamp01(value);
        return clamped * clamped * clamped;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        Mode next = mode == null ? Mode.NORMAL : mode;
        if (next == Mode.FLUX_BUSTIT) {
            next = Mode.NORMAL;
            textureType = TextureType.FLUX;
        }
        if (this.mode == next) {
            return;
        }
        this.mode = next;
        circles.clear();
        blockPulses.clear();
        FluxVisualsClient.requestConfigSave();
    }

    public TextureType getTextureType() {
        return textureType;
    }

    public void setTextureType(TextureType textureType) {
        TextureType next = textureType == null ? TextureType.CIRCLE : textureType;
        if (this.textureType == next) {
            return;
        }
        this.textureType = next;
        circles.clear();
        FluxVisualsClient.requestConfigSave();
    }

    public Particles.TextureType getParticleTexture() {
        return particleTexture;
    }

    public void setParticleTexture(Particles.TextureType particleTexture) {
        Particles.TextureType next = particleTexture == null ? Particles.TextureType.STAR : particleTexture;
        if (this.particleTexture == next) {
            return;
        }
        this.particleTexture = next;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public void setParticlesEnabled(boolean particlesEnabled) {
        if (this.particlesEnabled == particlesEnabled) {
            return;
        }
        this.particlesEnabled = particlesEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        int next = Math.max(1, Math.min(96, amount));
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
        float next = Math.max(0.25F, Math.min(4.0F, lifeSeconds));
        if (Math.abs(this.lifeSeconds - next) < 0.001F) {
            return;
        }
        this.lifeSeconds = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getSize() {
        return circleSize;
    }

    public void setSize(float size) {
        float next = Math.max(0.35F, Math.min(3.0F, size));
        if (Math.abs(this.circleSize - next) < 0.001F) {
            return;
        }
        this.circleSize = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getParticleSize() {
        return particleSize;
    }

    public void setParticleSize(float particleSize) {
        float next = Math.max(0.25F, Math.min(2.5F, particleSize));
        if (Math.abs(this.particleSize - next) < 0.001F) {
            return;
        }
        this.particleSize = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getSpread() {
        return spread;
    }

    public void setSpread(float spread) {
        float next = clamp01(spread);
        if (Math.abs(this.spread - next) < 0.001F) {
            return;
        }
        this.spread = next;
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

    public float getCircleAlpha() {
        return circleAlpha;
    }

    public void setCircleAlpha(float alpha) {
        float next = clamp01(alpha);
        if (Math.abs(this.circleAlpha - next) < 0.001F) {
            return;
        }
        this.circleAlpha = next;
        FluxVisualsClient.requestConfigSave();
    }

    public int getColorRgb() {
        return colorRgb();
    }

    public int getArgbColor() {
        return (Math.round(circleAlpha * 255.0F) << 24) | (colorRgb() & 0x00FFFFFF);
    }

    public void setArgbColor(int col) {
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        setColor(hsb[0], hsb[1], hsb[2]);
        this.circleAlpha = ((col >>> 24) & 0xFF) / 255.0F;
        FluxVisualsClient.requestConfigSave();
    }

    public enum Mode {
        NORMAL("\u041e\u0431\u044b\u0447\u043d\u044b\u0439"),
        FLUX_BUSTIT("FluxBustit"),
        BLOCKS("\u0411\u043b\u043e\u043a\u0438");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum TextureType {
        CIRCLE("\u041a\u0440\u0443\u0433", "jump"),
        RING("\u041a\u043e\u043b\u044c\u0446\u043e", "ring", "jump"),
        FLUX("Flux", "fluxbustit");

        private final String label;
        private final Identifier id;
        private final Identifier fallbackId;

        TextureType(String label, String path) {
            this(label, path, path);
        }

        TextureType(String label, String path, String fallback) {
            this.label = label;
            this.id = Identifier.of("fluxvisuals", "jumpcircles/" + path + ".png");
            this.fallbackId = Identifier.of("fluxvisuals", "jumpcircles/" + fallback + ".png");
        }

        public String label() {
            return label;
        }

        private Identifier id() {
            return id;
        }

        private Identifier resourceId() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getResourceManager().getResource(id).isPresent()) {
                return id;
            }
            return fallbackId;
        }
    }

    private static final class CircleEffect {
        private final Vec3d pos;
        private final Mode mode;
        private final TextureType texture;
        private final float rotation;
        private final int maxAge;
        private int age;

        private CircleEffect(Vec3d pos, Mode mode, TextureType texture) {
            this.pos = pos;
            this.mode = mode;
            this.texture = texture == null ? TextureType.CIRCLE : texture;
            this.rotation = (float) (Math.random() * Math.PI * 2.0D);
            this.maxAge = this.texture == TextureType.FLUX ? 34 : 28;
        }
    }

    private static final class BlockPulse {
        private final BlockPos pos;
        private final int delay;
        private final int maxAge = 14;
        private int age;

        private BlockPulse(BlockPos pos, int delay) {
            this.pos = pos;
            this.delay = delay;
        }
    }
}
