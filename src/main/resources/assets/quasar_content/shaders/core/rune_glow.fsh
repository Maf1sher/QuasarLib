#version 150

uniform sampler2D SceneSampler;
uniform sampler2D MaskSampler;
uniform sampler2D DepthSampler;
uniform vec3 MaskColor;
uniform float MaskStrength;
uniform vec3 GlowColor;
uniform float GlowStrength;
uniform float GlowPulse;
uniform float GlowTime;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 sceneColor = texture(SceneSampler, texCoord);
    float coverage = clamp(texture(MaskSampler, texCoord).r * MaskStrength, 0.0, 1.0);
    float pulse = 0.75 + 0.25 * sin(GlowTime * GlowPulse);
    vec3 tint = GlowColor + MaskColor * 0.2;
    vec3 glow = tint * GlowStrength * pulse;
    fragColor = vec4(sceneColor.rgb + glow * coverage, sceneColor.a);
}
