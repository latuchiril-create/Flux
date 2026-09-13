#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    vec2 centered = texCoord0 * 2.0 - 1.0;
    float distanceFromCenter = length(centered);

    float nearShape = smoothstep(0.18, 0.72, texel.a);
    float shapeEdge = smoothstep(0.10, 0.48, texel.a) * (1.0 - smoothstep(0.46, 0.90, texel.a));
    float radialFade = 1.0 - smoothstep(0.46, 0.62, distanceFromCenter);
    float alpha = (nearShape * 0.018 + shapeEdge * 0.12) * radialFade * vertexColor.a;

    if (alpha <= 0.003) {
        discard;
    }

    vec3 hotCenter = mix(vertexColor.rgb, vec3(1.0), 0.10);
    vec3 edgeTint = vertexColor.rgb * vec3(0.70, 0.82, 0.95);
    vec3 color = mix(edgeTint, hotCenter, shapeEdge);

    vec4 glow = vec4(color, alpha);
    fragColor = apply_fog(glow, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
