package luna.ware.util.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import luna.ware.util.render.shader.ShaderProgram;
import luna.ware.util.render.shader.ShaderBuilder;

/**
 * Fast background blur for HUD/GUI rects.
 *
 * Optimization strategy (near-zero FPS cost):
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
    private static final float SPREAD = 2.25F;

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
            in vec2 texCoord;
            out vec4 fragColor;
            void main() {
                vec4 color = texture(Sampler0, texCoord) * 0.2270270270;
                vec2 off1 = direction * 1.3846153846;
                vec2 off2 = direction * 3.2307692308;
                color += texture(Sampler0, texCoord + off1) * 0.3162162162;
                color += texture(Sampler0, texCoord - off1) * 0.3162162162;
                color += texture(Sampler0, texCoord + off2) * 0.0702702703;
                color += texture(Sampler0, texCoord - off2) * 0.0702702703;
                fragColor = vec4(color.rgb, 1.0);
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
            in vec2 texCoord;
            out vec4 fragColor;

            float roundSDF(vec2 p, vec2 b, float r) {
                return length(max(abs(p) - b, 0.0)) - r;
            }

            void main() {
                vec2 screenUv = gl_FragCoord.xy / screenSize;
                vec2 texel = 1.0 / screenSize;
                vec3 blurred = texture(Sampler0, screenUv).rgb * 0.26;
                blurred += texture(Sampler0, screenUv + vec2(texel.x, 0.0)).rgb * 0.09;
                blurred += texture(Sampler0, screenUv - vec2(texel.x, 0.0)).rgb * 0.09;
                blurred += texture(Sampler0, screenUv + vec2(0.0, texel.y)).rgb * 0.09;
                blurred += texture(Sampler0, screenUv - vec2(0.0, texel.y)).rgb * 0.09;
                blurred += texture(Sampler0, screenUv + vec2(texel.x, texel.y)).rgb * 0.055;
                blurred += texture(Sampler0, screenUv + vec2(texel.x, -texel.y)).rgb * 0.055;
                blurred += texture(Sampler0, screenUv + vec2(-texel.x, texel.y)).rgb * 0.055;
                blurred += texture(Sampler0, screenUv - vec2(texel.x, texel.y)).rgb * 0.055;
                blurred += texture(Sampler0, screenUv + vec2(2.0 * texel.x, 0.0)).rgb * 0.04;
                blurred += texture(Sampler0, screenUv - vec2(2.0 * texel.x, 0.0)).rgb * 0.04;
                blurred += texture(Sampler0, screenUv + vec2(0.0, 2.0 * texel.y)).rgb * 0.04;
                blurred += texture(Sampler0, screenUv - vec2(0.0, 2.0 * texel.y)).rgb * 0.04;
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
        prepareIfNeeded(blurStrength);
        if (!ready || compositeProgram == null || !compositeProgram.isLinked()) {
            return;
        }

        Window window = MinecraftClient.getInstance().getWindow();
        float scale = ScreenScale.getScale();
        float maxRadius = Math.min(Math.abs(width), Math.abs(height)) * 0.5F;
        compositeProgram.bind();
        compositeProgram.setVec2("location", x * scale, window.getHeight() - height * scale - y * scale);
        compositeProgram.setVec2("rectSize", width * scale, height * scale);
        compositeProgram.setFloat("radius", Math.clamp(radius, 0.0F, maxRadius) * scale);
        compositeProgram.setFloat("opacity", Math.clamp(opacity, 0.0F, 1.0F));
        compositeProgram.setVec2("screenSize", window.getFramebufferWidth(), window.getFramebufferHeight());
        compositeProgram.setInt("Sampler0", 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previousSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GL33.glBindSampler(0, linearSampler);
        GlStateManager._bindTexture(textureA);
        BlendUtil.runBlended(() -> ShaderProgram.drawQuad(x, y, width, height));
        compositeProgram.unbind();
        GlStateManager._bindTexture(0);
        GL33.glBindSampler(0, previousSampler);
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
        prepareIfNeeded(blurStrength);
        if (!ready || liquidGlassProgram == null || !liquidGlassProgram.isLinked()) {
            return;
        }

        Window window = MinecraftClient.getInstance().getWindow();
        float scale = ScreenScale.getScale();
        float maxRadius = Math.min(Math.abs(width), Math.abs(height)) * 0.5F;
        float clampedRadius = Math.clamp(radius, 0.0F, maxRadius);

        liquidGlassProgram.bind();
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
        BlendUtil.runBlended(() -> ShaderProgram.drawQuad(x, y, width, height));
        liquidGlassProgram.unbind();
        GlStateManager._bindTexture(0);
        GL33.glBindSampler(0, previousSampler);
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
        prepareIfNeeded(blurStrength);
    }

    private static void prepareIfNeeded(float requestedStrength) {
        long frame = RenderPhase.getFrameIndex();
        float blurStrength = Math.clamp(requestedStrength, 0.0F, 4.0F);
        if (frame == preparedFrame && Float.compare(blurStrength, preparedStrength) == 0) {
            return;
        }
        preparedFrame = frame;
        preparedStrength = blurStrength;
        ready = false;

        int mainFbo = RenderPhase.getMainFbo();
        if (mainFbo < 0) {
            return;
        }

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

        Window window = MinecraftClient.getInstance().getWindow();
        int fbWidth = window.getFramebufferWidth();
        int fbHeight = window.getFramebufferHeight();
        if (fbWidth <= 0 || fbHeight <= 0) {
            return;
        }
        ensureTargets(Math.max(1, fbWidth / DOWNSCALE), Math.max(1, fbHeight / DOWNSCALE));

        GL11.glGetError(); // clear stale errors so diagnostics below are ours

        GlStateManager._disableBlend();
        GlStateManager._viewport(0, 0, lowWidth, lowHeight);
        gaussianProgram.use();
        gaussianProgram.setInt("Sampler0", 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        // 1.21.9+ uses GL sampler objects: texture-object filter params are left at GL
        // defaults (mipmap filtering, no mipmaps -> incomplete -> solid black). Binding
        // our own sampler makes every source texture sample correctly.
        int previousSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GL33.glBindSampler(0, linearSampler);

        for (int i = 0; i < ITERATIONS; i++) {
            float spread = (SPREAD + i) * blurStrength;
            // Horizontal: first iteration samples the full-res main color texture
            // (implicit downsample), later ones sample the low-res result.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboB);
            GlStateManager._bindTexture(i == 0 ? sourceTexture : textureA);
            gaussianProgram.setVec2("direction", spread / lowWidth, 0.0F);
            ShaderProgram.drawQuad(-1.0F, -1.0F, 2.0F, 2.0F);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboA);
            GlStateManager._bindTexture(textureB);
            gaussianProgram.setVec2("direction", 0.0F, spread / lowHeight);
            ShaderProgram.drawQuad(-1.0F, -1.0F, 2.0F, 2.0F);
        }

        gaussianProgram.unbind();
        GlStateManager._bindTexture(0);
        GL33.glBindSampler(0, previousSampler);

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

        // Restore the main target for the rest of the deferred pass.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
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
        gaussian.attachSource(GAUSSIAN_FRAGMENT, 35632);
        gaussian.link();

        ShaderProgram composite = new ShaderProgram();
        composite.attachSource(COMPOSITE_VERTEX, 35633);
        composite.attachSource(COMPOSITE_FRAGMENT, 35632);
        composite.link();

        ShaderProgram liquidGlass = ShaderBuilder.create()
                .attach("vertex/passthrough.vsh", 35633)
                .attach("fragment/liquid_glass.fsh", 35632)
                .link()
                .build();

        if (!gaussian.isLinked() || !composite.isLinked() || !liquidGlass.isLinked()) {
            programsFailed = true;
            LOGGER.error("[Blur] shader programs failed to link (gaussian={}, composite={}, liquidGlass={})",
                    gaussian.isLinked(), composite.isLinked(), liquidGlass.isLinked());
            return;
        }
        gaussianProgram = gaussian;
        compositeProgram = composite;
        liquidGlassProgram = liquidGlass;

        linearSampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(linearSampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL33.glSamplerParameteri(linearSampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL33.glSamplerParameteri(linearSampler, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(linearSampler, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
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
