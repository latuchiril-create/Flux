package dev.fuga.fluxvisuals.render.util;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * Saves the GL rasterizer, shader and texture state touched by custom raw-GL GUI
 * rendering and restores it afterwards.
 *
 * <p>Vanilla 1.21.8 GUI rendering is deferred: Screen/InGameHud only record
 * draws, GuiRenderer executes them at the end of the frame. Any leaked state
 * (disabled depth test, wrong blend func, un-bound shader program, clobbered
 * texture unit 0, disabled cull, ...) therefore corrupts vanilla draws issued
 * later - typically item icons (textured quads with depth testing) disappear
 * or flicker while plain text/counts keep rendering.
 */
public final class GlStateGuard implements AutoCloseable {
    private final boolean blendEnabled;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final boolean depthEnabled;
    private final boolean depthMask;
    private final boolean cullEnabled;
    private final int program;
    private final int activeTexture;
    private final int texture0;
    private final int sampler0;
    private final int vao;
    private final int vbo;
    private boolean restored;

    private GlStateGuard(boolean blendEnabled, int blendSrcRgb, int blendDstRgb,
                         int blendSrcAlpha, int blendDstAlpha,
                         boolean depthEnabled, boolean depthMask, boolean cullEnabled,
                         int program, int activeTexture, int texture0, int sampler0,
                         int vao, int vbo) {
        this.blendEnabled = blendEnabled;
        this.blendSrcRgb = blendSrcRgb;
        this.blendDstRgb = blendDstRgb;
        this.blendSrcAlpha = blendSrcAlpha;
        this.blendDstAlpha = blendDstAlpha;
        this.depthEnabled = depthEnabled;
        this.depthMask = depthMask;
        this.cullEnabled = cullEnabled;
        this.program = program;
        this.activeTexture = activeTexture;
        this.texture0 = texture0;
        this.sampler0 = sampler0;
        this.vao = vao;
        this.vbo = vbo;
    }

    /** Captures the current GL state. */
    public static GlStateGuard capture() {
        int activeTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int tex0;
        int samp0;
        if (activeTex == GL13.GL_TEXTURE0) {
            tex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            samp0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        } else {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            tex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            samp0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GlStateManager._activeTexture(activeTex);
        }
        int prog = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vaoBinding = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int vboBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        return new GlStateGuard(
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                prog,
                activeTex,
                tex0,
                samp0,
                vaoBinding,
                vboBinding);
    }

    /** Captures the current state and forces the state custom GUI primitives need. */
    public static GlStateGuard captureAndForceGuiState() {
        GlStateGuard guard = capture();
        forceGuiState();
        return guard;
    }

    /** State required by custom 2D primitives: standard alpha blend, no depth, no cull. */
    public static void forceGuiState() {
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    /** Restores the captured state (idempotent). */
    public void restore() {
        if (restored) {
            return;
        }
        restored = true;

        // Restore sampler 0 and texture 0
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL33.glBindSampler(0, sampler0);
        GlStateManager._bindTexture(texture0);

        // Restore active texture unit
        if (activeTexture != GL13.GL_TEXTURE0) {
            GlStateManager._activeTexture(activeTexture);
        }

        // Restore shader program
        GL20.glUseProgram(program);

        // Restore VAO and VBO
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        // Restore blend state
        GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        if (blendEnabled) {
            GlStateManager._enableBlend();
        } else {
            GlStateManager._disableBlend();
        }

        // Restore depth state
        GlStateManager._depthMask(depthMask);
        if (depthEnabled) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }

        // Restore cull face
        if (cullEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Override
    public void close() {
        restore();
    }
}
