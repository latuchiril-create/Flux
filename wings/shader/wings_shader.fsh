#version 410 core

in vec4 vColor;
out vec4 fragColor;

layout(std140) uniform Uniforms {
    mat4 uProjection;
    mat4 uModelView;
    vec4 uParams;
};

mat2 rotate2D(float r) {
    return mat2(cos(r), sin(r), -sin(r), cos(r));
}

void main() {
    float time = uParams.x;
    vec2 resolution = uParams.yz;

    vec2 uv = 0.33 * (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    vec3 col = vec3(0.0);
    float t = time;

    vec2 n = vec2(0.0);
    vec2 q = vec2(0.0);
    vec2 p = uv * 2.5;
    float d = dot(p, p);
    float S = 16.0;
    float a = 0.0;
    mat2 m = rotate2D(15.0 + (sin(d * 0.1 + time * 0.1) * 2.0));

    for (float j = 0.0; j < 6.0; j++) {
        p *= m * 1.05;
        n *= m;
        q = p * S + t * 2.5 + sin((t + j)) * 0.0018 + 3.0 * j - 1.25 * n;
        a += dot(cos(q) / S, vec2(0.15));
        n -= sin(q);
        S *= 1.5;
    }

    col = vec3(2.5, 1.9, 3.5) * (a + 0.182) + 9.0 * a + a;

    fragColor = vec4(col, vColor.a);
}
