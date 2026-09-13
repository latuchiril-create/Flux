package dev.fuga.fluxvisuals.render.liqvid;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 1.21.6+ GUI is deferred: vanilla records the interface during Screen/InGameHud
 * render and executes the draws (plus menu blur) at the end of the frame in
 * GuiRenderer. LunaWare therefore renders in two phases:
 *  - RECORD phase (vanilla record window): only DrawContext work (item icons)
 *    is submitted; all raw-GL primitives are skipped.
 *  - DEFERRED phase (after GuiRenderer finished): the MAIN framebuffer is bound
 *    explicitly (vanilla leaves the item-atlas FBO or 0 bound after its passes)
 *    and scissor/cull/depth state is sanitized before raw-GL drawing.
 */
public final class RenderPhase {
    private static boolean recording;

    private static int previousFbo;
    private static final int[] previousViewport = new int[4];
    private static final int[] previousScissorBox = new int[4];
    private static boolean scissorWasEnabled;
    private static boolean cullWasEnabled;
    private static boolean depthWasEnabled;

    private static long frameIndex;
    private static int mainFbo = -1;

    private RenderPhase() {
    }

    public static void beginRecord() {
        recording = true;
    }

    public static void endRecord() {
        recording = false;
    }

    public static boolean isRecording() {
        return recording;
    }

    /** Binds the main framebuffer and prepares GL state for the deferred raw-GL pass. */
    public static void beginDeferredDraw() {
        previousFbo = GlStateManager.getFrameBuffer(GL30.GL_FRAMEBUFFER);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, previousScissorBox);
        scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        frameIndex++;
        mainFbo = -1;
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer != null
                && framebuffer.getColorAttachment() instanceof GlTexture colorTexture
                && RenderSystem.getDevice() instanceof GlBackend backend) {
            mainFbo = colorTexture.getOrCreateFramebuffer(backend.getBufferManager(), framebuffer.getDepthAttachment());
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
            GlStateManager._viewport(0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /**
     * Advances the per-frame counter used to invalidate frame-cached data
     * (for example the blurred snapshot in BlurRenderer). Must be called
     * exactly once per rendered frame.
     */
    public static void beginFrame() {
        frameIndex++;
    }

    public static long getFrameIndex() {
        return frameIndex;
    }

    /** GL id of the main framebuffer, valid during the deferred pass (-1 otherwise). */
    public static int getMainFbo() {
        return mainFbo;
    }

    /** Restores GL state after the deferred raw-GL pass. */
    public static void endDeferredDraw() {
        if (cullWasEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
        if (depthWasEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        if (scissorWasEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(previousScissorBox[0], previousScissorBox[1],
                    previousScissorBox[2], previousScissorBox[3]);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        GlStateManager._viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
    }
}

