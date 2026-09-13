package dev.fuga.fluxvisuals.render.liqvid;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.fuga.fluxvisuals.render.liqvid.ScreenScale;

public class ShaderProgram {
    private static final Logger LOGGER = LoggerFactory.getLogger("LunaWare");
    private static final int VERTEX_SHADER = 35633;
    private static final int FRAGMENT_SHADER = 35632;
    private static int vao = -1;
    private static int vbo = -1;
    private static final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    private final int programId;
    private boolean linked = true;
    private final Map<String, Integer> uniformLocations = new LinkedHashMap<>();

    public ShaderProgram() {
        this.programId = GL30.glCreateProgram();
    }

    public void attachSource(String source, int type) {
        int shaderId = GL30.glCreateShader(type);
        GL30.glShaderSource(shaderId, source);
        GL30.glCompileShader(shaderId);
        if (GL30.glGetShaderi(shaderId, 35713) == 0) {
            LOGGER.error("Shader compile error: {}", GL30.glGetShaderInfoLog(shaderId));
            this.linked = false;
            return;
        }
        GL30.glAttachShader(this.programId, shaderId);
    }

    public void link() {
        GL30.glBindAttribLocation(this.programId, 0, "Position");
        GL30.glBindAttribLocation(this.programId, 1, "UV0");
        GL30.glBindAttribLocation(this.programId, 2, "Color");
        GL30.glLinkProgram(this.programId);
        if (GL30.glGetProgrami(this.programId, 35714) == 0) {
            LOGGER.error("Program link error: {}", GL30.glGetProgramInfoLog(this.programId));
            this.linked = false;
        }
    }

    public boolean isLinked() {
        return this.linked;
    }

    public boolean isUsable() {
        return this.linked
                && this.programId != 0
                && GL30.glIsProgram(this.programId);
    }

    public void delete() {
        if (this.programId != 0 && GL30.glIsProgram(this.programId)) {
            GL30.glDeleteProgram(this.programId);
        }
        this.linked = false;
        this.uniformLocations.clear();
    }

    public void bind() {
        if (!this.linked) {
            return;
        }

        GL30.glUseProgram(this.programId);
        // 1.21.11: RenderSystem.getProjectionMatrix() is gone; LunaWare keeps its own
        // HUD ortho matrices in ScreenScale.
        Matrix4f projection = ScreenScale.getProjectionMatrix();
        Matrix4f modelView = ScreenScale.getModelViewMatrix();

        matrixBuffer.clear();
        projection.get(matrixBuffer);
        matrixBuffer.rewind();
        setMatrix("ProjMat", matrixBuffer);

        matrixBuffer.clear();
        modelView.get(matrixBuffer);
        matrixBuffer.rewind();
        setMatrix("ModelViewMat", matrixBuffer);

        MinecraftClient client = MinecraftClient.getInstance();
        setVec2("resolution", client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
    }

    /** Binds the program without uploading the ScreenScale matrices (for offscreen passes). */
    public void use() {
        if (this.linked) {
            GL30.glUseProgram(this.programId);
        }
    }

    public void unbind() {
        GL30.glUseProgram(0);
    }

    public static void drawQuad(float x, float y, float width, float height) {
        ensureQuadBuffers();
        float[] vertices = new float[]{
                x, y + height, 0.0F, 0.0F, 1.0F,
                x + width, y + height, 0.0F, 1.0F, 1.0F,
                x + width, y, 0.0F, 1.0F, 0.0F,
                x, y + height, 0.0F, 0.0F, 1.0F,
                x + width, y, 0.0F, 1.0F, 0.0F,
                x, y, 0.0F, 0.0F, 0.0F
        };
        int previousVao = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        GL30.glBindVertexArray(vao);
        GL30.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, vbo);
        GL30.glBufferData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, vertices, org.lwjgl.opengl.GL15.GL_STREAM_DRAW);
        GL30.glDrawArrays(org.lwjgl.opengl.GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(previousVao);
        GL30.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, previousVbo);
    }

    private static void ensureQuadBuffers() {
        if (vao != -1 && GL30.glIsVertexArray(vao)) {
            return;
        }
        int previousVao = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        vao = GL30.glGenVertexArrays();
        vbo = GL30.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL30.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, vbo);
        int stride = 20;
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(0, 3, org.lwjgl.opengl.GL11.GL_FLOAT, false, stride, 0L);
        GL30.glEnableVertexAttribArray(1);
        GL30.glVertexAttribPointer(1, 2, org.lwjgl.opengl.GL11.GL_FLOAT, false, stride, 12L);
        GL30.glBindVertexArray(previousVao);
        GL30.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, previousVbo);
    }

    private int uniform(String name) {
        return this.uniformLocations.computeIfAbsent(name, key -> GL30.glGetUniformLocation(this.programId, key));
    }

    public void setFloat(String name, float value) {
        GL30.glUniform1f(this.uniform(name), value);
    }

    public void setVec2(String name, float x, float y) {
        GL30.glUniform2f(this.uniform(name), x, y);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        GL30.glUniform4f(this.uniform(name), x, y, z, w);
    }

    public void setColor(String name, Color color) {
        GL30.glUniform4f(
                this.uniform(name),
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                color.getAlpha() / 255.0F
        );
    }

    public void setInt(String name, int value) {
        GL30.glUniform1i(this.uniform(name), value);
    }

    private void setMatrix(String name, FloatBuffer buffer) {
        GL30.glUniformMatrix4fv(this.uniform(name), false, buffer);
    }
}

