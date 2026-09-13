#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float source = texture(Sampler0, texCoord0).a;
    float softAlpha = source * source * (3.0 - 2.0 * source);
    float alpha = softAlpha * vertexColor.a;
    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, alpha);
}
