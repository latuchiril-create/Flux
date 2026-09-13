#version 150

#moj_import <minecraft:projection.glsl>

in vec4 Position;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2  BlurDir;   // (1,0) = горизонтальный проход, (0,1) = вертикальный
    float Radius;    // радиус в текселях
    float Spread;    // множитель шага между семплами (>1 = дешёвый широкий блюр)
};

out vec2 texCoord;
out vec2 sampleStep;

void main() {
    // Полноэкранный квад: Position.xy приходит в диапазоне 0..1
    vec4 outPos = ProjMat * vec4(Position.xy * OutSize, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);

    vec2 oneTexel = 1.0 / InSize;
    sampleStep = oneTexel * BlurDir * max(Spread, 1.0);

    texCoord = Position.xy;
}
