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

const vec3 eps = vec3(0.001, 0., 0.);
const int iter = 64;
const float sq = 0.70710678;

float c(vec3 p) {
    vec3 q = abs(mod(p + vec3(cos(p.z*0.5), cos(p.x*0.5), cos(p.y*0.5)), 2.0) - 1.0);
    float a = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    q = vec3(p.x+p.y, p.y+p.z, p.z+p.x) * sq;
    q = abs(mod(q, 2.0) - 1.0);
    float b = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    return min(a, b);
}

vec3 n(vec3 p) {
    float o = c(p);
    return normalize(o - vec3(c(p - eps), c(p - eps.zxy), c(p - eps.yzx)));
}

void main() {
    time = GameTime * LineWidth * 240.0;
    screenSize = max(ScreenSize, vec2(1.0));
    baseColor = vertexColor;
    alpha = vertexColor.a;

    float aspect = screenSize.x / screenSize.y;
    vec2 p = gl_FragCoord.xy / screenSize * 2. - 1.;
    vec2 m = vec2(sin(time * 0.3) * 0.3, cos(time * 0.2) * 0.3);
    p.x *= aspect;
    m.x *= aspect;

    vec3 o = vec3(0., 0., time * 0.5);
    vec3 s = vec3(m, 0.);
    vec3 b = vec3(0., 0., 0.);
    vec3 d = vec3(p, 1.) / 32.;
    vec3 t = vec3(.5);
    vec3 a;

    for (int i = 0; i < iter; ++i) {
        float h = c(b + s + o);
        b += h * 10.0 * d;
        t += h;
    }
    t /= float(iter);
    a = n(b + s + o);
    float x = dot(a, t);

    vec3 diffuse = baseColor.rgb;
    t = (t + pow(x, 4.)) * (1. - t * .01) * diffuse;
    t *= b.z * 0.125;

    vec2 vig = p * 0.43;
    vig.y *= aspect;
    float vig_amount = 1.0 - length(vig) * 0.5;

    vec4 color = vec4(t * 3.0, 1.) * vig_amount;

    float finalAlpha = mix(alpha * 0.5, 1.0, smoothstep(0.98, 1.0, alpha));
    fragColor = vec4(color.rgb, finalAlpha) * ColorModulator;
}
