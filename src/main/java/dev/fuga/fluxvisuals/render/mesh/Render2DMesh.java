package dev.fuga.fluxvisuals.render.mesh;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * High-performance mesh and vertex array manager for Render2D.
 */
public final class Render2DMesh {
    private static int vao = -1;
    private static int vbo = -1;

    private Render2DMesh() {
    }

    public static void ensureBuffers() {
        if (vao != -1 && GL30.glIsVertexArray(vao)) {
            return;
        }

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();

        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        // Stride: 3 (Pos) + 2 (UV/LocalPos) = 5 floats * 4 bytes = 20 bytes
        int stride = 20;

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);

        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 12L);

        GL30.glBindVertexArray(previousVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Draws an unpadded 2D quad covering [x, y, width, height].
     */
    public static void drawQuad(float x, float y, float width, float height) {
        drawPaddedQuad(x, y, width, height, 2.0F);
    }

    /**
     * Draws a padded 2D quad covering [x - pad, y - pad, width + pad*2, height + pad*2]
     * with local coordinates spanning [-pad, -pad] to [width + pad, height + pad].
     * This ensures the subpixel anti-aliasing curve is never clipped by polygon edges!
     */
    public static void drawPaddedQuad(float x, float y, float width, float height, float pad) {
        ensureBuffers();

        float px1 = x - pad;
        float py1 = y - pad;
        float px2 = x + width + pad;
        float py2 = y + height + pad;

        float lx1 = -pad;
        float ly1 = -pad;
        float lx2 = width + pad;
        float ly2 = height + pad;

        float[] vertices = new float[]{
                px1, py2, 0.0F, lx1, ly2,
                px2, py2, 0.0F, lx2, ly2,
                px2, py1, 0.0F, lx2, ly1,
                px1, py2, 0.0F, lx1, ly2,
                px2, py1, 0.0F, lx2, ly1,
                px1, py1, 0.0F, lx1, ly1
        };

        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GL30.glBindVertexArray(previousVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
    }
}
