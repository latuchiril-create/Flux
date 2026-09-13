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
    if (aa <= 0.0) {
        aa = fwidth(dist);
    }
    aa = max(aa, 0.35);

    float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);
    alpha = smoothstep(0.0, 1.0, alpha);

    if (alpha <= 0.001) {
        discard;
    }

    fragColor = texColor * colorModulator * vec4(1.0, 1.0, 1.0, alpha);
}
