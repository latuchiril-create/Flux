#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float sat(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float radial = length(vec2(p.x * 0.92, p.y * 1.08));
    float diamond = abs(p.x) * 0.74 + abs(p.y);
    float textureFeather = texture(Sampler0, texCoord0).a;

    float wideBloom = exp(-radial * radial * 2.35) * (1.0 - smoothstep(0.78, 1.12, radial));
    float softDiamond = exp(-diamond * diamond * 5.8);
    float inner = exp(-diamond * diamond * 18.0);
    float core = exp(-diamond * diamond * 58.0);

    float horizontalRay = exp(-p.y * p.y * 42.0) * (1.0 - smoothstep(0.20, 1.0, abs(p.x)));
    float verticalRay = exp(-p.x * p.x * 54.0) * (1.0 - smoothstep(0.16, 0.88, abs(p.y)));
    float diagonalRay = exp(-pow(abs(p.x + p.y) * 0.72, 2.0) * 34.0) * (1.0 - smoothstep(0.22, 1.10, radial));
    float sparkle = 0.93 + 0.07 * cos(atan(p.y, p.x) * 4.0);
    float edgeCut = 1.0 - smoothstep(0.86, 1.14, radial);

    float alpha = wideBloom * 0.10 + softDiamond * 0.12 + inner * 0.18 + core * 0.24
            + horizontalRay * 0.045 + verticalRay * 0.030 + diagonalRay * 0.020;
    alpha *= edgeCut * sparkle * vertexColor.a * (0.92 + textureFeather * 0.08);

    if (alpha <= 0.002) {
        discard;
    }

    vec3 outerBlue = vec3(0.12, 0.32, 1.0);
    vec3 cyan = vec3(0.42, 0.90, 1.0);
    vec3 hotWhite = vec3(0.92, 0.98, 1.0);
    vec3 baseTint = mix(outerBlue, vertexColor.rgb, 0.22);
    vec3 color = mix(baseTint, cyan, sat(softDiamond * 0.46 + inner * 0.32));
    color = mix(color, hotWhite, sat(core * 0.55 + horizontalRay * 0.05));
    color += vec3(0.02, 0.05, 0.12) * wideBloom;
    fragColor = vec4(color, alpha);
}
