#version 150

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 bloom = texture(InSampler, texCoord);
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));

    vec4 horizontalRay = vec4(0.0);
    horizontalRay += texture(InSampler, texCoord + vec2(texel.x * 7.0, 0.0)) * 0.32;
    horizontalRay += texture(InSampler, texCoord - vec2(texel.x * 7.0, 0.0)) * 0.32;
    horizontalRay += texture(InSampler, texCoord + vec2(texel.x * 15.0, 0.0)) * 0.18;
    horizontalRay += texture(InSampler, texCoord - vec2(texel.x * 15.0, 0.0)) * 0.18;
    horizontalRay += texture(InSampler, texCoord + vec2(texel.x * 28.0, 0.0)) * 0.08;
    horizontalRay += texture(InSampler, texCoord - vec2(texel.x * 28.0, 0.0)) * 0.08;

    vec2 diag = vec2(texel.x * 9.0, texel.y * 9.0);
    vec4 diagonalRay = vec4(0.0);
    diagonalRay += texture(InSampler, texCoord + diag) * 0.15;
    diagonalRay += texture(InSampler, texCoord - diag) * 0.15;
    diagonalRay += texture(InSampler, texCoord + diag * 2.1) * 0.08;
    diagonalRay += texture(InSampler, texCoord - diag * 2.1) * 0.08;

    float core = smoothstep(0.010, 0.34, bloom.a);
    float ray = smoothstep(0.020, 0.30, horizontalRay.a) * 0.22
            + smoothstep(0.020, 0.28, diagonalRay.a) * 0.10;
    float alpha = core * 0.46 + ray;
    if (alpha <= 0.002) {
        discard;
    }

    vec3 lightBlue = vec3(0.34, 0.78, 1.0);
    vec3 warmWhite = vec3(0.82, 0.96, 1.0);
    vec3 rayColor = horizontalRay.rgb * 0.42 + diagonalRay.rgb * 0.22;
    vec3 color = mix(bloom.rgb + rayColor, lightBlue, 0.30);
    color = mix(color, warmWhite, smoothstep(0.18, 0.72, bloom.a) * 0.38);
    fragColor = vec4(color * alpha, alpha);
}
