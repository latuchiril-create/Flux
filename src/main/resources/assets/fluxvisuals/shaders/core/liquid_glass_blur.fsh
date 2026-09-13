#version 150

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 pixel = 1.0 / vec2(textureSize(Sampler0, 0));
    float time = 0.0;
    float radius = 3.0;
    vec2 wave = vec2(
        sin((texCoord.y + time * 0.12) * 34.0),
        cos((texCoord.x - time * 0.10) * 29.0)
    ) * pixel * radius * 0.35;

    vec4 color = texture(Sampler0, texCoord + wave) * 0.22;
    color += texture(Sampler0, texCoord + wave + vec2(pixel.x, 0.0) * radius) * 0.14;
    color += texture(Sampler0, texCoord + wave - vec2(pixel.x, 0.0) * radius) * 0.14;
    color += texture(Sampler0, texCoord + wave + vec2(0.0, pixel.y) * radius) * 0.14;
    color += texture(Sampler0, texCoord + wave - vec2(0.0, pixel.y) * radius) * 0.14;
    color += texture(Sampler0, texCoord + wave + vec2(pixel.x, pixel.y) * radius * 0.7) * 0.11;
    color += texture(Sampler0, texCoord + wave - vec2(pixel.x, pixel.y) * radius * 0.7) * 0.11;

    vec3 tint = vec3(0.82, 0.90, 1.0);
    fragColor = vec4(mix(color.rgb, tint, 0.10), color.a);
}
