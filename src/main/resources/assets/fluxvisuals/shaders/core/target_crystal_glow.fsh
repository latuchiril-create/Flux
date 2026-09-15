#version 150

in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r2 = dot(p, p);
    // Continuous near halo and broad bloom, smoothly zero at the boundary.
    float boundary = 1.0 - smoothstep(0.55, 1.0, r2);
    float halo = exp2(-7.0 * r2);
    float core = exp2(-38.0 * r2);
    float intensity = (0.42 * halo + 0.48 * core) * boundary * vertexColor.a;
    vec3 tint = mix(vertexColor.rgb, vec3(1.0), 0.32 * core);
    fragColor = vec4(tint, intensity);
}