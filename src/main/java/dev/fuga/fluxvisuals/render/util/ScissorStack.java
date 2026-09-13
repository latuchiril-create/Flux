package dev.fuga.fluxvisuals.render.util;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;

/**
 * Robust nested scissor / clipping stack with DPI scaling and intersection support.
 */
public final class ScissorStack {
    public record ScissorRect(float x, float y, float width, float height) {
        public ScissorRect intersect(ScissorRect other) {
            float newX = Math.max(this.x, other.x);
            float newY = Math.max(this.y, other.y);
            float newX2 = Math.min(this.x + this.width, other.x + other.width);
            float newY2 = Math.min(this.y + this.height, other.y + other.height);
            float newW = Math.max(0.0F, newX2 - newX);
            float newH = Math.max(0.0F, newY2 - newY);
            return new ScissorRect(newX, newY, newW, newH);
        }
    }

    private static final Deque<ScissorRect> STACK = new ArrayDeque<>();

    private ScissorStack() {
    }

    public static void push(float x, float y, float width, float height) {
        ScissorRect newRect = new ScissorRect(x, y, width, height);
        if (!STACK.isEmpty()) {
            newRect = STACK.peek().intersect(newRect);
        }
        STACK.push(newRect);
        apply(newRect);
    }

    public static void pop() {
        if (STACK.isEmpty()) {
            return;
        }
        STACK.pop();
        if (STACK.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            apply(STACK.peek());
        }
    }

    public static void clear() {
        STACK.clear();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void apply(ScissorRect rect) {
        Window window = MinecraftClient.getInstance().getWindow();
        if (window == null) {
            return;
        }
        double scale = window.getScaleFactor();
        int fbHeight = window.getFramebufferHeight();

        int x = (int) Math.round(rect.x * scale);
        int y = (int) Math.round((rect.y + rect.height) * scale);
        int w = (int) Math.round(rect.width * scale);
        int h = (int) Math.round(rect.height * scale);

        int glY = Math.max(0, fbHeight - y);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.max(0, x), Math.max(0, glY), Math.max(0, w), Math.max(0, h));
    }
}
