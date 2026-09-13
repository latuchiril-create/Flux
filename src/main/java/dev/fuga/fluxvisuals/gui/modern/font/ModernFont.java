package dev.fuga.fluxvisuals.gui.modern.font;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import dev.fuga.fluxvisuals.render.Render2D;

public final class ModernFont {
    private static final int OVERSAMPLE = 4;
    private static final Map<String, Font> LOADED_FONTS = new HashMap<>();
    private static final Map<TextKey, TextTexture> TEXTURES = new HashMap<>();
    private static final Map<Integer, TextTexture> LOGO_TEXTURES = new HashMap<>();
    private static final Map<BadgeKey, TextTexture> BADGE_TEXTURES = new HashMap<>();
    private static final Map<String, TextTexture> TOOLTIP_TEXTURES = new HashMap<>();
    private static final Map<String, TextTexture> HUE_TEXTURES = new HashMap<>();
    private static Identifier HUE_SPECTRUM_ID = null;

    public enum Type {
        SF_BOLD("font/sf-pro-display-semibold.otf", Font.BOLD, "SansSerif"),
        SF_MEDIUM("font/sfprodisplaymedium.ttf", Font.PLAIN, "SansSerif"),
        INTER_SEMIBOLD("font/inter_18pt-semibold.ttf", Font.BOLD, "SansSerif"),
        INTER_MEDIUM("font/inter_18pt-medium.ttf", Font.PLAIN, "SansSerif"),
        OXANIUM("font/oxanium-semibold.ttf", Font.BOLD, "SansSerif");

        public final String path;
        public final int style;
        public final String fallback;

        Type(String path, int style, String fallback) {
            this.path = path;
            this.style = style;
            this.fallback = fallback;
        }
    }

    private record TextKey(String text, int size, Type type) {}
    private record BadgeKey(String text, int bgArgb, int textArgb, int height) {}
    private record TextTexture(Identifier id, float width, float height, float quadW, float quadH, float padX, float padY) {
        public TextTexture(Identifier id, float width, float height) {
            this(id, width, height, width, height, 0.0F, 0.0F);
        }
    }

    private ModernFont() {}

    private static Font getFont(Type type) {
        Font font = LOADED_FONTS.get(type.path);
        if (font != null) {
            return font;
        }
        try (InputStream stream = ModernFont.class.getResourceAsStream("/assets/fluxvisuals/" + type.path)) {
            if (stream != null) {
                font = Font.createFont(Font.TRUETYPE_FONT, stream);
            } else {
                font = new Font(type.fallback, type.style, 16);
            }
        } catch (Exception e) {
            font = new Font(type.fallback, type.style, 16);
        }
        LOADED_FONTS.put(type.path, font);
        return font;
    }

    private static TextTexture getTexture(String text, int pixelSize, Type type) {
        TextKey key = new TextKey(text, pixelSize, type);
        TextTexture cached = TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        int renderSize = pixelSize * OVERSAMPLE;
        Font baseFont = getFont(type);
        Font font;
        if (baseFont.canDisplayUpTo(text) != -1) {
            font = new Font(type.fallback, type.style, renderSize);
        } else {
            font = baseFont.deriveFont(type.style, (float) renderSize);
        }

        BufferedImage measureImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gMeasure = measureImg.createGraphics();
        gMeasure.setFont(font);
        gMeasure.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gMeasure.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics fm = gMeasure.getFontMetrics();

        int padX = 4 * OVERSAMPLE;
        int padY = 2 * OVERSAMPLE;
        int advance = fm.stringWidth(text);
        int ascent = fm.getAscent();
        int descent = fm.getDescent();
        int highW = advance + padX * 2;
        int highH = ascent + descent + padY * 2;
        gMeasure.dispose();

        BufferedImage highImg = new BufferedImage(highW, highH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = highImg.createGraphics();
        g.setFont(font);
        g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawString(text, padX, padY + ascent);
        g.dispose();

        NativeImage nativeImage = new NativeImage(highW, highH, false);
        for (int y = 0; y < highH; y++) {
            for (int x = 0; x < highW; x++) {
                int argb = highImg.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                // Guarantee pure white RGB across all pixels so bilinear texture sampling
                // never creates black fringing or muddy dark halos around letter edges
                nativeImage.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "modern_font/" + type.name().toLowerCase(Locale.ROOT) + "_"
                + Integer.toHexString(text.hashCode()) + "_" + pixelSize);
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
        tex.setFilter(true, false);
        tex.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        tex.upload();

        float quadW = highW / (float) OVERSAMPLE;
        float quadH = highH / (float) OVERSAMPLE;
        float screenPadX = padX / (float) OVERSAMPLE;
        float screenPadY = padY / (float) OVERSAMPLE;
        float textW = advance / (float) OVERSAMPLE;
        float textH = (ascent + descent) / (float) OVERSAMPLE;

        TextTexture result = new TextTexture(id, textW, textH, quadW, quadH, screenPadX, screenPadY);
        TEXTURES.put(key, result);
        return result;
    }

    private static TextTexture getFluxLogoTexture(int pixelSize) {
        TextTexture cached = LOGO_TEXTURES.get(pixelSize);
        if (cached != null) {
            return cached;
        }

        int renderSize = pixelSize * OVERSAMPLE;
        Font baseFont = getFont(Type.OXANIUM);
        Font font = baseFont.deriveFont(Font.BOLD, (float) renderSize);

        BufferedImage measureImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gMeasure = measureImg.createGraphics();
        gMeasure.setFont(font);
        gMeasure.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gMeasure.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics fm = gMeasure.getFontMetrics();

        int letterGap = Math.round(2.0F * OVERSAMPLE);
        int highW = Math.max(1, fm.stringWidth("FLUX") + letterGap * 4 + 32);
        int highH = Math.max(1, fm.getAscent() + fm.getDescent() + 16);
        int ascent = fm.getAscent();
        gMeasure.dispose();

        BufferedImage highImg = new BufferedImage(highW, highH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = highImg.createGraphics();
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int curX = 8;
        int fluW = fm.stringWidth("FLU");
        int xW = fm.stringWidth("X");
        int xPos = curX + fluW + letterGap;

        // Subtle crisp drop shadow
        g.setColor(new Color(0x70000000, true));
        g.drawString("FLU", curX + 2, ascent + 6);
        g.drawString("X", xPos + 2, ascent + 6);

        // 'FLU' in Titanium Platinum Silver
        GradientPaint silverGp = new GradientPaint(0, 4, new Color(0xFFFFFF), 0, highH - 4, new Color(0xCBD5E1));
        g.setPaint(silverGp);
        g.drawString("FLU", curX, ascent + 4);

        // 'X' in Radiant Electric Cyber Violet (Clean, no blurry halo)
        GradientPaint violetGp = new GradientPaint(xPos, 4, new Color(0xF0ABFC), xPos + xW, highH - 4, new Color(0x818CF8));
        g.setPaint(violetGp);
        g.drawString("X", xPos, ascent + 4);
        g.dispose();

        int width = Math.max(1, Math.round(highW / (float) OVERSAMPLE));
        int height = Math.max(1, Math.round(highH / (float) OVERSAMPLE));

        NativeImage nativeImage = new NativeImage(highW, highH, false);
        for (int y = 0; y < highH; y++) {
            for (int x = 0; x < highW; x++) {
                nativeImage.setColorArgb(x, y, highImg.getRGB(x, y));
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "modern_logo/flux_clean_" + pixelSize);
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
        tex.setFilter(true, false);
        tex.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        tex.upload();

        TextTexture result = new TextTexture(id, width, height);
        LOGO_TEXTURES.put(pixelSize, result);
        return result;
    }

    private static TextTexture getExodusLogoTexture(int pixelSize) {
        return getFluxLogoTexture(pixelSize);
    }

    private static TextTexture getBadgeTexture(String text, int bgArgb, int textArgb, int badgeHeight) {
        BadgeKey key = new BadgeKey(text, bgArgb, textArgb, badgeHeight);
        TextTexture cached = BADGE_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        int highH = badgeHeight * OVERSAMPLE;
        int fontSize = Math.round(highH * 0.58F);
        Font baseFont = getFont(Type.SF_BOLD);
        Font font = baseFont.deriveFont(Font.BOLD, (float) fontSize);

        BufferedImage measureImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gMeasure = measureImg.createGraphics();
        gMeasure.setFont(font);
        gMeasure.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gMeasure.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics fm = gMeasure.getFontMetrics();

        int textW = fm.stringWidth(text);
        int padX = Math.round(highH * 0.45F);
        int highW = textW + padX * 2;
        int ascent = fm.getAscent();
        gMeasure.dispose();

        BufferedImage highImg = new BufferedImage(highW, highH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = highImg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Smooth rounded pill background
        g.setColor(new Color(bgArgb, true));
        g.fill(new RoundRectangle2D.Float(0, 0, highW, highH, highH * 0.55F, highH * 0.55F));

        // Delicate 1px border
        g.setColor(new Color(0x35FFFFFF, true));
        g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, highW - 1.0F, highH - 1.0F, highH * 0.55F, highH * 0.55F));

        // Centered crisp text
        g.setFont(font);
        g.setColor(new Color(textArgb, true));
        int textX = (highW - textW) / 2;
        int textY = (highH - fm.getHeight()) / 2 + ascent;
        g.drawString(text, textX, textY);
        g.dispose();

        int width = Math.max(1, Math.round(highW / (float) OVERSAMPLE));
        int height = Math.max(1, Math.round(highH / (float) OVERSAMPLE));

        NativeImage nativeImage = new NativeImage(highW, highH, false);
        for (int y = 0; y < highH; y++) {
            for (int x = 0; x < highW; x++) {
                nativeImage.setColorArgb(x, y, highImg.getRGB(x, y));
            }
        }

        Identifier id = Identifier.of("fluxvisuals", "modern_badge/" + Integer.toHexString(key.hashCode()) + "_" + badgeHeight);
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
        tex.setFilter(true, false);
        tex.setClamp(true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        tex.upload();

        TextTexture result = new TextTexture(id, width, height);
        BADGE_TEXTURES.put(key, result);
        return result;
    }

    public static void drawTooltipBubble(DrawContext context, String title, List<String> lines, float x, float y, float w, float h, float arrowRelX, float alpha, float scale) {
        if (title == null || lines == null || alpha <= 0.01F) return;
        int targetW = Math.max(20, Math.round(w));
        int targetH = Math.max(20, Math.round(h));
        String key = title + "_" + lines.size() + "_" + targetW + "_" + targetH + "_" + Math.round(arrowRelX);

        TextTexture tex = TOOLTIP_TEXTURES.get(key);
        if (tex == null) {
            int highW = targetW * OVERSAMPLE;
            int highH = targetH * OVERSAMPLE;
            int radius = Math.round(14.0F * scale * OVERSAMPLE);
            int arrowSize = Math.round(6.0F * scale * OVERSAMPLE);
            int arrowPx = Math.round(arrowRelX * OVERSAMPLE);

            BufferedImage highImg = new BufferedImage(highW, highH + arrowSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = highImg.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Card background (Opaque solid midnight obsidian)
            g.setColor(new Color(0xFF0E121B, false));
            g.fill(new RoundRectangle2D.Float(0, 0, highW, highH, radius, radius));

            // Arrow triangle
            Path2D.Float arrow = new Path2D.Float();
            arrow.moveTo(arrowPx - arrowSize, highH - 1);
            arrow.lineTo(arrowPx, highH + arrowSize);
            arrow.lineTo(arrowPx + arrowSize, highH - 1);
            arrow.closePath();
            g.fill(arrow);

            // Vibrant violet border
            g.setColor(new Color(0xC07C3AED, true));
            g.setStroke(new BasicStroke(1.5F * OVERSAMPLE));
            g.draw(new RoundRectangle2D.Float(1, 1, highW - 2, highH - 2, radius, radius));
            g.draw(arrow);

            // Title Text
            Font titleFont = getFont(Type.SF_BOLD).deriveFont(Font.BOLD, 12.0F * scale * OVERSAMPLE);
            g.setFont(titleFont);
            g.setColor(Color.WHITE);
            FontMetrics tfm = g.getFontMetrics();
            int curY = Math.round(8.0F * scale * OVERSAMPLE) + tfm.getAscent();
            int padLeft = Math.round(12.0F * scale * OVERSAMPLE);
            g.drawString(title, padLeft, curY);

            // Description Lines
            Font descFont = getFont(Type.SF_MEDIUM).deriveFont(Font.PLAIN, 10.0F * scale * OVERSAMPLE);
            g.setFont(descFont);
            g.setColor(new Color(0xD1D5DB));
            FontMetrics dfm = g.getFontMetrics();
            curY += tfm.getDescent() + Math.round(4.0F * scale * OVERSAMPLE);

            for (String line : lines) {
                curY += dfm.getAscent();
                g.drawString(line, padLeft, curY);
                curY += dfm.getDescent() + Math.round(2.0F * scale * OVERSAMPLE);
            }

            g.dispose();

            NativeImage nativeImage = new NativeImage(highW, highH + arrowSize, false);
            for (int iy = 0; iy < highH + arrowSize; iy++) {
                for (int ix = 0; ix < highW; ix++) {
                    nativeImage.setColorArgb(ix, iy, highImg.getRGB(ix, iy));
                }
            }

            Identifier id = Identifier.of("fluxvisuals", "modern_tooltip/" + Integer.toHexString(key.hashCode()));
            NativeImageBackedTexture natTex = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
            natTex.setFilter(true, false);
            natTex.setClamp(true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, natTex);
            natTex.upload();

            tex = new TextTexture(id, targetW, targetH + Math.round(6.0F * scale));
            TOOLTIP_TEXTURES.put(key, tex);
        }

        int renderA = Math.max(0, Math.min(255, Math.round(255 * alpha)));
        int tint = (renderA << 24) | 0x00FFFFFF;

        Render2D.drawRoundTexture(context, tex.id, x, y, tex.width, tex.height, 0.0F, tint);
    }

    public static void drawTooltipBubble(DrawContext context, String title, List<String> lines, float targetAnchorX, float targetAnchorY, float alpha, float scale) {
        if (title == null || lines == null || alpha <= 0.01F) return;
        float w = 180.0F * scale;
        float h = (28.0F + lines.size() * 14.0F) * scale;
        float x = targetAnchorX - w * 0.5F;
        float y = targetAnchorY - h - 8.0F * scale;
        float arrowRelX = w * 0.5F;
        drawTooltipBubble(context, title, lines, x, y, w, h, arrowRelX, alpha, scale);
    }

    public static void drawContinuousHueBar(DrawContext context, float x, float y, float w, float h, float radius, float alpha) {
        if (alpha <= 0.01F) return;
        int targetW = Math.max(10, Math.round(w));
        int targetH = Math.max(4, Math.round(h));
        int radPx = Math.max(1, Math.round(radius));
        String key = targetW + "_" + targetH + "_" + radPx;

        TextTexture tex = HUE_TEXTURES.get(key);
        if (tex == null) {
            int highW = targetW * OVERSAMPLE;
            int highH = targetH * OVERSAMPLE;
            int highRad = radPx * OVERSAMPLE * 2;

            BufferedImage highImg = new BufferedImage(highW, highH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = highImg.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g.setClip(new RoundRectangle2D.Float(0, 0, highW, highH, highRad, highRad));

            for (int ix = 0; ix < highW; ix++) {
                float hue = ix / (float) Math.max(1, highW - 1);
                int rgb = Color.HSBtoRGB(hue, 1.0F, 1.0F);
                g.setColor(new Color(rgb));
                g.drawLine(ix, 0, ix, highH);
            }
            g.dispose();

            NativeImage image = new NativeImage(highW, highH, false);
            for (int iy = 0; iy < highH; iy++) {
                for (int ix = 0; ix < highW; ix++) {
                    image.setColorArgb(ix, iy, highImg.getRGB(ix, iy));
                }
            }

            Identifier id = Identifier.of("fluxvisuals", "modern_palette/hue_round_" + key);
            NativeImageBackedTexture natTex = new NativeImageBackedTexture(() -> id.toString(), image);
            natTex.setFilter(true, false);
            natTex.setClamp(true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, natTex);
            natTex.upload();

            tex = new TextTexture(id, targetW, targetH);
            HUE_TEXTURES.put(key, tex);
        }

        int renderA = Math.max(0, Math.min(255, Math.round(255 * alpha)));
        int tint = (renderA << 24) | 0x00FFFFFF;

        Render2D.drawRoundTexture(context, tex.id, x, y, tex.width, tex.height, radius, tint);
    }

    public static void drawBadge(DrawContext context, String text, float x, float y, int bgArgb, int textArgb, int height, float alpha) {
        if (text == null || text.isEmpty()) return;
        TextTexture tex = getBadgeTexture(text, bgArgb, textArgb, Math.max(10, height));
        int renderA = Math.round(255 * alpha);
        int tint = (renderA << 24) | 0x00FFFFFF;

        Render2D.drawRoundTexture(context, tex.id, x, y, tex.width, tex.height, 0.0F, tint);
    }

    public static float getBadgeWidth(String text, int bgArgb, int textArgb, int height) {
        if (text == null || text.isEmpty()) return 0.0F;
        TextTexture tex = getBadgeTexture(text, bgArgb, textArgb, Math.max(10, height));
        return (float) tex.width;
    }

    public static void drawFluxLogo(DrawContext context, float x, float y, float size, int color) {
        int pxSize = Math.max(8, Math.round(size));
        TextTexture tex = getFluxLogoTexture(pxSize);
        float renderW = tex.width;
        float renderH = tex.height;

        Render2D.drawRoundTexture(context, tex.id, x, y, renderW, renderH, 0.0F, color);
    }

    public static void drawExodusLogo(DrawContext context, float x, float y, float size, int color) {
        drawFluxLogo(context, x, y, size, color);
    }

    public static void draw(DrawContext context, String text, float x, float y, float size, int color, Type type) {
        if (text == null || text.isEmpty()) return;
        int pxSize = Math.max(8, Math.round(size));
        TextTexture tex = getTexture(text, pxSize, type);

        Render2D.drawRoundTexture(context, tex.id, x - tex.padX, y - tex.padY, tex.quadW, tex.quadH, 0.0F, color);
    }

    public static void drawCentered(DrawContext context, String text, float centerX, float y, float size, int color, Type type) {
        float width = getWidth(text, size, type);
        draw(context, text, centerX - width * 0.5F, y, size, color, type);
    }

    public static void drawRight(DrawContext context, String text, float rightX, float y, float size, int color, Type type) {
        float width = getWidth(text, size, type);
        draw(context, text, rightX - width, y, size, color, type);
    }

    public static float getWidth(String text, float size, Type type) {
        if (text == null || text.isEmpty()) return 0.0F;
        int pxSize = Math.max(8, Math.round(size));
        TextTexture tex = getTexture(text, pxSize, type);
        return (float) tex.width;
    }

    public static float getHeight(float size, Type type) {
        int pxSize = Math.max(8, Math.round(size));
        return (float) pxSize;
    }
}
