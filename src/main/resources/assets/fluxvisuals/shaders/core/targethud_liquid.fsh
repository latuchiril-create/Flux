#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

vec3 smoothBackdrop(vec2 uv, vec2 px) {
    vec3 c = texture(Sampler0, clamp(uv, 0.0, 1.0)).rgb * 0.34;
    c += texture(Sampler0, clamp(uv + vec2(px.x,0),0.0,1.0)).rgb * 0.10;
    c += texture(Sampler0, clamp(uv - vec2(px.x,0),0.0,1.0)).rgb * 0.10;
    c += texture(Sampler0, clamp(uv + vec2(0,px.y),0.0,1.0)).rgb * 0.10;
    c += texture(Sampler0, clamp(uv - vec2(0,px.y),0.0,1.0)).rgb * 0.10;
    c += texture(Sampler0, clamp(uv + px,0.0,1.0)).rgb * 0.065;
    c += texture(Sampler0, clamp(uv - px,0.0,1.0)).rgb * 0.065;
    c += texture(Sampler0, clamp(uv + vec2(px.x,-px.y),0.0,1.0)).rgb * 0.065;
    c += texture(Sampler0, clamp(uv + vec2(-px.x,px.y),0.0,1.0)).rgb * 0.065;
    return c;
}

float roundedBoxSDF(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - r;
}

void main() {
    vec2 size = vec2(1.0/max(abs(dFdx(texCoord.x)),0.000001), 1.0/max(abs(dFdy(texCoord.y)),0.000001));
    vec2 halfSize = size * 0.5;
    vec2 local = texCoord * size - halfSize;
    float dist = roundedBoxSDF(local, max(halfSize-1.0,0.0), min(11.0,min(size.x,size.y)*0.25));
    float mask = 1.0-smoothstep(0.0,clamp(fwidth(dist)*0.85,0.5,1.0),dist);
    if (mask <= 0.001) discard;
    vec2 screenUv = gl_FragCoord.xy/max(ScreenSize,vec2(1.0));
    vec2 px = 1.0/max(ScreenSize,vec2(1.0));
    vec2 center = local/max(halfSize,vec2(1.0));
    vec2 radial = normalize(center+vec2(0.0001));
    float edge = smoothstep(-7.0,0.0,dist);
    vec2 wave = vec2(sin(screenUv.y*9.0+screenUv.x*6.0),cos(screenUv.x*8.0-screenUv.y*5.0));
    vec2 offset = (wave*0.24+radial*0.76)*edge*7.0*px;
    vec3 blurred = smoothBackdrop(screenUv+offset,px);
    vec3 tint = vec3(0.055,0.075,0.11);
    vec3 color = mix(blurred,tint,0.42);
    float shine = clamp(1.0-(local.y/max(halfSize.y,1.0)+1.0)*0.5,0.0,1.0);
    color += mix(vec3(1.0),tint,0.28)*edge*shine*0.12;
    fragColor = vec4(clamp(color,0.0,1.0),mask*0.88)*vertexColor;
}
