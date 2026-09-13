#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float along = texCoord0.x;
    float across = abs(texCoord0.y - 0.5) * 2.0;
    float head = texture(Sampler0, vec2(0.42 + along * 0.28, texCoord0.y)).a;
    float ribbon = (1.0 - smoothstep(0.04, 0.96, along)) * (1.0 - smoothstep(0.10, 0.98, across));
    float core = (1.0 - smoothstep(0.00, 0.72, across)) * (1.0 - smoothstep(0.18, 1.0, along));
    float alpha = max(head * 0.28, ribbon * 0.64 + core * 0.18) * vertexColor.a;

    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, alpha);
}
