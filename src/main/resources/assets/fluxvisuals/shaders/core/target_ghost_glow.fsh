#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord0 - vec2(0.5);
    float dist = length(centered);
    float source = texture(Sampler0, texCoord0).a;
    source = source * source * (3.0 - 2.0 * source);
    float radial = 1.0 - smoothstep(0.05, 0.56, dist);
    float rim = smoothstep(0.06, 0.24, dist) * (1.0 - smoothstep(0.28, 0.58, dist));
    float rays = 0.90 + 0.10 * cos(atan(centered.y, centered.x) * 6.0);
    float alpha = source * (radial * 0.38 + rim * 0.16) * rays * vertexColor.a;

    if (alpha <= 0.002) {
        discard;
    }

    vec3 color = vertexColor.rgb * (1.04 - smoothstep(0.0, 0.56, dist) * 0.10);
    fragColor = vec4(color, alpha);
}
