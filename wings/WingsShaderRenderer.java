package dile.ru.utils.render.world.wingsshader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import dile.ru.utils.render.Render3D;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class WingsShaderRenderer {
    private static final int UNIFORM_SIZE = 144;
    private static final int MAX_VERTICES = 256;
    private static final int VERTEX_SIZE = 16;

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer vertexBuffer;
    private static ByteBuffer uniformData;
    private static ByteBuffer vertexData;
    private static int vertexCount;

    private WingsShaderRenderer() {
    }

    public static void begin() {
        if (pipeline == null) {
            initPipeline();
        }
        if (pipeline == null) return;
        vertexCount = 0;
        if (vertexData == null) {
            vertexData = MemoryUtil.memAlloc(MAX_VERTICES * VERTEX_SIZE);
        }
    }

    public static void addVertex(float x, float y, float z, int color) {
        if (vertexCount >= MAX_VERTICES || vertexData == null) return;
        int offset = vertexCount * VERTEX_SIZE;
        vertexData.putFloat(offset, x);
        vertexData.putFloat(offset + 4, y);
        vertexData.putFloat(offset + 8, z);
        vertexData.put(offset + 12, (byte) ((color >> 16) & 0xFF));
        vertexData.put(offset + 13, (byte) ((color >> 8) & 0xFF));
        vertexData.put(offset + 14, (byte) (color & 0xFF));
        vertexData.put(offset + 15, (byte) ((color >> 24) & 0xFF));
        vertexCount++;
    }

    public static void render(Matrix4f modelView, boolean depth) {
        if (pipeline == null || vertexCount == 0 || uniformBuffer == null || vertexBuffer == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getMainRenderTarget() == null) return;

        float time = (System.nanoTime() % 180_000_000_000L) / 1_000_000_000.0f;
        float resX = mc.getWindow().getWidth();
        float resY = mc.getWindow().getHeight();
        Matrix4f projection = new Matrix4f(Render3D.lastProjMat);

        uniformData.clear();
        putMatrix(uniformData, projection);
        putMatrix(uniformData, modelView);
        uniformData.putFloat(time);
        uniformData.putFloat(resX);
        uniformData.putFloat(resY);
        uniformData.putFloat(0.0f);
        uniformData.flip();

        vertexData.position(0);
        vertexData.limit(vertexCount * VERTEX_SIZE);

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(0, uniformData.remaining()), uniformData);
            encoder.writeToBuffer(vertexBuffer.slice(0, vertexData.remaining()), vertexData);

            GlStateManager._disableCull();
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_SRC_ALPHA);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "wings_shader",
                    mc.getMainRenderTarget().getColorTextureView(),
                    OptionalInt.empty(),
                    mc.getMainRenderTarget().getDepthTextureView(),
                    depth ? OptionalDouble.empty() : OptionalDouble.of(1.0)
            )) {
                pass.setPipeline(pipeline);
                pass.setUniform("Uniforms", uniformBuffer.slice());
                pass.setVertexBuffer(0, vertexBuffer);
                pass.draw(0, vertexCount);
            }

            GlStateManager._enableCull();
            GlStateManager._disableBlend();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        vertexData.clear();
        vertexCount = 0;
    }

    private static void initPipeline() {
        try {
            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("dile", "pipeline/world/wings_shader"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("dile", "world/wings_shader/wings_shader"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("dile", "world/wings_shader/wings_shader"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();

            uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "wings_shader_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE
            );
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "wings_shader_vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    MAX_VERTICES * VERTEX_SIZE
            );
        } catch (Throwable t) {
            t.printStackTrace();
            pipeline = null;
        }
    }

    private static void putMatrix(ByteBuffer buffer, Matrix4f m) {
        buffer.putFloat(m.m00()).putFloat(m.m01()).putFloat(m.m02()).putFloat(m.m03());
        buffer.putFloat(m.m10()).putFloat(m.m11()).putFloat(m.m12()).putFloat(m.m13());
        buffer.putFloat(m.m20()).putFloat(m.m21()).putFloat(m.m22()).putFloat(m.m23());
        buffer.putFloat(m.m30()).putFloat(m.m31()).putFloat(m.m32()).putFloat(m.m33());
    }

    public static void shutdown() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (uniformData != null) {
            MemoryUtil.memFree(uniformData);
            uniformData = null;
        }
        if (vertexData != null) {
            MemoryUtil.memFree(vertexData);
            vertexData = null;
        }
        pipeline = null;
    }
}
