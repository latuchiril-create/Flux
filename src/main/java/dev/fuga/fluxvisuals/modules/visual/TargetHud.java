package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import dev.fuga.fluxvisuals.render.liqvid.BlurRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public final class TargetHud extends Module {
    private static final RenderPipeline LIQUID_GLASS_PIPELINE = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.of("fluxvisuals", "pipeline/targethud_liquidglass"))
            .withVertexShader(Identifier.of("rockstar", "core/liquidglass/vertex"))
            .withFragmentShader(Identifier.of("rockstar", "core/liquidglass/fragment"))
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .build());
    private static final int AA_SAMPLES = 4;
    private static final float MIN_WIDTH = 100.0F;
    private static final float MAX_NAME_WIDTH = 150.0F;
    /** Dynamic textures must never grow unbounded (HP text changes every hit):
     *  leaked GL textures starve texture memory and item icons start to glitch. */
    private static final int MAX_DYNAMIC_TEXTURES = 96;
    private static final Map<RoundKey, Identifier> ROUND_TEXTURES = lruTextureMap();
    private static final Map<MaskKey, Identifier> MASK_TEXTURES = lruTextureMap();
    private static final Map<HeadKey, Identifier> HEAD_TEXTURES = lruTextureMap();
    private static final Map<TextKey, TextTexture> TEXT_TEXTURES = new java.util.LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextKey, TextTexture> eldest) {
            if (size() > MAX_DYNAMIC_TEXTURES) {
                destroyTexture(eldest.getValue().id());
                return true;
            }
            return false;
        }
    };

    private static <K> Map<K, Identifier> lruTextureMap() {
        return new java.util.LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Identifier> eldest) {
                if (size() > MAX_DYNAMIC_TEXTURES) {
                    destroyTexture(eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }

    private static void destroyTexture(Identifier id) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null && id != null) {
                client.getTextureManager().destroyTexture(id);
            }
        } catch (Exception ignored) {
        }
    }
    private static final Map<FontRole, Font> FONTS = new EnumMap<>(FontRole.class);
    private static SimpleFramebuffer liquidGlassSnapshot;
    private final Map<Integer, Float> lastHealth = new HashMap<>();
    private final Map<Integer, Integer> flashTicks = new HashMap<>();

    private float x = 20.0F;
    private float y = 80.0F;
    private float scale = 1.0F;
    private boolean redOnDamage = true;
    private boolean hitParticles = true;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private float lastW = 170.0F;
    private float lastH = 42.0F;

    public TargetHud() {
        super("TargetHud", "Minimal target HUD with drag and hp bar.", ModuleCategory.HUD);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.world == null) {
            return;
        }
        LivingEntity target = currentTarget(client);
        if (target != null) {
            int id = target.getId();
            float hp = shownHealth(client, target);
            Float prev = lastHealth.put(id, hp);
            if (prev != null && hp < prev - 0.01F) {
                flashTicks.put(id, 10);
            }
        }
        flashTicks.replaceAll((k, v) -> Math.max(0, v - 1));
        flashTicks.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled() || context == null || client == null || client.player == null) {
            return;
        }
        if (client.currentScreen != null
                && !(client.currentScreen instanceof ChatScreen)
                && !(client.currentScreen instanceof InventoryScreen)) {
            return;
        }
        LivingEntity target = currentTarget(client);
        boolean chat = client.currentScreen instanceof ChatScreen;
        if (target == null && !chat) {
            return;
        }

        String name = target == null ? "No target" : FluxVisualsClient.MODULE_MANAGER.getNameProtect()
                .protect(target.getName().getString());
        float hp = target == null ? 0.0F : shownHealth(client, target);
        float maxHp = target == null ? 20.0F : Math.max(Math.max(1.0F, target.getMaxHealth()), hp);
        float hpPct = Math.max(0.0F, Math.min(1.0F, hp / maxHp));
        int namePixelSize = Math.max(9, Math.round(10.0F * scale));
        int hpPixelSize = Math.max(8, Math.round(8.0F * scale));
        String hpText = "HP: " + formatHp(hp);
        String shownName = trimToWidth(name, namePixelSize, FontRole.DISPLAY, MAX_NAME_WIDTH * scale);
        float textWidth = Math.max(
                measureTextWidth(shownName, namePixelSize, FontRole.DISPLAY),
                measureTextWidth(hpText, hpPixelSize, FontRole.BODY)
        );
        float contentWidth = Math.max(48.0F * scale, Math.min(MAX_NAME_WIDTH * scale, textWidth));

        float w = Math.max(MIN_WIDTH * scale, 44.0F * scale + contentWidth + 9.0F * scale);
        float h = 42.0F * scale;
        lastW = w;
        lastH = h;

        // Use the installed Minecraft post-effect chain from AGENTS.md.  The
        // vanilla blur id is overridden by fluxvisuals:post/* resources, so
        // this is the actual Gaussian/composite shader rather than the old
        // liquid-glass prototype pipeline.
        // Render the blurred framebuffer through a rounded local mask. Do not
        // call DrawContext.applyBlur(): that API processes minecraft:main and
        // therefore blurs the entire screen (and can also be called only once
        // per frame). BlurRenderer composites only this TargetHUD rectangle.
        BlurRenderer.drawBlur(x, y, w, h, 10.0F * scale, 0.78F);
        rounded(context, x + 1.0F * scale, y + 1.0F * scale,
                Math.max(1.0F, w - 2.0F * scale), Math.max(1.0F, h - 2.0F * scale),
                10.0F * scale, 0x24070A0F);
        outline(context, x, y, w, h, 11.0F * scale, 0x525C7185);

        int headX = Math.round(x + 4.0F * scale);
        int headY = Math.round(y + 4.0F * scale);
        int headSize = Math.max(8, Math.round(34.0F * scale));
        if (target instanceof AbstractClientPlayerEntity player) {
            Identifier roundedHead = roundedHeadTexture(player, headSize);
            if (roundedHead != null) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, roundedHead, headX, headY, 0.0F, 0.0F, headSize, headSize,
                        headSize, headSize, headSize, headSize, 0xFFFFFFFF);
            } else {
                var skin = player.getSkinTextures().texture();
                context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 8.0F, 8.0F, headSize, headSize, 8, 8, 64, 64);
                context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 40.0F, 8.0F, headSize, headSize, 8, 8, 64, 64);
                maskRoundedHead(context, headX, headY, headSize, Math.max(2, Math.round(headSize * 0.24F)));
            }
        } else {
            int mobColor = target == null ? 0xFF2C2C2C : 0xFF55585F;
            rounded(context, headX, headY, headSize, headSize, 8.0F * scale, mobColor);
        }

        boolean flash = target != null && redOnDamage && flashTicks.getOrDefault(target.getId(), 0) > 0;
        if (flash) {
            rounded(context, headX, headY, headSize, headSize, 8.0F * scale, 0x66FF3B3B);
        }

        int nameColor = 0xFFEFEFEF;
        drawText(context, shownName, x + 44.0F * scale, y + 5.0F * scale, namePixelSize, nameColor, FontRole.DISPLAY);
        drawText(context, hpText, x + 44.0F * scale, y + 18.0F * scale, hpPixelSize, 0xFFB8BDC8, FontRole.BODY);

        int barX = Math.round(x + 44.0F * scale);
        int barY = Math.round(y + 30.0F * scale);
        int barW = Math.max(30, Math.round(contentWidth));
        float barH = Math.max(3.0F, 4.0F * scale);
        rounded(context, barX, barY, barW, barH, 2.0F * scale, 0x66222224);
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * hpPct)));
        if (fillW > 0) {
            // БЫЛО: до ~150 вызовов rounded() по 1px в КАДР (просадка GPU
            // прямо во время боя, когда смотришь на хотбар). Стало: один вызов.
            dev.fuga.fluxvisuals.render.Render2D.drawGradientRoundLR(context,
                    barX, barY, fillW, barH, Math.min(2.0F * scale, barH * 0.5F),
                    0xFFFF0000, 0xFF00FF00);
        }
    }

    private static void drawLiquidGlassBackdrop(DrawContext context, float x, float y, float w, float h) {
        // Use the complete liqvidshader pipeline: cached Gaussian background,
        // refraction, inner blur and perimeter lighting.
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachment() == null) {
            rounded(context, x, y, w, h, 11.0F, 0xB51A2029);
            return;
        }
        int width = main.textureWidth;
        int height = main.textureHeight;
        if (liquidGlassSnapshot == null
                || liquidGlassSnapshot.textureWidth != width
                || liquidGlassSnapshot.textureHeight != height) {
            if (liquidGlassSnapshot != null) {
                liquidGlassSnapshot.delete();
            }
            liquidGlassSnapshot = new SimpleFramebuffer("FluxVisuals TargetHud liquid glass", width, height, false);
        }
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                main.getColorAttachment(), liquidGlassSnapshot.getColorAttachment(),
                0, 0, 0, 0, 0, width, height);
        context.fill(LIQUID_GLASS_PIPELINE, TextureSetup.of(liquidGlassSnapshot.getColorAttachmentView()),
                Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
    }

    public boolean handleMouse(MinecraftClient client, double mouseX, double mouseY, int button, int action) {
        if (!isEnabled() || client == null || !(client.currentScreen instanceof ChatScreen)) {
            dragging = false;
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        float scaledX = (float) (mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        float scaledY = (float) (mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight());
        if (action == GLFW.GLFW_RELEASE) {
            dragging = false;
            return false;
        }
        if (action == GLFW.GLFW_PRESS && inside(scaledX, scaledY, x, y, lastW, lastH)) {
            dragging = true;
            dragOffsetX = scaledX - x;
            dragOffsetY = scaledY - y;
            return true;
        }
        return false;
    }

    public void handleMouseMove(MinecraftClient client, double mouseX, double mouseY) {
        if (!dragging || client == null || !(client.currentScreen instanceof ChatScreen)) {
            return;
        }
        float scaledX = (float) (mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        float scaledY = (float) (mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight());
        setPosition(scaledX - dragOffsetX, scaledY - dragOffsetY);
    }

    public boolean shouldSpawnHitParticles() {
        return hitParticles;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        float next = Math.max(0.75F, Math.min(1.8F, scale));
        if (Math.abs(this.scale - next) < 0.001F) {
            return;
        }
        this.scale = next;
        FluxVisualsClient.requestConfigSave();
    }

    public void setPosition(float x, float y) {
        float nextX = Math.max(0.0F, x);
        float nextY = Math.max(0.0F, y);
        if (Math.abs(this.x - nextX) < 0.001F && Math.abs(this.y - nextY) < 0.001F) {
            return;
        }
        this.x = nextX;
        this.y = nextY;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isRedOnDamage() {
        return redOnDamage;
    }

    public void setRedOnDamage(boolean redOnDamage) {
        if (this.redOnDamage == redOnDamage) {
            return;
        }
        this.redOnDamage = redOnDamage;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isHitParticles() {
        return hitParticles;
    }

    public void setHitParticles(boolean hitParticles) {
        if (this.hitParticles == hitParticles) {
            return;
        }
        this.hitParticles = hitParticles;
        FluxVisualsClient.requestConfigSave();
    }

    private static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && py >= y && px <= x + w && py <= y + h;
    }

    private static LivingEntity currentTarget(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit) || entityHit.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        Entity entity = entityHit.getEntity();
        if (!(entity instanceof LivingEntity living) || entity.isRemoved()) {
            return null;
        }
        return living;
    }

    private static float shownHealth(MinecraftClient client, LivingEntity entity) {
        Integer score = scoreboardHealth(client, entity);
        if (score != null && score > 0) {
            return score;
        }
        return (float) Math.ceil(Math.max(0.0F, entity.getHealth() + entity.getAbsorptionAmount()));
    }

    private static Integer scoreboardHealth(MinecraftClient client, LivingEntity entity) {
        if (!(entity instanceof PlayerEntity player) || client == null || client.world == null) {
            return null;
        }
        Scoreboard scoreboard = client.world.getScoreboard();
        Integer belowName = scoreboardValue(scoreboard, player, ScoreboardDisplaySlot.BELOW_NAME);
        if (belowName != null) {
            return belowName;
        }
        return scoreboardValue(scoreboard, player, ScoreboardDisplaySlot.LIST);
    }

    private static Integer scoreboardValue(Scoreboard scoreboard, PlayerEntity player, ScoreboardDisplaySlot slot) {
        if (scoreboard == null || player == null || slot == null) {
            return null;
        }
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(slot);
        if (objective == null) {
            return null;
        }
        ReadableScoreboardScore score = scoreboard.getScore(player, objective);
        return score == null ? null : score.getScore();
    }

    private static String formatHp(float value) {
        int rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.001F) {
            return Integer.toString(rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String trimToWidth(String value, int pixelSize, FontRole role, float maxWidth) {
        String safe = value == null ? "" : value.trim();
        if (safe.isEmpty() || measureTextWidth(safe, pixelSize, role) <= maxWidth) {
            return safe;
        }
        String suffix = "...";
        if (measureTextWidth(suffix, pixelSize, role) > maxWidth) {
            return "";
        }
        String trimmed = safe;
        while (trimmed.length() > 1 && measureTextWidth(trimmed + suffix, pixelSize, role) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + suffix;
    }

    private static float measureTextWidth(String value, int pixelSize, FontRole role) {
        return textTexture(value, pixelSize, role).width;
    }

    private static void drawText(DrawContext context, String value, float x, float y, int pixelSize, int color, FontRole role) {
        TextTexture text = textTexture(value, pixelSize, role);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, text.id, Math.round(x), Math.round(y), 0.0F, 0.0F,
                text.width, text.height, text.textureWidth, text.textureHeight, text.textureWidth, text.textureHeight, color);
    }

    private static TextTexture textTexture(String value, int pixelSize, FontRole role) {
        String safe = value == null ? "" : value;
        TextKey key = new TextKey(safe, pixelSize, role);
        TextTexture cached = TEXT_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        int renderSize = Math.max(8, pixelSize * 2);
        Font font = font(role);
        if (font.canDisplayUpTo(safe) != -1) {
            font = new Font(role.fallbackFamily, role.style, renderSize);
        } else {
            font = font.deriveFont(role.style, (float) renderSize);
        }
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = measure.createGraphics();
        measureGraphics.setFont(font);
        FontMetrics metrics = measureGraphics.getFontMetrics();
        int width = Math.max(1, metrics.stringWidth(safe) + 6);
        int height = Math.max(1, metrics.getHeight() + 6);
        measureGraphics.dispose();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        FontMetrics finalMetrics = graphics.getFontMetrics();
        graphics.drawString(safe, 3, 3 + finalMetrics.getAscent());
        graphics.dispose();

        NativeImage nativeImage = new NativeImage(width, height, false);
        for (int iy = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++) {
                nativeImage.setColorArgb(ix, iy, image.getRGB(ix, iy));
            }
        }
        Identifier id = Identifier.of("fluxvisuals", "dynamic/targethud_text_" + Integer.toHexString(key.hashCode()));
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
        texture.setFilter(true, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        TextTexture created = new TextTexture(id, width / 2, height / 2, width, height);
        TEXT_TEXTURES.put(key, created);
        return created;
    }

    private static Font font(FontRole role) {
        Font cached = FONTS.get(role);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = TargetHud.class.getClassLoader().getResourceAsStream("assets/fluxvisuals/" + role.path)) {
            cached = stream == null ? new Font(role.fallbackFamily, role.style, 16) : Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (FontFormatException | IOException | RuntimeException ignored) {
            cached = new Font(role.fallbackFamily, role.style, 16);
        }
        FONTS.put(role, cached);
        return cached;
    }

    private static void rounded(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        Identifier texture = roundedTexture(Math.max(1, Math.round(w)), Math.max(1, Math.round(h)), Math.max(0, Math.round(radius)), false);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), 0.0F, 0.0F,
                Math.round(w), Math.round(h), Math.round(w), Math.round(h), Math.round(w), Math.round(h), color);
    }

    private static void outline(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        Identifier texture = roundedTexture(Math.max(1, Math.round(w)), Math.max(1, Math.round(h)), Math.max(0, Math.round(radius)), true);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), 0.0F, 0.0F,
                Math.round(w), Math.round(h), Math.round(w), Math.round(h), Math.round(w), Math.round(h), color);
    }

    private static Identifier roundedTexture(int width, int height, int radius, boolean outline) {
        RoundKey key = new RoundKey(width, height, Math.min(radius, Math.min(width, height) / 2), outline);
        Identifier cached = ROUND_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        NativeImage image = new NativeImage(width, height, false);
        for (int iy = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++) {
                float coverage = roundCoverage(width, height, key.radius, outline ? 1 : 0, ix, iy);
                int alpha = Math.max(0, Math.min(255, Math.round(coverage * 255.0F)));
                image.setColorArgb(ix, iy, (alpha << 24) | 0x00FFFFFF);
            }
        }
        Identifier id = Identifier.of("fluxvisuals", "dynamic/targethud_round_" + width + "_" + height + "_" + key.radius + "_" + outline);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        texture.setFilter(true, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        ROUND_TEXTURES.put(key, id);
        return id;
    }

    private static void maskRoundedHead(DrawContext context, int x, int y, int size, int radius) {
        Identifier texture = inverseRoundedTexture(size, size, radius);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                size, size, size, size, size, size, 0xFF0B0B0E);
    }

    private static Identifier inverseRoundedTexture(int width, int height, int radius) {
        MaskKey key = new MaskKey(width, height, Math.min(radius, Math.min(width, height) / 2));
        Identifier cached = MASK_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        NativeImage image = new NativeImage(width, height, false);
        for (int iy = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++) {
                float coverage = 1.0F - roundCoverage(width, height, key.radius, 0, ix, iy);
                int alpha = Math.max(0, Math.min(255, Math.round(coverage * 255.0F)));
                image.setColorArgb(ix, iy, (alpha << 24) | 0x00FFFFFF);
            }
        }
        Identifier id = Identifier.of("fluxvisuals", "dynamic/targethud_head_mask_" + width + "_" + height + "_" + key.radius);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        texture.setFilter(true, false);
        texture.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        texture.upload();
        MASK_TEXTURES.put(key, id);
        return id;
    }

    private static boolean insideRound(int width, int height, int radius, float x, float y) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (radius <= 0) {
            return true;
        }
        float cx = Math.max(radius, Math.min(width - radius, x));
        float cy = Math.max(radius, Math.min(height - radius, y));
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static float roundCoverage(int width, int height, int radius, int outline, int x, int y) {
        int hits = 0;
        int total = AA_SAMPLES * AA_SAMPLES;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                float px = x + (sx + 0.5F) / AA_SAMPLES;
                float py = y + (sy + 0.5F) / AA_SAMPLES;
                boolean outer = insideRound(width, height, radius, px, py);
                boolean inner = outline <= 0 || !insideRound(width - outline * 2, height - outline * 2,
                        Math.max(0, radius - outline), px - outline, py - outline);
                if (outer && inner) {
                    hits++;
                }
            }
        }
        return hits / (float) total;
    }

    private static Identifier roundedHeadTexture(AbstractClientPlayerEntity player, int size) {
        if (player == null || size <= 0) {
            return null;
        }
        Identifier skinId = player.getSkinTextures().texture();
        HeadKey key = new HeadKey(skinId, size);
        Identifier cached = HEAD_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        try (InputStream stream = client.getResourceManager().open(skinId)) {
            NativeImage skin = NativeImage.read(stream);
            NativeImage head = new NativeImage(size, size, false);
            int radius = Math.max(2, Math.round(size * 0.24F));
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float u = 8.0F + (x + 0.5F) * 8.0F / size;
                    float v = 8.0F + (y + 0.5F) * 8.0F / size;
                    int base = sampleNearest(skin, u, v);
                    int hat = sampleNearest(skin, 32.0F + u, v);
                    int color = blendOver(base, hat);
                    float coverage = roundCoverage(size, size, radius, 0, x, y);
                    int alpha = Math.round(((color >>> 24) & 255) * coverage);
                    head.setColorArgb(x, y, (alpha << 24) | (color & 0x00FFFFFF));
                }
            }
            Identifier id = Identifier.of("fluxvisuals", "dynamic/targethud_head_" + Integer.toHexString(skinId.hashCode()) + "_" + size);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), head);
            texture.setFilter(true, false);
            texture.setClamp(true);
            client.getTextureManager().registerTexture(id, texture);
            texture.upload();
            HEAD_TEXTURES.put(key, id);
            return id;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static int sampleNearest(NativeImage image, float u, float v) {
        int x = Math.max(0, Math.min(image.getWidth() - 1, (int) u));
        int y = Math.max(0, Math.min(image.getHeight() - 1, (int) v));
        return image.getColorArgb(x, y);
    }

    private static int blendOver(int baseArgb, int topArgb) {
        int ba = (baseArgb >>> 24) & 255;
        int br = (baseArgb >> 16) & 255;
        int bg = (baseArgb >> 8) & 255;
        int bb = baseArgb & 255;
        int ta = (topArgb >>> 24) & 255;
        int tr = (topArgb >> 16) & 255;
        int tg = (topArgb >> 8) & 255;
        int tb = topArgb & 255;
        float a = ta / 255.0F;
        int r = Math.round(br * (1.0F - a) + tr * a);
        int g = Math.round(bg * (1.0F - a) + tg * a);
        int b = Math.round(bb * (1.0F - a) + tb * a);
        int outA = Math.max(ba, ta);
        return (outA << 24) | (r << 16) | (g << 8) | b;
    }

    private record RoundKey(int width, int height, int radius, boolean outline) {
    }

    private record MaskKey(int width, int height, int radius) {
    }

    private record HeadKey(Identifier skin, int size) {
    }

    private record TextKey(String value, int pixelSize, FontRole role) {
    }

    private record TextTexture(Identifier id, int width, int height, int textureWidth, int textureHeight) {
    }

    private enum FontRole {
        DISPLAY("font/sf-pro-display-semibold.otf", Font.PLAIN, "SansSerif"),
        BODY("font/inter_18pt-medium.ttf", Font.PLAIN, "SansSerif");

        private final String path;
        private final int style;
        private final String fallbackFamily;

        FontRole(String path, int style, String fallbackFamily) {
            this.path = path;
            this.style = style;
            this.fallbackFamily = fallbackFamily;
        }
    }
}
