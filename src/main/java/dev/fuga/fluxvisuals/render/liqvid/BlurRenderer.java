package dev.fuga.fluxvisuals.render.liqvid;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.Window;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.fuga.fluxvisuals.render.Render2D;
import dev.fuga.fluxvisuals.render.liqvid.ShaderProgram;
import dev.fuga.fluxvisuals.render.liqvid.ShaderBuilder;
import dev.fuga.fluxvisuals.render.util.GlStateGuard;

/**
 * Fast background blur for HUD/GUI rects.
 *
 *  - The first Gaussian pass samples the main framebuffer color texture directly
 *    into a full-resolution target, so no framebuffer blit or lossy downsample is
 *    needed.
 *  - Separable Gaussian iterations (9-tap resolved in 5 fetches via linear
 *    filtering) run on the original pixels, keeping thin UI lines and block edges
 *    smooth instead of making them look pixelated after upsampling.
 *  - The result is cached per frame: any number of drawBlur() calls per frame
 *    reuse the same blurred texture; each rect only costs one textured quad.
 *  - The composite uses a tiny linear tent filter as a final anti-banding pass.
 */
public final class BlurRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LunaWare");
    private static final int DOWNSCALE = 1;
    private static final int ITERATIONS = 4;

    private static final String FULLSCREEN_VERTEX = """
            #version 150
            in vec3 Position;
            in vec2 UV0;
            out vec2 texCoord;
            void main() {
                texCoord = UV0;
                gl_Position = vec4(Position.xy, 0.0, 1.0);
            }
            """;

    private static final String GAUSSIAN_FRAGMENT = """
            #version 150
            uniform sampler2D Sampler0;
            uniform vec2 direction;
            uniform float blurRadius;
            uniform float blurSpread;
            in vec2 texCoord;
            out vec4 fragColor;
            void main() {
                float r = clamp(blurRadius, 1.0, 64.0);
                float sigma = r * 0.5;
                float twoSigmaSq = 2.0 * sigma * sigma;
                vec4 sum = texture(Sampler0, texCoord);
                float wSum = 1.0;
                for (float i = 1.0; i <= r; i += 2.0) {
                    float j = i + 1.0;
                    float w1 = exp(-(i * i) / twoSigmaSq);
                    float w2 = (j <= r) ? exp(-(j * j) / twoSigmaSq) : 0.0;
                    float w = w1 + w2;
                    float offset = (i * w1 + j * w2) / max(w, 1e-6);
                    vec2 d = direction * max(blurSpread, 1.0) * offset;
                    sum += (texture(Sampler0, texCoord + d)
                            + texture(Sampler0, texCoord - d)) * w;
                    wSum += 2.0 * w;
                }
                fragColor = sum / wSum;
            }
            """;

    private static final String COMPOSITE_VERTEX = """
            #version 150
            in vec3 Position;
            in vec2 UV0;
            uniform mat4 ProjMat;
            uniform mat4 ModelViewMat;
            out vec2 texCoord;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                texCoord = UV0;
            }
            """;

    private static final String COMPOSITE_FRAGMENT = """
            #version 150
            uniform sampler2D Sampler0;
            uniform vec2 location, rectSize, screenSize;
            uniform float radius, opacity;
            uniform float gameTime;
            in vec2 texCoord;
            out vec4 fragColor;

            float roundSDF(vec2 p, vec2 b, float r) {
                return length(max(abs(p) - b, 0.0)) - r;
            }

            float hash12(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            void main() {
                vec2 screenUv = gl_FragCoord.xy / screenSize;
                vec3 blurred = texture(Sampler0, screenUv).rgb;
                const vec3 lumaWeights = vec3(0.2126, 0.7152, 0.0722);
                float luma = dot(blurred, lumaWeights);
                blurred = mix(vec3(luma), blurred, 0.85);
                blurred = mix(blurred, blurred * vec3(0.72, 0.78, 1.0), 0.35);
                blurred *= 0.86;
                vec2 vignetteUv = texCoord - 0.5;
                float vignette = 1.0 - 0.3 * smoothstep(0.0, 0.75,
                        dot(vignetteUv, vignetteUv) * 2.0);
                blurred *= vignette;
                float noise = hash12(gl_FragCoord.xy + fract(gameTime * 1024.0) * 137.0);
                blurred += (noise - 0.5) * 0.018;
                vec2 rectHalf = rectSize * .5;
                float distance = roundSDF(rectHalf - (texCoord * rectSize), rectHalf - radius - 1., radius);
                float antialias = clamp(fwidth(distance) * 0.85, 0.5, 1.0);
                float mask = 1. - smoothstep(0.0, antialias, distance);
                fragColor = vec4(blurred, mask * opacity);
            }
            """;

    private static ShaderProgram gaussianProgram;
    private static ShaderProgram compositeProgram;
    private static ShaderProgram liquidGlassProgram;
    private static int linearSampler = -1;
    private static boolean programsFailed;
    private static int textureA = -1;
    private static int textureB = -1;
    private static int fboA = -1;
    private static int fboB = -1;
    private static int lowWidth;
    private static int lowHeight;
    private static long preparedFrame = -1L;
    private static float preparedStrength = -1.0F;
    private static boolean ready;
    private static boolean diagnosticsLogged;

    private BlurRenderer() {
    }

    /** Draws a rounded blur rect in HUD ortho coordinates (deferred phase only). */
    public static void drawBlur(float x, float y, float width, float height, float radius) {
        drawBlur(x, y, width, height, radius, 1.0F);
    }

    public static void drawBlur(float x, float y, float width, float height, float radius, float opacity) {
        drawBlur(x, y, width, height, radius, opacity, getBlurStrength());
    }

    /** Draws blur with a per-call strength multiplier. 1.0 is the default strength. */
    public static void drawBlur(
            float x,
            float y,
            float width,
            float height,
            float radius,
            float opacity,
            float blurStrength
    ) {
        if (opacity <= 0.0F) {
            return;
        }
        withMainFramebuffer(target -> {
            prepareIfNeeded(blurStrength, target);
            if (!ready || compositeProgram == null || !compositeProgram.isLinked()) {
                return;
            }

            Window window = MinecraftClient.getInstance().getWindow();
            float scale = ScreenScale.getScale();
            float maxRadius = Math.min(Math.abs(width), Math.abs(height)) * 0.5F;
            compositeProgram.bind();
            compositeProgram.setVec2("location", x * scale, window.getFramebufferHeight() - height * scale - y * scale);
            compositeProgram.setVec2("rectSize", width * scale, height * scale);
            compositeProgram.setFloat("radius", Math.clamp(radius, 0.0F, maxRadius) * scale);
            compositeProgram.setFloat("opacity", Math.clamp(opacity, 0.0F, 1.0F));
            compositeProgram.setFloat("GameTime", (System.nanoTime() & 0xFFFFFFL) / 1_000_000_000.0F);
            compositeProgram.setVec4("Tint", 0.72F, 0.78F, 1.0F, 0.35F);

            compositeProgram.setFloat("Brightness", 0.86F);
            compositeProgram.setFloat("Saturation", 0.85F);
            compositeProgram.setFloat("Vignette", 0.3F);
            compositeProgram.setFloat("Grain", 0.018F);
            compositeProgram.setVec2("screenSize", window.getFramebufferWidth(), window.getFramebufferHeight());
            compositeProgram.setInt("Sampler0", 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            int previousSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL33.glBindSampler(0, linearSampler);
            GlStateManager._bindTexture(textureA);
            try {
                BlendUtil.runBlended(() -> ShaderProgram.drawQuad(x, y, width, height));
            } finally {
                compositeProgram.unbind();
                GlStateManager._bindTexture(0);
                GL33.glBindSampler(0, previousSampler);
            }
        });
    }

    /**
     * Draws the cached background blur through the liquid-glass shader.
     * All shape values are supplied in GUI units and converted to framebuffer
     * pixels here, just like the regular rounded-rect renderer.
     */
    public static void drawLiquidGlass(
            float x,
            float y,
            float width,
            float height,
            float radius,
            java.awt.Color tint,
            float opacity,
            float distortion,
            float edgeLight,
            float shine
    ) {
        drawLiquidGlass(
                x, y, width, height, radius, tint, opacity,
                distortion, edgeLight, shine, getBlurStrength()
        );
    }

    /** Draws liquid glass using an explicit background blur strength. */
    public static void drawLiquidGlass(
            float x,
            float y,
            float width,
            float height,
            float radius,
            java.awt.Color tint,
            float opacity,
            float distortion,
            float edgeLight,
            float shine,
            float blurStrength
    ) {
        drawLiquidGlass(x, y, width, height, radius, tint, opacity,
                distortion, edgeLight, shine, blurStrength,
                distortion, true);
    }

    public static void drawLiquidGlass(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            java.awt.Color tint,
            float opacity,
            float distortion,
            float edgeLight,
            float shine,
            float blurStrength,
            float innerDistortion,
            boolean innerBlur
    ) {
        drawLiquidGlass(
                context != null ? dev.fuga.fluxvisuals.render.Render2D.extractModelView(context) : null,
                x, y, width, height, radius, tint, opacity, distortion, edgeLight, shine, blurStrength, innerDistortion, innerBlur
        );
    }

    public static void drawLiquidGlass(
            float x,
            float y,
            float width,
            float height,
            float radius,
            java.awt.Color tint,
            float opacity,
            float distortion,
            float edgeLight,
            float shine,
            float blurStrength,
            float innerDistortion,
            boolean innerBlur
    ) {
        drawLiquidGlass(
                (Matrix4f) null,
                x, y, width, height, radius, tint, opacity, distortion, edgeLight, shine, blurStrength, innerDistortion, innerBlur
        );
    }

    public static void drawLiquidGlass(
            org.joml.Matrix4f modelView,
            float x,
            float y,
            float width,
            float height,
            float radius,
            java.awt.Color tint,
            float opacity,
            float distortion,
            float edgeLight,
            float shine,
            float blurStrength,
            float innerDistortion,
            boolean innerBlur
    ) {
        if (opacity <= 0.0F) {
            return;
        }
        withMainFramebuffer(target -> {
            prepareIfNeeded(blurStrength, target);
            if (!ready || liquidGlassProgram == null || !liquidGlassProgram.isLinked()) {
                return;
            }

            Window window = MinecraftClient.getInstance().getWindow();
            float scale = ScreenScale.getScale();
            float maxRadius = Math.min(Math.abs(width), Math.abs(height)) * 0.5F;
            float clampedRadius = Math.clamp(radius, 0.0F, maxRadius);

            liquidGlassProgram.bind(modelView);
            liquidGlassProgram.setVec2("rectSize", Math.abs(width) * scale, Math.abs(height) * scale);
            liquidGlassProgram.setVec2("screenSize", window.getFramebufferWidth(), window.getFramebufferHeight());
            liquidGlassProgram.setFloat("radius", clampedRadius * scale);
            liquidGlassProgram.setFloat("opacity", Math.clamp(opacity, 0.0F, 1.0F));
            liquidGlassProgram.setFloat("distortion", Math.max(0.0F, distortion) * scale);
            liquidGlassProgram.setFloat("innerDistortion", Math.max(0.0F, innerDistortion) * scale);
            liquidGlassProgram.setFloat("innerBlur", innerBlur ? 1.0F : 0.0F);
            liquidGlassProgram.setFloat("edgeLight", Math.max(0.0F, edgeLight) * scale);
            liquidGlassProgram.setFloat("shine", Math.max(0.0F, shine));
            liquidGlassProgram.setColor("tintColor", tint);
            liquidGlassProgram.setInt("Sampler0", 0);

            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            int previousSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL33.glBindSampler(0, linearSampler);
            GlStateManager._bindTexture(textureA);
            try {
                BlendUtil.runBlended(() -> ShaderProgram.drawQuad(x, y, width, height));
            } finally {
                liquidGlassProgram.unbind();
                GlStateManager._bindTexture(0);
                GL33.glBindSampler(0, previousSampler);
            }
        });
    }

    /**
     * Rebuilds the blur snapshot from the current main framebuffer contents.
     * This is needed for glass controls drawn above other deferred GUI elements
     * (for example a slider knob above its already rendered track).
     */
    public static void refreshBackgroundBlur(float blurStrength) {
        preparedFrame = Long.MIN_VALUE;
        preparedStrength = -1.0F;
        ready = false;
    }

    private record MainFramebuffer(int fbo, int width, int height) {
    }

    /**
     * Binds the main framebuffer with sanitized GL state and GUI ortho
     * matrices, runs the action, then restores the previous state. This keeps
     * the renderer self-sufficient during the vanilla GUI record window,
     * where the bound framebuffer, viewport and matrices are unspecified.
     */
    private static void withMainFramebuffer(Consumer<MainFramebuffer> action) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null
                || !(framebuffer.getColorAttachment() instanceof GlTexture colorTexture)
                || !(RenderSystem.getDevice() instanceof GlBackend backend)) {
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

        GlStateGuard glGuard = GlStateGuard.capture();
        int previousFbo = GlStateManager.getFrameBuffer(GL30.GL_FRAMEBUFFER);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] previousScissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, previousScissorBox);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        GlStateManager._viewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        ScreenScale.begin(width / (double) scaledWidth);
        try {
            action.accept(new MainFramebuffer(mainFbo, width, height));
        } finally {
            ScreenScale.end();
            if (scissorWasEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(previousScissorBox[0], previousScissorBox[1],
                        previousScissorBox[2], previousScissorBox[3]);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GlStateManager._viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
            glGuard.restore();
        }
    }

    private static void prepareIfNeeded(float requestedStrength, MainFramebuffer target) {
        long frame = RenderPhase.getFrameIndex();
        float blurStrength = Math.clamp(requestedStrength, 0.0F, 4.0F);
        if (frame == preparedFrame && Float.compare(blurStrength, preparedStrength) == 0) {
            return;
        }
        preparedFrame = frame;
        preparedStrength = blurStrength;
        ready = false;

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (framebuffer == null || !(framebuffer.getColorAttachment() instanceof GlTexture colorTexture)) {
            logOnce("main framebuffer color attachment is not a GlTexture");
            return;
        }
        int sourceTexture = colorTexture.getGlId();

        ensurePrograms();
        if (programsFailed) {
            return;
        }

        int fbWidth = target.width();
        int fbHeight = target.height();
        ensureTargets(Math.max(1, fbWidth / DOWNSCALE), Math.max(1, fbHeight / DOWNSCALE));

        GL11.glGetError(); // clear stale errors so diagnostics below are ours

        // prepareIfNeeded runs inside withMainFramebuffer: blend state must be
        // restored, otherwise vanilla draws later in the frame lose blending
        // (item icons disappear while text survives).
        GlStateGuard blendGuard = GlStateGuard.capture();
        try {
            GlStateManager._disableBlend();
            GlStateManager._viewport(0, 0, lowWidth, lowHeight);
            gaussianProgram.use();
            gaussianProgram.setInt("Sampler0", 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            int previousSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL33.glBindSampler(0, linearSampler);

            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    float radius = (i < 2 ? 6.0F : 8.0F) * blurStrength;
                    float spread = i < 2 ? 1.0F : 3.0F;
                    gaussianProgram.setFloat("Radius", Math.max(1.0F, radius));
                    gaussianProgram.setFloat("Spread", spread);
                    // Horizontal: first iteration samples the full-res main color texture
                    // (implicit downsample), later ones sample the low-res result.
                    GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboB);
                    GlStateManager._bindTexture(i == 0 ? sourceTexture : textureA);
                    gaussianProgram.setVec2("BlurDir", 1.0F / lowWidth, 0.0F);
                    ShaderProgram.drawQuad(-1.0F, -1.0F, 2.0F, 2.0F);

                    GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboA);
                    GlStateManager._bindTexture(textureB);
                    gaussianProgram.setVec2("BlurDir", 0.0F, 1.0F / lowHeight);
                    ShaderProgram.drawQuad(-1.0F, -1.0F, 2.0F, 2.0F);
                }
            } finally {
                gaussianProgram.unbind();
                GlStateManager._bindTexture(0);
                GL33.glBindSampler(0, previousSampler);
            }
        } finally {
            blendGuard.restore();
        }

        if (!diagnosticsLogged) {
            diagnosticsLogged = true;
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            int error = GL11.glGetError();
            LOGGER.info(
                    "[Blur] source={} fboA={} fboB={} low={}x{} status={} glError={}",
                    sourceTexture, fboA, fboB, lowWidth, lowHeight,
                    status == GL30.GL_FRAMEBUFFER_COMPLETE ? "COMPLETE" : "0x" + Integer.toHexString(status),
                    error == 0 ? "none" : "0x" + Integer.toHexString(error)
            );
        }

        // Restore the main target for the composite draw.
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fbo());
        GlStateManager._viewport(0, 0, fbWidth, fbHeight);
        ready = true;
    }

    private static float getBlurStrength() {
        return 1.0F;
    }
    private static boolean errorLogged;

    private static void logOnce(String message) {
        if (!errorLogged) {
            errorLogged = true;
            LOGGER.error("[Blur] {}", message);
        }
    }

    private static void ensurePrograms() {
        if (gaussianProgram != null || programsFailed) {
            return;
        }
        ShaderProgram gaussian = new ShaderProgram();
        gaussian.attachSource(FULLSCREEN_VERTEX, 35633);
        gaussian.attachSource(loadPostShader("gaussian_blur.fsh", true), 35632);
        gaussian.link();

        ShaderProgram composite = new ShaderProgram();
        composite.attachSource(COMPOSITE_VERTEX, 35633);
        composite.attachSource(loadPostShader("blur_composite.fsh", false), 35632);
        composite.link();

        if (!gaussian.isLinked() || !composite.isLinked()) {
            programsFailed = true;
            LOGGER.error("[Blur] shader programs failed to link (gaussian={}, composite={})",
                    gaussian.isLinked(), composite.isLinked());
            return;
        }
        gaussianProgram = gaussian;
        compositeProgram = composite;

        // The liquid-glass program is optional: it must never disable the
        // regular blur path when its resources fail to load or compile.
        ShaderProgram liquidGlass = ShaderBuilder.create()
                .attach("vertex/passthrough.vsh", 35633)
                .attach("fragment/liquid_glass.fsh", 35632)
                .link()
                .build();
        if (liquidGlass.isLinked()) {
            liquidGlassProgram = liquidGlass;
        } else {
            LOGGER.error("[Blur] liquid glass shader failed to link");
        }

        linearSampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(linearSampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL33.glSamplerParameteri(linearSampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL33.glSamplerParameteri(linearSampler, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(linearSampler, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
    }

    private static String loadPostShader(String name, boolean gaussian) {
        String path = "/assets/fluxvisuals/shaders/post/" + name;
        try (InputStream stream = BlurRenderer.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing post shader resource " + path);
            }
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("#moj_import <minecraft:globals.glsl>", "")
                    .replace("uniform sampler2D InSampler;", "uniform sampler2D Sampler0;")
                    .replace("InSampler", "Sampler0");
            if (gaussian) {
                return source
                        .replaceFirst("(?s)layout\\(std140\\) uniform BlurConfig \\{.*?};", """
                                uniform vec2 BlurDir;
                                uniform float Radius;
                                uniform float Spread;""")
                        .replace("in vec2 sampleStep;", "")
                        .replace("vec2 d = sampleStep * offset;", "vec2 d = BlurDir * Spread * offset;");
            }
            source = source.replaceFirst("(?s)layout\\(std140\\) uniform BlurStyle \\{.*?};", """
                    uniform vec4 Tint;
                    uniform float Brightness;
                    uniform float Saturation;
                    uniform float Vignette;
                    uniform float Grain;
                    uniform float GameTime;
                    uniform vec2 screenSize;
                    uniform vec2 rectSize;
                    uniform float radius;
                    uniform float opacity;""");
            source = source.replace("vec3 color = texture(Sampler0, texCoord).rgb;",
                    "vec3 color = texture(Sampler0, gl_FragCoord.xy / screenSize).rgb;");
            source = source.replace("fragColor = vec4(color, 1.0);", """
                    vec2 halfSize = rectSize * 0.5;
                        vec2 q = abs(texCoord * rectSize - halfSize) - (halfSize - radius);
                        float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
                        float aa = clamp(fwidth(dist), 0.5, 1.5);
                        float mask = 1.0 - smoothstep(0.0, aa, dist);
                        fragColor = vec4(color, mask * opacity);""");
            return source;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read post shader " + path, exception);
        }
    }

    private static void ensureTargets(int width, int height) {
        if (width == lowWidth && height == lowHeight && textureA != -1) {
            return;
        }
        deleteTargets();
        lowWidth = width;
        lowHeight = height;
        textureA = createTexture(width, height);
        textureB = createTexture(width, height);
        fboA = createFbo(textureA);
        fboB = createFbo(textureB);
    }

    private static int createTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GlStateManager._bindTexture(texture);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_RGBA16F,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL30.GL_HALF_FLOAT,
                (java.nio.ByteBuffer) null
        );
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        return texture;
    }

    private static int createFbo(int texture) {
        int previousFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.error("[Blur] framebuffer incomplete: 0x{}", Integer.toHexString(status));
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        return fbo;
    }

    private static void deleteTargets() {
        if (textureA != -1) {
            GL11.glDeleteTextures(textureA);
            GL11.glDeleteTextures(textureB);
            GL30.glDeleteFramebuffers(fboA);
            GL30.glDeleteFramebuffers(fboB);
            textureA = -1;
            textureB = -1;
            fboA = -1;
            fboB = -1;
        }
    }
}

