#version 410 core

layout(std140) uniform Uniforms {
    mat4 uProjection;
    mat4 uModelView;
    vec4 uParams;
};

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec4 inColor;

out vec4 vColor;

void main() {
    vColor = inColor;
    gl_Position = uProjection * uModelView * vec4(inPosition, 1.0);
}
