package dev.fuga.fluxvisuals.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.fuga.fluxvisuals.render.font.Fonts;
import dev.fuga.fluxvisuals.render.font.MsdfFont;
import dev.fuga.fluxvisuals.render.liqvid.ScreenScale;
import dev.fuga.fluxvisuals.render.mesh.Render2DMesh;
import dev.fuga.fluxvisuals.render.shader.Render2DShader;
import dev.fuga.fluxvisuals.render.util.RenderColor;
import dev.fuga.fluxvisuals.render.util.ScissorStack;
import dev.fuga.fluxvisuals.render.util.GlStateGuard;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Modern, hardware-accelerated, ultra-fast 2D & UI Render Engine for Fabric 1.21.8.
 * Replaces vanilla unrounded primitives and bitmap fonts with GPU-evaluated SDF shaders.
 */
public final class Render2D {
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f().identity();
    private static final Matrix4f ORTHO_MATRIX = new Matrix4f();
    private static final float DEFAULT_AA_PAD = 2.0F;
    private static final Deque<GlStateGuard> GUI_STATE_STACK = new ArrayDeque<>();
    private static final Set<Integer> MIPMAPPED_TEXTURES = new HashSet<>();

    private Render2D() {
    }

    /* =========================================================================
     * Main Framebuffer & Matrix Context Helpers
     * ========================================================================= */

    public static void withMainFramebuffer(Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null
                || !(framebuffer.getColorAttachment() instanceof GlTexture colorTexture)
                || !(RenderSystem.getDevice() instanceof GlBackend backend)) {
            action.run();
            return;
        }
        int width = window.getFramebufferWidth();
        int height = window.getFramebufferHeight();
        int scaledWidth = window.getScaledWidth();
        int scaledHeight = window.getScaledHeight();
        if (width <= 0 || height <= 0 || scaledWidth <= 0 || scaledHeight <= 0) {
            return;
        }
        int mainFbo = colorTexture.getOrCreateFramebuffer(backend.getBufferManager(), framebuffer.getDepthAttachment());

        int previousFbo = GlStateManager.getFrameBuffer(GL30.GL_FRAMEBUFFER);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] previousScissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, previousScissorBox);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        GlStateManager._viewport(0, 0, width, height);
        if (!scissorWasEnabled) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glDisable(GL11.GL_CULL_FACE);
        ScreenScale.begin(width / (double) scaledWidth);
        try {
            action.run();
        } finally {
            ScreenScale.end();
            GlStateManager._activeTexture(previousActiveTexture);
            if (scissorWasEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(previousScissorBox[0], previousScissorBox[1],
                        previousScissorBox[2], previousScissorBox[3]);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            if (cullWasEnabled) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GlStateManager._viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
        }
    }

    public static Matrix4f getProjectionMatrix() {
        if (ScreenScale.isActive()) {
            return ScreenScale.getProjectionMatrix();
        }
        Window window = MinecraftClient.getInstance().getWindow();
        if (window == null) {
            return IDENTITY_MATRIX;
        }
        float width = window.getScaledWidth();
        float height = window.getScaledHeight();
        ORTHO_MATRIX.identity().setOrtho(0.0F, width, height, 0.0F, -1000.0F, 1000.0F);
        return ORTHO_MATRIX;
    }

    public static Matrix4f extractModelView(DrawContext context) {
        if (context != null && context.getMatrices() != null) {
            Matrix3x2fStack stack = context.getMatrices();
            return new Matrix4f(
                    stack.m00, stack.m01, 0.0F, 0.0F,
                    stack.m10, stack.m11, 0.0F, 0.0F,
                    0.0F,      0.0F,      1.0F, 0.0F,
                    stack.m20, stack.m21, 0.0F, 1.0F
            );
        }
        return ScreenScale.isActive() ? ScreenScale.getModelViewMatrix() : IDENTITY_MATRIX;
    }

    public static void setupGlState() {
        // Леaked GL state corrupts vanilla draws executed later in the frame
        // (deferred GuiRenderer): item icons vanish while text/counts survive.
        GUI_STATE_STACK.push(GlStateGuard.captureAndForceGuiState());
    }

    public static void restoreGlState() {
        GlStateGuard guard = GUI_STATE_STACK.poll();
        if (guard != null) {
            guard.restore();
        } else {
            // Fallback: never leave depth writes disabled even on unbalanced use.
            GlStateManager._depthMask(true);
        }
    }

    /* =========================================================================
     * Rounded Rectangle (Solid Color)
     * ========================================================================= */

    public static void drawRound(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        drawRound(extractModelView(context), x, y, width, height, radius, radius, radius, radius, color);
    }

    public static void drawRound(DrawContext context, float x, float y, float width, float height, float radius, Color color) {
        drawRound(context, x, y, width, height, radius, color.getRGB());
    }

    public static void drawRound(float x, float y, float width, float height, float radius, int color) {
        drawRound(IDENTITY_MATRIX, x, y, width, height, radius, radius, radius, radius, color);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, Color color) {
        drawRound(x, y, width, height, radius, color.getRGB());
    }

    public static void drawRound(DrawContext context, float x, float y, float width, float height,
                                 float rTL, float rTR, float rBR, float rBL, int color) {
        drawRound(extractModelView(context), x, y, width, height, rTL, rTR, rBR, rBL, color);
    }

    public static void drawRound(float x, float y, float width, float height,
                                 float rTL, float rTR, float rBR, float rBL, int color) {
        drawRound(IDENTITY_MATRIX, x, y, width, height, rTL, rTR, rBR, rBL, color);
    }

    public static void drawRound(Matrix4f modelView, float x, float y, float width, float height,
                                 float rTL, float rTR, float rBR, float rBL, int color) {
        if (width <= 0.0F || height <= 0.0F || RenderColor.getAlpha(color) <= 0) {
            return;
        }

        withMainFramebuffer(() -> {
            setupGlState();
            try {
                Render2DShader shader = Render2DShader.ROUNDED_RECT;
                shader.bind();

                shader.setMatrix4f("ProjMat", getProjectionMatrix());
                shader.setMatrix4f("ModelViewMat", modelView);
                shader.setVec2("size", width, height);
                shader.setVec4("radius", rTL, rTR, rBR, rBL);
                shader.setColor("color1", color);
                shader.setColor("color2", color);
                shader.setColor("color3", color);
                shader.setColor("color4", color);

                Render2DMesh.drawPaddedQuad(x, y, width, height, DEFAULT_AA_PAD);

                shader.unbind();
            } finally {
                restoreGlState();
            }
        });
    }

    /* =========================================================================
     * Rounded Rectangle (Gradient)
     * ========================================================================= */

    public static void drawGradientRound(DrawContext context, float x, float y, float width, float height, float radius,
                                         int tl, int tr, int br, int bl) {
        drawGradientRound(extractModelView(context), x, y, width, height, radius, radius, radius, radius, tl, tr, br, bl);
    }

    public static void drawGradientRound(DrawContext context, float x, float y, float width, float height, float radius,
                                         Color tl, Color tr, Color br, Color bl) {
        drawGradientRound(context, x, y, width, height, radius, tl.getRGB(), tr.getRGB(), br.getRGB(), bl.getRGB());
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius,
                                         int tl, int tr, int br, int bl) {
        drawGradientRound(IDENTITY_MATRIX, x, y, width, height, radius, radius, radius, radius, tl, tr, br, bl);
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius,
                                         Color tl, Color tr, Color br, Color bl) {
        drawGradientRound(x, y, width, height, radius, tl.getRGB(), tr.getRGB(), br.getRGB(), bl.getRGB());
    }

    public static void drawGradientRoundLR(DrawContext context, float x, float y, float width, float height, float radius,
                                           int leftColor, int rightColor) {
        drawGradientRound(context, x, y, width, height, radius, leftColor, rightColor, rightColor, leftColor);
    }

    public static void drawGradientRoundLR(float x, float y, float width, float height, float radius,
                                           int leftColor, int rightColor) {
        drawGradientRound(IDENTITY_MATRIX, x, y, width, height, radius, radius, radius, radius,
                leftColor, rightColor, rightColor, leftColor);
    }

    public static void drawGradientRoundTB(DrawContext context, float x, float y, float width, float height, float radius,
                                           int topColor, int bottomColor) {
        drawGradientRound(context, x, y, width, height, radius, topColor, topColor, bottomColor, bottomColor);
    }

    public static void drawGradientRoundTB(float x, float y, float width, float height, float radius,
                                           int topColor, int bottomColor) {
        drawGradientRound(IDENTITY_MATRIX, x, y, width, height, radius, radius, radius, radius,
                topColor, topColor, bottomColor, bottomColor);
    }

    public static void drawGradientRound(Matrix4f modelView, float x, float y, float width, float height,
                                         float rTL, float rTR, float rBR, float rBL,
                                         int tl, int tr, int br, int bl) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }

        withMainFramebuffer(() -> {
            setupGlState();
            try {
                Render2DShader shader = Render2DShader.ROUNDED_RECT;
                shader.bind();

                shader.setMatrix4f("ProjMat", getProjectionMatrix());
                shader.setMatrix4f("ModelViewMat", modelView);
                shader.setVec2("size", width, height);
                shader.setVec4("radius", rTL, rTR, rBR, rBL);
                shader.setColor("color1", tl);
                shader.setColor("color2", tr);
                shader.setColor("color3", br);
                shader.setColor("color4", bl);

                Render2DMesh.drawPaddedQuad(x, y, width, height, DEFAULT_AA_PAD);

                shader.unbind();
            } finally {
                restoreGlState();
            }
        });
    }

    /* =========================================================================
     * Rounded Rectangle Outline / Border
     * ========================================================================= */

    public static void drawRoundOutline(DrawContext context, float x, float y, float width, float height,
                                        float radius, float thickness, int outlineColor) {
        drawRoundOutline(extractModelView(context), x, y, width, height, radius, radius, radius, radius, thickness, outlineColor);
    }

    public static void drawRoundOutline(DrawContext context, float x, float y, float width, float height,
                                        float radius, float thickness, Color outlineColor) {
        drawRoundOutline(context, x, y, width, height, radius, thickness, outlineColor.getRGB());
    }

    public static void drawRoundOutline(float x, float y, float width, float height,
                                        float radius, float thickness, int outlineColor) {
        drawRoundOutline(IDENTITY_MATRIX, x, y, width, height, radius, radius, radius, radius, thickness, outlineColor);
    }

    public static void drawRoundOutline(float x, float y, float width, float height,
                                        float radius, float thickness, Color outlineColor) {
        drawRoundOutline(x, y, width, height, radius, thickness, outlineColor.getRGB());
    }

    public static void drawRoundOutline(Matrix4f modelView, float x, float y, float width, float height,
                                        float rTL, float rTR, float rBR, float rBL, float thickness, int outlineColor) {
        if (width <= 0.0F || height <= 0.0F || thickness <= 0.0F || RenderColor.getAlpha(outlineColor) <= 0) {
            return;
        }

        withMainFramebuffer(() -> {
            setupGlState();
            try {
                Render2DShader shader = Render2DShader.ROUNDED_OUTLINE;
                shader.bind();

                shader.setMatrix4f("ProjMat", getProjectionMatrix());
                shader.setMatrix4f("ModelViewMat", modelView);
                shader.setVec2("size", width, height);
                shader.setVec4("radius", rTL, rTR, rBR, rBL);
                shader.setFloat("thickness", thickness);
                shader.setColor("color1", outlineColor);
                shader.setColor("color2", outlineColor);
                shader.setColor("color3", outlineColor);
                shader.setColor("color4", outlineColor);

                Render2DMesh.drawPaddedQuad(x, y, width, height, DEFAULT_AA_PAD);

                shader.unbind();
            } finally {
                restoreGlState();
            }
        });
    }

    public static void drawGradientRoundOutline(DrawContext context, float x, float y, float width, float height,
                                                float radius, float thickness,
                                                int tl, int tr, int br, int bl) {
        drawGradientRoundOutline(extractModelView(context), x, y, width, height,
                radius, radius, radius, radius, thickness, tl, tr, br, bl);
    }

    public static void drawGradientRoundOutline(Matrix4f modelView, float x, float y, float width, float height,
                                                float rTL, float rTR, float rBR, float rBL, float thickness,
                                                int tl, int tr, int br, int bl) {
        if (width <= 0.0F || height <= 0.0F || thickness <= 0.0F) {
            return;
        }

        withMainFramebuffer(() -> {
            setupGlState();
            try {
                Render2DShader shader = Render2DShader.ROUNDED_OUTLINE;
                shader.bind();

                shader.setMatrix4f("ProjMat", getProjectionMatrix());
                shader.setMatrix4f("ModelViewMat", modelView);
                shader.setVec2("size", width, height);
                shader.setVec4("radius", rTL, rTR, rBR, rBL);
                shader.setFloat("thickness", thickness);
                shader.setColor("color1", tl);
                shader.setColor("color2", tr);
                shader.setColor("color3", br);
                shader.setColor("color4", bl);

                Render2DMesh.drawPaddedQuad(x, y, width, height, DEFAULT_AA_PAD);

                shader.unbind();
            } finally {
                restoreGlState();
            }
        });
    }

    /* =========================================================================
     * Drop Shadows & Glow
     * ========================================================================= */

    public static void drawShadow(DrawContext context, float x, float y, float width, float height,
                                  float radius, float shadowSize, int shadowColor) {
        drawShadow(extractModelView(context), x, y, width, height, radius, shadowSize, 0.0F, shadowColor);
    }

    public static void drawShadow(DrawContext context, float x, float y, float width, float height,
                                  float radius, float shadowSize, Color shadowColor) {
        drawShadow(context, x, y, width, height, radius, shadowSize, shadowColor.getRGB());
    }

    public static void drawShadow(float x, float y, float width, float height,
                                  float radius, float shadowSize, int shadowColor) {
        drawShadow(IDENTITY_MATRIX, x, y, width, height, radius, shadowSize, 0.0F, shadowColor);
    }

    public static void drawShadow(float x, float y, float width, float height,
                                  float radius, float shadowSize, Color shadowColor) {
        drawShadow(x, y, width, height, radius, shadowSize, shadowColor.getRGB());
    }

    public static void drawShadow(DrawContext context, float x, float y, float width, float height,
                                  float radius, float shadowSize, float spread, int shadowColor) {
        drawShadow(extractModelView(context), x, y, width, height, radius, shadowSize, spread, shadowColor);
    }

    public static void drawShadow(float x, float y, float width, float height,
                                  float radius, float shadowSize, float spread, int shadowColor) {
        drawShadow(IDENTITY_MATRIX, x, y, width, height, radius, shadowSize, spread, shadowColor);
    }

    public static void drawShadow(Matrix4f modelView, float x, float y, float width, float height,
                                  float radius, float shadowSize, float spread, int shadowColor) {
        if (width <= 0.0F || height <= 0.0F || shadowSize <= 0.0F || RenderColor.getAlpha(shadowColor) <= 0) {
            return;
        }

        withMainFramebuffer(() -> {
            setupGlState();
            try {
                Render2DShader shader = Render2DShader.ROUNDED_SHADOW;
                shader.bind();

                shader.setMatrix4f("ProjMat", getProjectionMatrix());
                shader.setMatrix4f("ModelViewMat", modelView);
                shader.setVec2("size", width, height);
                shader.setVec4("radius", radius, radius, radius, radius);
                shader.setFloat("shadowSize", shadowSize);
                shader.setFloat("shadowSpread", spread);
                shader.setColor("shadowColor", shadowColor);

                Render2DMesh.drawPaddedQuad(x, y, width, height, shadowSize + DEFAULT_AA_PAD);

                shader.unbind();
            } finally {
                restoreGlState();
            }
        });
    }

    public static void drawGlow(DrawContext context, float x, float y, float width, float height,
                                float radius, float glowSize, int glowColor) {
        drawShadow(context, x, y, width, height, radius, glowSize, 0.0F, glowColor);
    }

    public static void drawGlow(float x, float y, float width, float height,
                                float radius, float glowSize, int glowColor) {
        drawShadow(IDENTITY_MATRIX, x, y, width, height, radius, glowSize, 0.0F, glowColor);
    }

    public static void drawGlow(float x, float y, float width, float height,
                                float radius, float glowSize, Color glowColor) {
        drawShadow(x, y, width, height, radius, glowSize, glowColor.getRGB());
    }

    /* =========================================================================
     * Circles, Rings & Radial Shapes
     * ========================================================================= */

    public static void drawCircle(DrawContext context, float centerX, float centerY, float radius, int color) {
        drawCircle(extractModelView(context), centerX, centerY, radius, 0.0F, color, color);
    }

    public static void drawCircle(DrawContext context, float centerX, float centerY, float radius, Color color) {
        drawCircle(context, centerX, centerY, radius, color.getRGB());
    }

    public static void drawCircle(float centerX, float centerY, float radius, int color) {
        drawCircle(IDENTITY_MATRIX, centerX, centerY, radius, 0.0F, color, color);
    }

    public static void drawCircle(float centerX, float centerY, float radius, Color color) {
        drawCircle(centerX, centerY, radius, color.getRGB());
    }

    public static void drawCircleOutline(DrawContext context, float centerX, float centerY, float radius,
                                         float thickness, int color) {
        drawCircle(extractModelView(context), centerX, centerY, radius, Math.max(0.0F, radius - thickness), color, color);
    }

    public static void drawCircleOutline(float centerX, float centerY, float radius, float thickness, int color) {
        drawCircle(IDENTITY_MATRIX, centerX, centerY, radius, Math.max(0.0F, radius - thickness), color, color);
    }

    public static void drawGradientCircle(DrawContext context, float centerX, float centerY, float radius,
                                          int innerColor, int outerColor) {
        drawCircle(extractModelView(context), centerX, centerY, radius, 0.0F, innerColor, outerColor);
    }

    public static void drawGradientCircle(float centerX, float centerY, float radius, int innerColor, int outerColor) {
        drawCircle(IDENTITY_MATRIX, centerX, centerY, radius, 0.0F, innerColor, outerColor);
    }

    public static void drawCircle(Matrix4f modelView, float centerX, float centerY, float radius,
                                  float innerRadius, int innerColor, int outerColor) {
        if (radius <= 0.0F) {
            return;
        }

        withMainFramebuffer(() -> {
            setupGlState();
            try {
                Render2DShader shader = Render2DShader.CIRCLE;
                shader.bind();

                float size = radius * 2.0F;
                float x = centerX - radius;
                float y = centerY - radius;

                shader.setMatrix4f("ProjMat", getProjectionMatrix());
                shader.setMatrix4f("ModelViewMat", modelView);
                shader.setVec2("size", size, size);
                shader.setFloat("radius", radius);
                shader.setFloat("innerRadius", innerRadius);
                shader.setColor("innerColor", innerColor);
                shader.setColor("outerColor", outerColor);

                Render2DMesh.drawPaddedQuad(x, y, size, size, DEFAULT_AA_PAD);

                shader.unbind();
            } finally {
                restoreGlState();
            }
        });
    }

    /* =========================================================================
     * Textured Rounded Rectangles
     * ========================================================================= */

    public static void drawRoundTexture(DrawContext context, Identifier texture,
                                        float x, float y, float width, float height, float radius, float alpha) {
        drawRoundTexture(extractModelView(context), texture, x, y, width, height, radius, RenderColor.rgba(1.0F, 1.0F, 1.0F, alpha));
    }

    public static void drawRoundTexture(Identifier texture, float x, float y, float width, float height,
                                        float radius, float alpha) {
        drawRoundTexture(IDENTITY_MATRIX, texture, x, y, width, height, radius, RenderColor.rgba(1.0F, 1.0F, 1.0F, alpha));
    }

    public static void drawRoundTexture(DrawContext context, Identifier texture,
                                        float x, float y, float width, float height, float radius, int tint) {
        drawRoundTexture(extractModelView(context), texture, x, y, width, height, radius, tint);
    }

    public static void drawRoundTexture(Identifier texture, float x, float y, float width, float height,
                                        float radius, int tint) {
        drawRoundTexture(IDENTITY_MATRIX, texture, x, y, width, height, radius, tint);
    }

    public static void drawRoundTexture(Matrix4f modelView, Identifier texture,
                                        float x, float y, float width, float height, float radius, int tint) {
        if (width <= 0.0F || height <= 0.0F || texture == null) {
            return;
        }

        AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
        int glId = 0;
        if (tex != null && tex.getGlTexture() instanceof GlTexture glTexture) {
            glId = glTexture.getGlId();
        }
        if (glId == 0) {
            return;
        }

        int finalGlId = glId;
        withMainFramebuffer(() -> {
            setupGlState();
            try {
                Render2DShader shader = Render2DShader.ROUNDED_TEXTURE;
                shader.bind();

                shader.setMatrix4f("ProjMat", getProjectionMatrix());
                shader.setMatrix4f("ModelViewMat", modelView);
                shader.setVec2("size", width, height);
                shader.setVec4("radius", radius, radius, radius, radius);
                shader.setColor("colorModulator", tint);
                shader.setInt("Sampler0", 0);

                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                GlStateManager._bindTexture(finalGlId);
                int prevMinFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                int prevMagFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);

                if (radius <= 0.001F) {
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                } else {
                    if (!MIPMAPPED_TEXTURES.contains(finalGlId)) {
                        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                        MIPMAPPED_TEXTURES.add(finalGlId);
                    }
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                }

                float pad = radius > 0.001F ? DEFAULT_AA_PAD : 0.0F;
                try {
                    Render2DMesh.drawPaddedQuad(x, y, width, height, pad);
                } finally {
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, prevMinFilter);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, prevMagFilter);
                    GlStateManager._bindTexture(0);
                }

                shader.unbind();
            } finally {
                restoreGlState();
            }
        });
    }

    /* =========================================================================
     * Anti-Aliased Lines
     * ========================================================================= */

    public static void drawLine(DrawContext context, float x1, float y1, float x2, float y2, float thickness, int color) {
        drawLine(extractModelView(context), x1, y1, x2, y2, thickness, color, color);
    }

    public static void drawLine(float x1, float y1, float x2, float y2, float thickness, int color) {
        drawLine(IDENTITY_MATRIX, x1, y1, x2, y2, thickness, color, color);
    }

    public static void drawLine(float x1, float y1, float x2, float y2, float thickness, Color color) {
        drawLine(x1, y1, x2, y2, thickness, color.getRGB());
    }

    public static void drawGradientLine(DrawContext context, float x1, float y1, float x2, float y2,
                                        float thickness, int c1, int c2) {
        drawLine(extractModelView(context), x1, y1, x2, y2, thickness, c1, c2);
    }

    public static void drawGradientLine(float x1, float y1, float x2, float y2,
                                        float thickness, int c1, int c2) {
        drawLine(IDENTITY_MATRIX, x1, y1, x2, y2, thickness, c1, c2);
    }

    public static void drawLine(Matrix4f modelView, float x1, float y1, float x2, float y2,
                                float thickness, int c1, int c2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0.0001F) {
            drawCircle(modelView, x1, y1, thickness * 0.5F, 0.0F, c1, c1);
            return;
        }

        float radius = thickness * 0.5F;
        float angle = (float) Math.atan2(dy, dx);

        Matrix4f lineMatrix = new Matrix4f(modelView);
        lineMatrix.translate(x1, y1, 0.0F);
        lineMatrix.rotateZ(angle);

        drawGradientRound(lineMatrix, 0.0F, -radius, length, thickness, radius, radius, radius, radius, c1, c2, c2, c1);
    }

    /* =========================================================================
     * MSDF Font Typography
     * ========================================================================= */

    public static void drawString(DrawContext context, String text, float x, float y, float size, int color) {
        Fonts.REGULAR.drawString(context, text, x, y, size, color);
    }

    public static void drawString(DrawContext context, MsdfFont font, String text, float x, float y, float size, int color) {
        (font != null ? font : Fonts.REGULAR).drawString(context, text, x, y, size, color);
    }

    public static void drawCenteredString(DrawContext context, String text, float centerX, float y, float size, int color) {
        Fonts.REGULAR.drawCenteredString(context, text, centerX, y, size, color);
    }

    public static void drawCenteredString(DrawContext context, MsdfFont font, String text, float centerX, float y, float size, int color) {
        (font != null ? font : Fonts.REGULAR).drawCenteredString(context, text, centerX, y, size, color);
    }

    public static void drawRightString(DrawContext context, String text, float rightX, float y, float size, int color) {
        Fonts.REGULAR.drawRightString(context, text, rightX, y, size, color);
    }

    public static void drawRightString(DrawContext context, MsdfFont font, String text, float rightX, float y, float size, int color) {
        (font != null ? font : Fonts.REGULAR).drawRightString(context, text, rightX, y, size, color);
    }

    public static void drawStringWithShadow(DrawContext context, String text, float x, float y, float size, int color) {
        Fonts.REGULAR.drawStringWithShadow(context, text, x, y, size, color);
    }

    public static void drawStringWithShadow(DrawContext context, MsdfFont font, String text, float x, float y, float size, int color) {
        (font != null ? font : Fonts.REGULAR).drawStringWithShadow(context, text, x, y, size, color);
    }

    public static float getStringWidth(String text, float size) {
        return Fonts.REGULAR.getWidth(text, size);
    }

    public static float getStringWidth(MsdfFont font, String text, float size) {
        return (font != null ? font : Fonts.REGULAR).getWidth(text, size);
    }

    public static float getStringHeight(float size) {
        return Fonts.REGULAR.getHeight(size);
    }

    public static float getStringHeight(MsdfFont font, float size) {
        return (font != null ? font : Fonts.REGULAR).getHeight(size);
    }

    /* =========================================================================
     * Scissors / Clipping Stack
     * ========================================================================= */

    public static void pushScissor(float x, float y, float width, float height) {
        ScissorStack.push(x, y, width, height);
    }

    public static void popScissor() {
        ScissorStack.pop();
    }

    public static void clearScissor() {
        ScissorStack.clear();
    }
}
