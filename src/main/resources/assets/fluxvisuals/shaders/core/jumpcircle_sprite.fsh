#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 source = texture(Sampler0, texCoord0);
    float brightness = max(source.r, max(source.g, source.b));
    float sourceAlpha = source.a < 0.99 ? source.a : smoothstep(0.56, 0.84, brightness);
    float alpha = sourceAlpha * vertexColor.a;
    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, alpha);
}
