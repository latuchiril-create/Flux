package dev.fuga.fluxvisuals.render.liqvid;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * 1.21.11: RenderSystem no longer exposes a CPU-side projection matrix, and the
 * vanilla GUI is rendered deferred. All LunaWare drawing is done with its own GL
 * shaders, so we keep our own ortho projection / model-view here instead of
 * touching the global RenderSystem state.
 */
public final class ScreenScale {
    private static float scale = 1.0F;
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f modelView = new Matrix4f();
    private static boolean active;

    private ScreenScale() {
    }

    public static void begin(double renderScale) {
        scale = (float) renderScale;
        Window window = MinecraftClient.getInstance().getWindow();
        double width = window.getFramebufferWidth() / renderScale;
        double height = window.getFramebufferHeight() / renderScale;
        projection.identity().setOrtho(0.0F, (float) width, (float) height, 0.0F, -1000.0F, 1000.0F);
        modelView.identity();
        active = true;
    }

    public static void end() {
        active = false;
        scale = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    public static boolean isActive() {
        return active;
    }

    public static Matrix4f getProjectionMatrix() {
        return projection;
    }

    public static Matrix4f getModelViewMatrix() {
        return modelView;
    }

    public static float getScale() {
        return scale;
    }

    public static double getScaledMouseX() {
        return getScaledMouse()[0];
    }

    public static double getScaledMouseY() {
        return getScaledMouse()[1];
    }

    /** Window cursor position -> scaled GUI coordinates. */
    public static double[] getScaledMouse() {
        Window window = MinecraftClient.getInstance().getWindow();
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), xpos, ypos);
        return new double[] {
            xpos[0] * window.getScaledWidth() / (double) window.getFramebufferWidth(),
            ypos[0] * window.getScaledHeight() / (double) window.getFramebufferHeight()
        };
    }

    /** Scaled GUI mouse coords -> HUD ortho coords (ScreenScale.begin(2.0)). */
    public static float toHudX(double scaledMouseX) {
        Window window = MinecraftClient.getInstance().getWindow();
        return (float) (scaledMouseX * (window.getFramebufferWidth() / 2.0) / window.getScaledWidth());
    }

    public static float toHudY(double scaledMouseY) {
        Window window = MinecraftClient.getInstance().getWindow();
        return (float) (scaledMouseY * (window.getFramebufferHeight() / 2.0) / window.getScaledHeight());
    }

    public static float mouseHudX() {
        return toHudX(getScaledMouseX());
    }

    public static float mouseHudY() {
        return toHudY(getScaledMouseY());
    }

    public static float getRenderWidth() {
        return toHudX(MinecraftClient.getInstance().getWindow().getScaledWidth());
    }

    public static float getRenderHeight() {
        return toHudY(MinecraftClient.getInstance().getWindow().getScaledHeight());
    }
}

