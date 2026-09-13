#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec4 vertexColor;
in vec3 localPos;

out vec4 fragColor;

float time;
vec2 screenSize;
vec4 baseColor;
float alpha;

float plasma(vec2 uv, float t) {
    float v = 0.0;

    v += sin(uv.x * 10.0 + t);
    v += sin((uv.y * 10.0 + t) * 0.5);
    v += sin((uv.x * 10.0 + uv.y * 10.0 + t) * 0.5);

    vec2 c = uv * 10.0 - vec2(5.0);
    v += sin(sqrt(c.x * c.x + c.y * c.y + 1.0) + t);

    c = uv * 10.0 - vec2(5.0 + sin(t * 0.5) * 3.0, 5.0 + cos(t * 0.7) * 3.0);
    v += sin(sqrt(c.x * c.x + c.y * c.y + 1.0) + t * 1.5);

    return v * 0.2;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    time = GameTime * LineWidth * 240.0;
    screenSize = max(ScreenSize, vec2(1.0));
    baseColor = vertexColor;
    alpha = vertexColor.a;

    vec2 uv = gl_FragCoord.xy / screenSize;

    float t = time * 0.8;

    float p = plasma(uv, t);

    vec3 base = baseColor.rgb;

    float baseHue = 0.0;
    float baseVal = max(max(base.r, base.g), base.b);

    if (baseVal > 0.0) {
        float minVal = min(min(base.r, base.g), base.b);

        if (base.r >= base.g && base.r >= base.b) {
            baseHue = (base.g - base.b) / (baseVal - minVal + 0.001);
        } else if (base.g >= base.r && base.g >= base.b) {
            baseHue = 2.0 + (base.b - base.r) / (baseVal - minVal + 0.001);
        } else {
            baseHue = 4.0 + (base.r - base.g) / (baseVal - minVal + 0.001);
        }
        baseHue /= 6.0;
        if (baseHue < 0.0) baseHue += 1.0;
    }

    float flowHue = baseHue + p * 0.15 + sin(time * 0.3) * 0.05;
    float flowSat = 0.7 + sin(p * 3.14159) * 0.3;
    float flowVal = 0.8 + p * 0.4;

    vec3 color = hsv2rgb(vec3(flowHue, flowSat, flowVal));

    float glow = pow(abs(sin(p * 3.14159 * 2.0)), 2.0) * 0.5;
    color += baseColor.rgb * glow;

    float pulse = 0.8 + sin(time * 2.0 + p * 5.0) * 0.2;
    color *= pulse;

    color = clamp(color, 0.0, 1.0);

    float finalAlpha = mix(alpha * (0.3 + abs(sin(p * 3.14159)) * 0.4), 1.0, smoothstep(0.98, 1.0, alpha));

    fragColor = vec4(color, finalAlpha) * ColorModulator;
}
