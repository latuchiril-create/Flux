#version 150

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
#ifdef HORIZONTAL
    vec2 direction = vec2(texel.x, 0.0);
#else
    vec2 direction = vec2(0.0, texel.y);
#endif

    vec4 sum = texture(InSampler, texCoord) * 0.182000;
    sum += texture(InSampler, texCoord + direction * 1.384615) * 0.270000;
    sum += texture(InSampler, texCoord - direction * 1.384615) * 0.270000;
    sum += texture(InSampler, texCoord + direction * 3.230769) * 0.120000;
    sum += texture(InSampler, texCoord - direction * 3.230769) * 0.120000;
    sum += texture(InSampler, texCoord + direction * 5.384615) * 0.019000;
    sum += texture(InSampler, texCoord - direction * 5.384615) * 0.019000;

    fragColor = vec4(sum.rgb, clamp(sum.a, 0.0, 1.0));
}
