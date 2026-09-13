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

    if (opacity <= 0.001) {
        discard;
    }

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
