#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float alphaAt(vec2 uv) {
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
        return 0.0;
    }
    return texture(Sampler0, uv).a;
}

void main() {
    float core = alphaAt(texCoord0);

    float nearShape = 0.0;
    nearShape += alphaAt(texCoord0 + vec2(0.020, 0.000));
    nearShape += alphaAt(texCoord0 + vec2(-0.020, 0.000));
    nearShape += alphaAt(texCoord0 + vec2(0.000, 0.020));
    nearShape += alphaAt(texCoord0 + vec2(0.000, -0.020));
    nearShape *= 0.25;

    float rim = max(0.0, nearShape - core * 0.72);
    float alpha = rim * vertexColor.a * 0.18;

    if (alpha <= 0.002) {
        discard;
    }

    vec3 color = mix(vertexColor.rgb, vec3(1.0), 0.10);
    fragColor = apply_fog(vec4(color, alpha), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
