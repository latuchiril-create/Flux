package dev.fuga.fluxvisuals.render.shader;

import dev.fuga.fluxvisuals.render.util.RenderColor;
import java.awt.Color;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance shader program manager and uniform cache for Render2D.
 */
public final class Render2DShader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Render2D");
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final float[] TEMP_COLOR = new float[4];

    private static final String DEFAULT_PASSTHROUGH_VSH = """
            #version 150
            in vec3 Position;
            in vec2 UV0;
            uniform mat4 ProjMat;
            uniform mat4 ModelViewMat;
            out vec2 localPos;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                localPos = UV0;
            }
            """;

    private static final String DEFAULT_ROUNDED_RECT_FSH = """
            #version 150
            uniform vec2 size;
            uniform vec4 radius;
            uniform vec4 color1;
            uniform vec4 color2;
            uniform vec4 color3;
            uniform vec4 color4;
            in vec2 localPos;
            out vec4 fragColor;
            float selectRadius(vec2 p, vec4 r) {
                vec2 pair = (p.x > 0.0) ? r.yz : r.xw;
                return (p.y > 0.0) ? pair.y : pair.x;
            }
            float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
                float rad = selectRadius(p, r);
                vec2 q = abs(p) - b + rad;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rad;
            }
            vec4 bilinearColor(vec2 uv) {
                vec4 top = mix(color1, color2, clamp(uv.x, 0.0, 1.0));
                vec4 bottom = mix(color4, color3, clamp(uv.x, 0.0, 1.0));
                return mix(top, bottom, clamp(uv.y, 0.0, 1.0));
            }
            void main() {
                vec2 halfSize = size * 0.5;
                vec2 p = localPos - halfSize;
                float maxRad = min(halfSize.x, halfSize.y);
                vec4 clampedRadius = clamp(radius, vec4(0.0), vec4(maxRad));
                float dist = roundedBoxSDF(p, halfSize, clampedRadius);
                float aa = length(vec2(dFdx(dist), dFdy(dist)));
                if (aa <= 0.0) aa = fwidth(dist);
                aa = max(aa, 0.35);
                float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);
                alpha = smoothstep(0.0, 1.0, alpha);
                if (alpha <= 0.001) discard;
                vec2 uv = localPos / size;
                vec4 col = bilinearColor(uv);
                fragColor = vec4(col.rgb, col.a * alpha);
            }
            """;

    private static final String DEFAULT_ROUNDED_OUTLINE_FSH = """
            #version 150
            uniform vec2 size;
            uniform vec4 radius;
            uniform float thickness;
            uniform vec4 color1;
            uniform vec4 color2;
            uniform vec4 color3;
            uniform vec4 color4;
            in vec2 localPos;
            out vec4 fragColor;
            float selectRadius(vec2 p, vec4 r) {
                vec2 pair = (p.x > 0.0) ? r.yz : r.xw;
                return (p.y > 0.0) ? pair.y : pair.x;
            }
            float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
                float rad = selectRadius(p, r);
                vec2 q = abs(p) - b + rad;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rad;
            }
            vec4 bilinearColor(vec2 uv) {
                vec4 top = mix(color1, color2, clamp(uv.x, 0.0, 1.0));
                vec4 bottom = mix(color4, color3, clamp(uv.x, 0.0, 1.0));
                return mix(top, bottom, clamp(uv.y, 0.0, 1.0));
            }
            void main() {
                vec2 halfSize = size * 0.5;
                vec2 p = localPos - halfSize;
                float maxRad = min(halfSize.x, halfSize.y);
                vec4 clampedRadius = clamp(radius, vec4(0.0), vec4(maxRad));
                float dist = roundedBoxSDF(p, halfSize, clampedRadius);
                float outlineDist = abs(dist + thickness * 0.5) - thickness * 0.5;
                float aa = length(vec2(dFdx(dist), dFdy(dist)));
                if (aa <= 0.0) aa = fwidth(dist);
                aa = max(aa, 0.35);
                float alpha = clamp(0.5 - outlineDist / aa, 0.0, 1.0);
                alpha = smoothstep(0.0, 1.0, alpha);
                if (alpha <= 0.001) discard;
                vec2 uv = localPos / size;
                vec4 col = bilinearColor(uv);
                fragColor = vec4(col.rgb, col.a * alpha);
            }
            """;

    private static final String DEFAULT_ROUNDED_SHADOW_FSH = """
            #version 150
            uniform vec2 size;
            uniform vec4 radius;
            uniform float shadowSize;
            uniform float shadowSpread;
            uniform vec4 shadowColor;
            in vec2 localPos;
            out vec4 fragColor;
            float selectRadius(vec2 p, vec4 r) {
                vec2 pair = (p.x > 0.0) ? r.yz : r.xw;
                return (p.y > 0.0) ? pair.y : pair.x;
            }
            float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
                float rad = selectRadius(p, r);
                vec2 q = abs(p) - b + rad;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rad;
            }
            void main() {
                vec2 halfSize = size * 0.5;
                vec2 p = localPos - halfSize;
                float maxRad = min(halfSize.x, halfSize.y);
                vec4 clampedRadius = clamp(radius, vec4(0.0), vec4(maxRad));
                float dist = roundedBoxSDF(p, halfSize + shadowSpread, clampedRadius);
                float sigma = max(0.5, shadowSize * 0.45);
                float factor = max(0.0, dist) / sigma;
                float shadowAlpha = exp(-0.5 * factor * factor);
                if (dist <= 0.0) shadowAlpha = 1.0;
                if (shadowAlpha <= 0.002) discard;
                fragColor = shadowColor * vec4(1.0, 1.0, 1.0, shadowAlpha);
            }
            """;

    private static final String DEFAULT_ROUNDED_TEXTURE_FSH = """
            #version 150
            uniform sampler2D Sampler0;
            uniform vec2 size;
            uniform vec4 radius;
            uniform vec4 colorModulator;
            in vec2 localPos;
            out vec4 fragColor;
            float selectRadius(vec2 p, vec4 r) {
                vec2 pair = (p.x > 0.0) ? r.yz : r.xw;
                return (p.y > 0.0) ? pair.y : pair.x;
            }
            float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
                float rad = selectRadius(p, r);
                vec2 q = abs(p) - b + rad;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rad;
            }
            void main() {
                vec2 uv = clamp(localPos / size, 0.0, 1.0);
                vec4 texColor = texture(Sampler0, uv);
                if (radius.x <= 0.001 && radius.y <= 0.001 && radius.z <= 0.001 && radius.w <= 0.001) {
                    fragColor = texColor * colorModulator;
                    return;
                }
                vec2 halfSize = size * 0.5;
                vec2 p = localPos - halfSize;
                float maxRad = min(halfSize.x, halfSize.y);
                vec4 clampedRadius = clamp(radius, vec4(0.0), vec4(maxRad));
                float dist = roundedBoxSDF(p, halfSize, clampedRadius);
                float aa = length(vec2(dFdx(dist), dFdy(dist)));
                if (aa <= 0.0) aa = fwidth(dist);
                aa = max(aa, 0.35);
                float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);
                alpha = smoothstep(0.0, 1.0, alpha);
                if (alpha <= 0.001) discard;
                fragColor = texColor * colorModulator * vec4(1.0, 1.0, 1.0, alpha);
            }
            """;

    private static final String DEFAULT_CIRCLE_FSH = """
            #version 150
            uniform vec2 size;
            uniform float radius;
            uniform float innerRadius;
            uniform vec4 innerColor;
            uniform vec4 outerColor;
            in vec2 localPos;
            out vec4 fragColor;
            void main() {
                vec2 halfSize = size * 0.5;
                vec2 p = localPos - halfSize;
                float dist = length(p);
                float outerDist = dist - radius;
                float innerDist = (innerRadius > 0.0) ? (innerRadius - dist) : -1000.0;
                float shapeDist = max(outerDist, innerDist);
                float aa = length(vec2(dFdx(dist), dFdy(dist)));
                if (aa <= 0.0) aa = fwidth(dist);
                aa = max(aa, 0.35);
                float alpha = clamp(0.5 - shapeDist / aa, 0.0, 1.0);
                alpha = smoothstep(0.0, 1.0, alpha);
                if (alpha <= 0.001) discard;
                float t = clamp(dist / max(radius, 0.001), 0.0, 1.0);
                vec4 col = mix(innerColor, outerColor, t);
                fragColor = vec4(col.rgb, col.a * alpha);
            }
            """;

    private static final String DEFAULT_MSDF_VSH = """
            #version 150
            in vec3 Position;
            in vec2 UV0;
            in vec4 Color;
            uniform mat4 ProjMat;
            uniform mat4 ModelViewMat;
            out vec2 TexCoord;
            out vec4 FragColor;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                TexCoord = UV0;
                FragColor = Color;
            }
            """;

    private static final String DEFAULT_MSDF_FSH = """
            #version 150
            uniform sampler2D Sampler0;
            uniform float Range;
            uniform float Thickness;
            uniform float Smoothness;
            uniform bool Outline;
            uniform float OutlineThickness;
            uniform vec4 OutlineColor;
            in vec2 TexCoord;
            in vec4 FragColor;
            out vec4 OutColor;
            float median(vec3 color) {
                return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
            }
            void main() {
                vec3 msdfSample = texture(Sampler0, TexCoord).rgb;
                float sd = median(msdfSample) - 0.5 + Thickness;
                vec2 unitRange = vec2(Range) / vec2(textureSize(Sampler0, 0));
                vec2 screenTexSize = vec2(1.0) / max(fwidth(TexCoord), vec2(1e-5));
                float screenPxDistance = sd * dot(unitRange, screenTexSize);
                float opacity = clamp(screenPxDistance + 0.5 + Smoothness, 0.0, 1.0);
                opacity = smoothstep(0.0, 1.0, opacity);
                if (opacity <= 0.001) discard;
                vec4 finalColor = vec4(FragColor.rgb, FragColor.a * opacity);
                if (Outline) {
                    float outlineSd = sd + OutlineThickness;
                    float outlinePxDistance = outlineSd * dot(unitRange, screenTexSize);
                    float outlineOpacity = clamp(outlinePxDistance + 0.5 + Smoothness, 0.0, 1.0);
                    outlineOpacity = smoothstep(0.0, 1.0, outlineOpacity);
                    finalColor = mix(OutlineColor, FragColor, opacity);
                    finalColor.a = FragColor.a * outlineOpacity;
                }
                OutColor = finalColor;
            }
            """;

    public static final Render2DShader ROUNDED_RECT = new Render2DShader(
            "passthrough.vsh",
            "rounded_rect.fsh",
            DEFAULT_PASSTHROUGH_VSH,
            DEFAULT_ROUNDED_RECT_FSH
    );

    public static final Render2DShader ROUNDED_OUTLINE = new Render2DShader(
            "passthrough.vsh",
            "rounded_outline.fsh",
            DEFAULT_PASSTHROUGH_VSH,
            DEFAULT_ROUNDED_OUTLINE_FSH
    );

    public static final Render2DShader ROUNDED_SHADOW = new Render2DShader(
            "passthrough.vsh",
            "rounded_shadow.fsh",
            DEFAULT_PASSTHROUGH_VSH,
            DEFAULT_ROUNDED_SHADOW_FSH
    );

    public static final Render2DShader ROUNDED_TEXTURE = new Render2DShader(
            "passthrough.vsh",
            "rounded_texture.fsh",
            DEFAULT_PASSTHROUGH_VSH,
            DEFAULT_ROUNDED_TEXTURE_FSH
    );

    public static final Render2DShader CIRCLE = new Render2DShader(
            "passthrough.vsh",
            "circle.fsh",
            DEFAULT_PASSTHROUGH_VSH,
            DEFAULT_CIRCLE_FSH
    );

    public static final Render2DShader MSDF_FONT = new Render2DShader(
            "msdf_font.vsh",
            "msdf_font.fsh",
            DEFAULT_MSDF_VSH,
            DEFAULT_MSDF_FSH
    );

    private final String vertexName;
    private final String fragmentName;
    private final String fallbackVertex;
    private final String fallbackFragment;

    private int programId = 0;
    private boolean linked = false;
    private final Map<String, Integer> uniformMap = new HashMap<>();

    public Render2DShader(String vertexName, String fragmentName, String fallbackVertex, String fallbackFragment) {
        this.vertexName = vertexName;
        this.fragmentName = fragmentName;
        this.fallbackVertex = fallbackVertex;
        this.fallbackFragment = fallbackFragment;
    }

    public void init() {
        if (this.linked && this.programId != 0 && GL20.glIsProgram(this.programId)) {
            return;
        }

        this.programId = GL20.glCreateProgram();

        String vertSource = loadSource(vertexName, fallbackVertex);
        String fragSource = loadSource(fragmentName, fallbackFragment);

        int vertShader = compileShader(vertSource, GL20.GL_VERTEX_SHADER);
        int fragShader = compileShader(fragSource, GL20.GL_FRAGMENT_SHADER);

        if (vertShader == 0 || fragShader == 0) {
            LOGGER.error("Failed to compile shaders for {} / {}", vertexName, fragmentName);
            return;
        }

        GL20.glAttachShader(this.programId, vertShader);
        GL20.glAttachShader(this.programId, fragShader);

        GL20.glBindAttribLocation(this.programId, 0, "Position");
        GL20.glBindAttribLocation(this.programId, 1, "UV0");
        GL20.glBindAttribLocation(this.programId, 2, "Color");

        GL20.glLinkProgram(this.programId);

        if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOGGER.error("Program link failed for {} / {}: {}", vertexName, fragmentName, GL20.glGetProgramInfoLog(this.programId));
            GL20.glDeleteProgram(this.programId);
            this.programId = 0;
            this.linked = false;
            return;
        }

        GL20.glDeleteShader(vertShader);
        GL20.glDeleteShader(fragShader);
        this.linked = true;
    }

    private static int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOGGER.error("Shader compile error (type {}): {}", type, GL20.glGetShaderInfoLog(shader));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private String loadSource(String name, String fallback) {
        String path = "/assets/fluxvisuals/shaders/render2d/" + name;
        try (InputStream stream = Render2DShader.class.getResourceAsStream(path)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public void bind() {
        if (!this.linked) {
            init();
        }
        if (this.linked) {
            GL20.glUseProgram(this.programId);
        }
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public boolean isLinked() {
        return this.linked;
    }

    public int getUniform(String name) {
        return this.uniformMap.computeIfAbsent(name, k -> GL20.glGetUniformLocation(this.programId, k));
    }

    public void setFloat(String name, float value) {
        int loc = getUniform(name);
        if (loc != -1) {
            GL20.glUniform1f(loc, value);
        }
    }

    public void setVec2(String name, float x, float y) {
        int loc = getUniform(name);
        if (loc != -1) {
            GL20.glUniform2f(loc, x, y);
        }
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        int loc = getUniform(name);
        if (loc != -1) {
            GL20.glUniform4f(loc, x, y, z, w);
        }
    }

    public void setColor(String name, int argb) {
        RenderColor.toFloats(argb, TEMP_COLOR);
        setVec4(name, TEMP_COLOR[0], TEMP_COLOR[1], TEMP_COLOR[2], TEMP_COLOR[3]);
    }

    public void setColor(String name, Color color) {
        RenderColor.toFloats(color, TEMP_COLOR);
        setVec4(name, TEMP_COLOR[0], TEMP_COLOR[1], TEMP_COLOR[2], TEMP_COLOR[3]);
    }

    public void setInt(String name, int value) {
        int loc = getUniform(name);
        if (loc != -1) {
            GL20.glUniform1i(loc, value);
        }
    }

    public void setMatrix4f(String name, Matrix4f matrix) {
        int loc = getUniform(name);
        if (loc != -1 && matrix != null) {
            MATRIX_BUFFER.clear();
            matrix.get(MATRIX_BUFFER);
            MATRIX_BUFFER.rewind();
            GL20.glUniformMatrix4fv(loc, false, MATRIX_BUFFER);
        }
    }
}
