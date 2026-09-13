#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = vec2((gl_VertexID == 1 || gl_VertexID == 2) ? 1.0 : 0.0,
                    (gl_VertexID >= 2) ? 1.0 : 0.0);
    vertexColor = Color;
}
