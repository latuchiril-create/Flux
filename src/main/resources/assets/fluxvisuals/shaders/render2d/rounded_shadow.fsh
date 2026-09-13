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

    if (dist <= 0.0) {
        shadowAlpha = 1.0;
    }

    if (shadowAlpha <= 0.002) {
        discard;
    }

    fragColor = shadowColor * vec4(1.0, 1.0, 1.0, shadowAlpha);
}
