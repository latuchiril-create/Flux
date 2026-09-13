package dev.fuga.fluxvisuals.render.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.opengl.GlStateManager;
import dev.fuga.fluxvisuals.render.Render2D;
import dev.fuga.fluxvisuals.render.mesh.Render2DMesh;
import dev.fuga.fluxvisuals.render.shader.Render2DShader;
import dev.fuga.fluxvisuals.render.util.RenderColor;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hardware-accelerated MSDF (Multi-channel Signed Distance Field) Font.
 */
public final class MsdfFont {
    private static final Logger LOGGER = LoggerFactory.getLogger("MsdfFont");
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f().identity();

    private final String name;
    private final Identifier textureId;
    private final Map<Integer, MsdfGlyph> glyphMap = new HashMap<>(256);
    private final MsdfGlyph[] asciiGlyphs = new MsdfGlyph[256];

    private float distanceRange = 10.0F;
    private float atlasWidth = 512.0F;
    private float atlasHeight = 512.0F;
    private float emSize = 1.0F;
    private float lineHeight = 1.2F;
    private float ascender = 0.95F;
    private float descender = -0.25F;
    private boolean loaded = false;

    // Pre-allocated dynamic vertex buffer for text batching
    private static int textVao = -1;
    private static int textVbo = -1;
    private static float[] vertexData = new float[1024 * 36]; // supports up to 1024 chars per draw call

    public MsdfFont(String name) {
        this.name = name;
        this.textureId = Identifier.of("fluxvisuals", "fonts/msdf/" + name + ".png");
    }

    public void init() {
        if (loaded) {
            return;
        }
        loaded = true;

        String jsonPath = "/assets/fluxvisuals/fonts/msdf/" + name + ".json";
        try (InputStream stream = MsdfFont.class.getResourceAsStream(jsonPath)) {
            if (stream == null) {
                LOGGER.error("MSDF font json not found: {}", jsonPath);
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

            if (root.has("atlas")) {
                JsonObject atlas = root.getAsJsonObject("atlas");
                if (atlas.has("distanceRange")) distanceRange = atlas.get("distanceRange").getAsFloat();
                if (atlas.has("width")) atlasWidth = atlas.get("width").getAsFloat();
                if (atlas.has("height")) atlasHeight = atlas.get("height").getAsFloat();
            }

            if (root.has("metrics")) {
                JsonObject metrics = root.getAsJsonObject("metrics");
                if (metrics.has("emSize")) emSize = metrics.get("emSize").getAsFloat();
                if (metrics.has("lineHeight")) lineHeight = metrics.get("lineHeight").getAsFloat();
                if (metrics.has("ascender")) ascender = metrics.get("ascender").getAsFloat();
                if (metrics.has("descender")) descender = metrics.get("descender").getAsFloat();
            }

            if (root.has("glyphs")) {
                JsonArray glyphs = root.getAsJsonArray("glyphs");
                for (JsonElement el : glyphs) {
                    JsonObject g = el.getAsJsonObject();
                    int unicode = g.get("unicode").getAsInt();
                    float advance = g.get("advance").getAsFloat();

                    boolean hasBounds = g.has("planeBounds") && g.has("atlasBounds");
                    float pLeft = 0, pBottom = 0, pRight = 0, pTop = 0;
                    float aLeft = 0, aBottom = 0, aRight = 0, aTop = 0;

                    if (hasBounds) {
                        JsonObject pb = g.getAsJsonObject("planeBounds");
                        pLeft = pb.get("left").getAsFloat();
                        pBottom = pb.get("bottom").getAsFloat();
                        pRight = pb.get("right").getAsFloat();
                        pTop = pb.get("top").getAsFloat();

                        JsonObject ab = g.getAsJsonObject("atlasBounds");
                        aLeft = ab.get("left").getAsFloat();
                        aBottom = ab.get("bottom").getAsFloat();
                        aRight = ab.get("right").getAsFloat();
                        aTop = ab.get("top").getAsFloat();
                    }

                    MsdfGlyph glyph = new MsdfGlyph(
                            unicode, advance, hasBounds,
                            pLeft, pBottom, pRight, pTop,
                            aLeft, aBottom, aRight, aTop,
                            atlasWidth, atlasHeight
                    );

                    glyphMap.put(unicode, glyph);
                    if (unicode >= 0 && unicode < 256) {
                        asciiGlyphs[unicode] = glyph;
                    }
                }
            }
            LOGGER.info("Loaded MSDF font '{}': {} glyphs, atlas {}x{}", name, glyphMap.size(), atlasWidth, atlasHeight);
        } catch (Exception e) {
            LOGGER.error("Error loading MSDF font {}: {}", name, e.getMessage());
        }
    }

    public MsdfGlyph getGlyph(int unicode) {
        if (!loaded) {
            init();
        }
        if (unicode >= 0 && unicode < 256) {
            MsdfGlyph g = asciiGlyphs[unicode];
            if (g != null) return g;
        }
        return glyphMap.get(unicode);
    }

    public float getWidth(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }
        if (!loaded) {
            init();
        }

        float width = 0.0F;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < len) {
                char code = text.charAt(i + 1);
                if (code == '#' && i + 7 < len) {
                    i += 7;
                } else {
                    i++;
                }
                continue;
            }
            MsdfGlyph glyph = getGlyph(c);
            if (glyph != null) {
                width += glyph.getAdvance() * size;
            } else {
                width += 0.5F * size;
            }
        }
        return width;
    }

    public float getHeight(float size) {
        return lineHeight * size;
    }

    public String trimToWidth(String text, float maxWidth, float size) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
            return "";
        }
        if (getWidth(text, size) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = getWidth(ellipsis, size);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        StringBuilder sb = new StringBuilder();
        float curW = 0.0F;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            float charW;
            if (c == '\u00A7' && i + 1 < len) {
                char code = text.charAt(i + 1);
                if (code == '#' && i + 7 < len) {
                    sb.append(text, i, i + 8);
                    i += 7;
                } else {
                    sb.append(text, i, i + 2);
                    i++;
                }
                continue;
            }
            MsdfGlyph glyph = getGlyph(c);
            charW = (glyph != null ? glyph.getAdvance() : 0.5F) * size;
            if (curW + charW + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(c);
            curW += charW;
        }
        return sb.append(ellipsis).toString();
    }

    /* =========================================================================
     * Rendering Methods
     * ========================================================================= */

    public void drawString(DrawContext context, String text, float x, float y, float size, int color) {
        drawString(Render2D.extractModelView(context), text, x, y, size, color, false, 0.0F, 0);
    }

    public void drawString(Matrix4f modelView, String text, float x, float y, float size, int color) {
        drawString(modelView, text, x, y, size, color, false, 0.0F, 0);
    }

    public void drawStringWithShadow(DrawContext context, String text, float x, float y, float size, int color) {
        drawStringWithShadow(Render2D.extractModelView(context), text, x, y, size, color);
    }

    public void drawStringWithShadow(Matrix4f modelView, String text, float x, float y, float size, int color) {
        int shadowColor = RenderColor.withAlpha(0xFF000000, RenderColor.getAlphaFloat(color) * 0.65F);
        drawString(modelView, text, x + 1.0F, y + 1.0F, size, shadowColor, false, 0.0F, 0);
        drawString(modelView, text, x, y, size, color, false, 0.0F, 0);
    }

    public void drawCenteredString(DrawContext context, String text, float centerX, float y, float size, int color) {
        float width = getWidth(text, size);
        drawString(Render2D.extractModelView(context), text, centerX - width * 0.5F, y, size, color, false, 0.0F, 0);
    }

    public void drawRightString(DrawContext context, String text, float rightX, float y, float size, int color) {
        float width = getWidth(text, size);
        drawString(Render2D.extractModelView(context), text, rightX - width, y, size, color, false, 0.0F, 0);
    }

    public void drawOutlineString(DrawContext context, String text, float x, float y, float size,
                                  float outlineThickness, int color, int outlineColor) {
        drawString(Render2D.extractModelView(context), text, x, y, size, color, true, outlineThickness, outlineColor);
    }

    public void drawString(Matrix4f modelView, String text, float x, float y, float size, int color,
                           boolean outline, float outlineThickness, int outlineColor) {
        if (text == null || text.isEmpty() || RenderColor.getAlpha(color) <= 0 || size <= 0.0F) {
            return;
        }
        if (!loaded) {
            init();
        }

        int texId = getTextureGlId();
        if (texId == 0) {
            return;
        }

        ensureTextBuffers();

        int vertexCount = 0;
        int maxVertices = vertexData.length / 9;

        float curX = x;
        float baselineY = y + ascender * size;
        int curColor = color;
        float r = RenderColor.getRedFloat(curColor);
        float g = RenderColor.getGreenFloat(curColor);
        float b = RenderColor.getBlueFloat(curColor);
        float a = RenderColor.getAlphaFloat(curColor);

        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // Parse color codes
            if (c == '\u00A7' && i + 1 < len) {
                char code = text.charAt(i + 1);
                if (code == '#' && i + 7 < len) {
                    try {
                        int hex = Integer.parseInt(text.substring(i + 2, i + 8), 16);
                        curColor = RenderColor.withAlpha(hex | 0xFF000000, a);
                        r = RenderColor.getRedFloat(curColor);
                        g = RenderColor.getGreenFloat(curColor);
                        b = RenderColor.getBlueFloat(curColor);
                    } catch (NumberFormatException ignored) {
                    }
                    i += 7;
                    continue;
                } else {
                    int mcColor = getMinecraftColor(code);
                    if (mcColor != -1) {
                        curColor = RenderColor.withAlpha(mcColor, a);
                        r = RenderColor.getRedFloat(curColor);
                        g = RenderColor.getGreenFloat(curColor);
                        b = RenderColor.getBlueFloat(curColor);
                    }
                    i++;
                    continue;
                }
            }

            MsdfGlyph glyph = getGlyph(c);
            if (glyph == null) {
                curX += 0.5F * size;
                continue;
            }

            if (glyph.hasBounds()) {
                float x0 = curX + glyph.getPlaneLeft() * size;
                float x1 = curX + glyph.getPlaneRight() * size;
                float y0 = baselineY - glyph.getPlaneTop() * size;
                float y1 = baselineY - glyph.getPlaneBottom() * size;

                float u0 = glyph.getU0();
                float v0 = glyph.getV0();
                float u1 = glyph.getU1();
                float v1 = glyph.getV1();

                if (vertexCount + 6 > maxVertices) {
                    break;
                }

                int offset = vertexCount * 9;

                // Triangle 1: (x0, y1), (x1, y1), (x1, y0)
                offset = putVertex(offset, x0, y1, u0, v1, r, g, b, a);
                offset = putVertex(offset, x1, y1, u1, v1, r, g, b, a);
                offset = putVertex(offset, x1, y0, u1, v0, r, g, b, a);

                // Triangle 2: (x0, y1), (x1, y0), (x0, y0)
                offset = putVertex(offset, x0, y1, u0, v1, r, g, b, a);
                offset = putVertex(offset, x1, y0, u1, v0, r, g, b, a);
                putVertex(offset, x0, y0, u0, v0, r, g, b, a);

                vertexCount += 6;
            }

            curX += glyph.getAdvance() * size;
        }

        if (vertexCount == 0) {
            return;
        }

        int finalVertexCount = vertexCount;
        Render2D.withMainFramebuffer(() -> {
            Render2D.setupGlState();
            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            Render2DShader shader = Render2DShader.MSDF_FONT;
            try {
                shader.bind();

                shader.setMatrix4f("ProjMat", Render2D.getProjectionMatrix());
                shader.setMatrix4f("ModelViewMat", modelView);
                shader.setFloat("Range", distanceRange);
                shader.setFloat("Thickness", 0.0F);
                shader.setFloat("Smoothness", 0.0F);
                shader.setInt("Outline", outline ? 1 : 0);
                shader.setFloat("OutlineThickness", outlineThickness);
                shader.setColor("OutlineColor", outlineColor);
                shader.setInt("Sampler0", 0);

                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                GlStateManager._bindTexture(texId);

                GL30.glBindVertexArray(textVao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, textVbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_STREAM_DRAW);

                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, finalVertexCount);
            } finally {
                try {
                    GL30.glBindVertexArray(prevVao);
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
                    GlStateManager._bindTexture(0);
                    shader.unbind();
                } catch (RuntimeException ignored) {
                }
                Render2D.restoreGlState();
            }
        });
    }

    private static int putVertex(int offset, float x, float y, float u, float v, float r, float g, float b, float a) {
        vertexData[offset++] = x;
        vertexData[offset++] = y;
        vertexData[offset++] = 0.0F;
        vertexData[offset++] = u;
        vertexData[offset++] = v;
        vertexData[offset++] = r;
        vertexData[offset++] = g;
        vertexData[offset++] = b;
        vertexData[offset++] = a;
        return offset;
    }

    private static void ensureTextBuffers() {
        if (textVao != -1 && GL30.glIsVertexArray(textVao)) {
            return;
        }
        textVao = GL30.glGenVertexArrays();
        textVbo = GL15.glGenBuffers();

        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(textVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, textVbo);

        // Stride: 3 (Pos) + 2 (UV) + 4 (Color) = 9 floats * 4 bytes = 36 bytes
        int stride = 36;

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);

        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 12L);

        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, stride, 20L);

        GL30.glBindVertexArray(prevVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private int getTextureGlId() {
        AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
        if (tex != null && tex.getGlTexture() instanceof GlTexture glTex) {
            return glTex.getGlId();
        }
        return 0;
    }

    private static int getMinecraftColor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f', 'r' -> 0xFFFFFFFF;
            default -> -1;
        };
    }
}
