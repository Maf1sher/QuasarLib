#version 150

#moj_import <fog.glsl>
#moj_import <quasar_lib:depth_reconstruction.glsl>
#moj_import <quasar_lib:soft_particles.glsl>

uniform sampler2D Sampler0;
uniform sampler2D ParticleDepthSampler;
uniform float SoftDepthEnabled;
uniform float ParticleSoftness;
uniform float ParticleAdditive;
uniform float ViewportWidth;
uniform float ViewportHeight;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float particleViewDistance;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * texture(Sampler0, texCoord0);
    if (color.a < 0.004) {
        discard;
    }
    if (SoftDepthEnabled > 0.5) {
        vec2 uv = gl_FragCoord.xy / vec2(ViewportWidth, ViewportHeight);
        float sceneDistance = quasarViewDistance(texture(ParticleDepthSampler, uv).r, uv);
        color.a *= quasarSoftParticleFade(sceneDistance, particleViewDistance, ParticleSoftness);
    }
    color.a *= quasarParticleNearFade(particleViewDistance, 0.25, 1.0);
    if (ParticleAdditive > 0.5) {
        float fade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
        fragColor = vec4(color.rgb, color.a * fade);
    } else {
        fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    }
}
