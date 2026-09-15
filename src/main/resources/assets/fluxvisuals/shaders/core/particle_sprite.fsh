#version 150

uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    // Use the resource-pack icon itself, retaining its RGB detail and soft alpha.
    fragColor = texture(Sampler0, texCoord0) * vertexColor;
}