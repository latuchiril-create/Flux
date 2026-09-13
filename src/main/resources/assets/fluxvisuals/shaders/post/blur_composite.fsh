#version 150

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform BlurStyle {
    vec4  Tint;        // rgb = цвет тонирования, a = сила (0 = выключено)
    float Brightness;  // 1.0 = без изменений, 0.8 = чуть затемнить
    float Saturation;  // 1.0 = без изменений, 0.0 = ч/б
    float Vignette;    // 0.0 = выключено, 0.3 = мягкая виньетка
    float Grain;       // 0.0 = выключено, ~0.02 = убирает бандинг
};

in vec2 texCoord;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

// быстрый хеш (Dave Hoskins, hash12) для дизеринга/зерна
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;

    // 1. Насыщенность
    float luma = dot(color, LUMA);
    color = mix(vec3(luma), color, Saturation);

    // 2. Тонирование (мультипликативное, сохраняет контраст)
    color = mix(color, color * Tint.rgb, Tint.a);

    // 3. Яркость
    color *= Brightness;

    // 4. Виньетка: мягкое затемнение к краям, фокус на центре GUI
    vec2  v   = texCoord - 0.5;
    float vig = 1.0 - Vignette * smoothstep(0.0, 0.75, dot(v, v) * 2.0);
    color *= vig;

    // 5. Дизеринг: гауссовы градиенты в 8 бит дают полосы, шум их разбивает
    float n = hash12(gl_FragCoord.xy + fract(GameTime * 1024.0) * 137.0);
    color += (n - 0.5) * Grain;

    fragColor = vec4(color, 1.0);
}
