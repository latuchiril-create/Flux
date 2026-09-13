#version 150

uniform vec2 size;
uniform vec4 radius; // (TL, TR, BR, BL)
uniform float thickness;
uniform vec4 color1; // Top-Left
uniform vec4 color2; // Top-Right
uniform vec4 color3; // Bottom-Right
uniform vec4 color4; // Bottom-Left

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
    if (aa <= 0.0) {
        aa = fwidth(dist);
    }
    aa = max(aa, 0.35);

    float alpha = clamp(0.5 - outlineDist / aa, 0.0, 1.0);
    alpha = smoothstep(0.0, 1.0, alpha);

    if (alpha <= 0.001) {
        discard;
    }

    vec2 uv = localPos / size;
    vec4 col = bilinearColor(uv);
    fragColor = vec4(col.rgb, col.a * alpha);
}
