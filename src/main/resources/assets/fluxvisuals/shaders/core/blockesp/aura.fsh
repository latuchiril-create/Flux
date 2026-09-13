#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec4 vertexColor;
in vec3 localPos;

out vec4 fragColor;

void main() {
    float t = GameTime * LineWidth * 240.0;
    vec3 animatedPos = localPos + vec3(t * 0.08, t * 0.035, -t * 0.055);
    vec3 p = abs(fract(animatedPos * 1.7) - 0.5);
    float edge = smoothstep(0.16, 0.48, max(max(p.x, p.y), p.z));
    float breathe = 0.78 + 0.22 * sin((animatedPos.x + animatedPos.y + animatedPos.z) * 5.5 + t * 1.6);
    vec3 color = mix(vertexColor.rgb * 0.55, vertexColor.rgb, edge) * breathe;
    float outAlpha = mix(vertexColor.a * (0.42 + edge * 0.58), 1.0, smoothstep(0.98, 1.0, vertexColor.a));
    fragColor = vec4(color, outAlpha) * ColorModulator;
}
