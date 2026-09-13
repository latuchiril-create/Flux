#version 150

#moj_import <rockstar:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;

out vec4 OutColor;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r, float smoothness) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    vec2 q_clamped = max(q, 0.0);
    float len = pow(pow(q_clamped.x, smoothness) + pow(q_clamped.y, smoothness), 1.0/smoothness);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

void main() {
    // The 1.21.8 pipeline no longer loads scalar uniforms from data.json.
    // Derivatives recover this quad's real pixel size while preserving the
    // original Rockstar rounded-SDF and liquid-glass calculations.
    vec2 Size = vec2(
        1.0 / max(abs(dFdx(FragCoord.x)), 0.000001),
        1.0 / max(abs(dFdy(FragCoord.y)), 0.000001)
    );
    vec4 Radius = vec4(min(11.0, min(Size.x, Size.y) * 0.25));
    float Smoothness = 1.0;
    float CornerSmoothness = 2.0;
    float GlobalAlpha = 1.0;
    float FresnelPower = 2.0;
    vec3 FresnelColor = vec3(0.72, 0.82, 0.92);
    float FresnelAlpha = 0.72;
    float BaseAlpha = 0.30;
    bool FresnelInvert = false;
    float FresnelMix = 0.46;
    float DistortStrength = 0.012;

    vec2 center = Size * 0.5;
    vec2 box_half_size = center - 1.0;
    vec2 pos = (FragCoord * Size) - center;

    float distance = roundedBoxSDF(-pos, box_half_size, Radius, CornerSmoothness);
    float alpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);

    float distToEdge = abs(roundedBoxSDF(pos, box_half_size, Radius, CornerSmoothness));

    float max_dist_norm = min(box_half_size.x, box_half_size.y);
    float edge_gradient = 1.0 - clamp(distToEdge / max_dist_norm, 0.0, 1.0);

    float fresnel;
    float base = FresnelInvert ? edge_gradient : (1.0 - edge_gradient);

    if (FresnelPower > 20.0) {
        fresnel = exp(FresnelPower * log(clamp(base, 0.001, 1.0)));
    } else {
        fresnel = pow(base, FresnelPower);
    }
    fresnel = clamp(fresnel, 0.0, 1.0);

    vec2 dir = normalize(pos);
    vec2 framebufferSize = vec2(textureSize(Sampler0, 0));
    vec2 screenTexCoord = vec2(gl_FragCoord.x, framebufferSize.y - gl_FragCoord.y) / framebufferSize;
    vec2 distortedTexCoord = screenTexCoord + dir * fresnel * DistortStrength;

    vec4 texColor = texture(Sampler0, distortedTexCoord) * FragColor;

    vec3 finalColor = mix(texColor.rgb, FresnelColor, fresnel * FresnelMix);
    float finalAlpha = mix(BaseAlpha, FresnelAlpha, fresnel) * alpha;

    if (finalAlpha < 0.001) {
        discard;
    }

    OutColor = vec4(finalColor, finalAlpha * GlobalAlpha);
}
