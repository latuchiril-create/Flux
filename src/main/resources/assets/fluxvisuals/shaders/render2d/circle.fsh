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
    if (aa <= 0.0) {
        aa = fwidth(dist);
    }
    aa = max(aa, 0.35);

    float alpha = clamp(0.5 - shapeDist / aa, 0.0, 1.0);
    alpha = smoothstep(0.0, 1.0, alpha);

    if (alpha <= 0.001) {
        discard;
    }

    float t = clamp(dist / max(radius, 0.001), 0.0, 1.0);
    vec4 col = mix(innerColor, outerColor, t);
    fragColor = vec4(col.rgb, col.a * alpha);
}
