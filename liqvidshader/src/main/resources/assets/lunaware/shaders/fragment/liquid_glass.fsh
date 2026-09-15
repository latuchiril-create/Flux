#version 150

uniform sampler2D Sampler0;
uniform vec2 rectSize;
uniform vec2 screenSize;
uniform float radius;
uniform float opacity;
uniform float distortion;
uniform float innerDistortion;
uniform float innerBlur;
uniform float edgeLight;
uniform float shine;
uniform vec4 tintColor;

in vec2 texCoord;
out vec4 fragColor;

vec3 smoothBackdrop(vec2 uv, vec2 texel) {
    vec3 color = texture(Sampler0, clamp(uv, vec2(0.0), vec2(1.0))).rgb * 0.34;
    color += texture(Sampler0, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb * 0.10;
    color += texture(Sampler0, clamp(uv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb * 0.10;
    color += texture(Sampler0, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb * 0.10;
    color += texture(Sampler0, clamp(uv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb * 0.10;
    color += texture(Sampler0, clamp(uv + vec2(texel.x, texel.y), vec2(0.0), vec2(1.0))).rgb * 0.065;
    color += texture(Sampler0, clamp(uv + vec2(texel.x, -texel.y), vec2(0.0), vec2(1.0))).rgb * 0.065;
    color += texture(Sampler0, clamp(uv + vec2(-texel.x, texel.y), vec2(0.0), vec2(1.0))).rgb * 0.065;
    color += texture(Sampler0, clamp(uv - vec2(texel.x, texel.y), vec2(0.0), vec2(1.0))).rgb * 0.065;
    return color;
}

float roundedBoxSDF(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 localPoint = texCoord * rectSize - halfSize;
    float distance = roundedBoxSDF(localPoint, max(halfSize - 1.0, 0.0), radius);
    float smoothing = clamp(fwidth(distance) * 0.85, 0.5, 1.0);
    float mask = 1.0 - smoothstep(0.0, smoothing, distance);
    if (mask <= 0.001) {
        discard;
    }

    vec2 screenUv = gl_FragCoord.xy / screenSize;
    vec2 fromCenter = localPoint / max(halfSize, vec2(1.0));
    float edgeWidth = max(edgeLight, 0.001);
    float edge = smoothstep(-edgeWidth, 0.0, distance);
    edge = edge * edge * (3.0 - 2.0 * edge);
    // Keep the highlight narrow, but carry the liquid refraction and local blur
    // a little way into the panel. This makes the perimeter feel like glass
    // instead of only blurring the one-pixel outline.
    float innerBandWidth = max(edgeWidth * 4.0,
            min(halfSize.x, halfSize.y) * 0.16);
    float innerEdge = smoothstep(-innerBandWidth, 0.0, distance);
    innerEdge = innerEdge * innerEdge * (3.0 - 2.0 * innerEdge);
    vec2 radial = normalize(fromCenter + vec2(0.0001));
    vec2 tangent = vec2(-radial.y, radial.x);
    float radialWeight = smoothstep(0.08, 0.38, length(fromCenter));
    vec2 wave = vec2(
            sin(screenUv.y * 9.0 + screenUv.x * 6.0),
            cos(screenUv.x * 8.0 - screenUv.y * 5.0)
    );
    vec2 pixel = 1.0 / max(screenSize, vec2(1.0));
    // Do not apply a constant offset in the center: the liquid movement is
    // deliberately confined to the smooth perimeter band.
    float innerWeight = innerEdge * radialWeight * step(0.5, innerBlur);
    float perimeterWeight = edge * radialWeight;
    float refraction = distortion * perimeterWeight + innerDistortion * innerWeight;
    vec2 offset = (wave * 0.24 + radial * 0.76)
            * refraction * pixel;

    vec3 blurred = smoothBackdrop(screenUv + offset, pixel);
    // Refract the already blurred backdrop in several directions near the edge.
    if (innerEdge > 0.001 && innerBlur > 0.5) {
        float edgeDistortion = innerDistortion * (0.28 + innerEdge * 0.72);
        vec2 edgeOffset = edgeDistortion * pixel;
        vec2 radialOffset = radial * edgeOffset * radialWeight;
        vec2 tangentOffset = tangent * edgeOffset * 0.65 * radialWeight;
        vec3 edgeBlur = smoothBackdrop(screenUv + offset + radialOffset, pixel);
        edgeBlur += smoothBackdrop(screenUv + offset - radialOffset, pixel);
        edgeBlur += smoothBackdrop(screenUv + offset + tangentOffset, pixel);
        edgeBlur += smoothBackdrop(screenUv + offset - tangentOffset, pixel);
        blurred = mix(blurred, edgeBlur * 0.25, innerEdge * 0.70);
    }

    // Smooth glass tint blending with refracted background
    vec3 baseGlass = blurred;
    vec3 color = mix(baseGlass, tintColor.rgb, tintColor.a);

    // Soft organic specular glint without white border line
    if (shine > 0.001) {
        vec2 lightSource = normalize(vec2(-0.45, -0.85));
        float rimDot = max(0.0, dot(radial, lightSource));
        float specular = pow(rimDot, 12.0) * edge * shine * 0.20;
        color += tintColor.rgb * specular;
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), mask * opacity);
}
