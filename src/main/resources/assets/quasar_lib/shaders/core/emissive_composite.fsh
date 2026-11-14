#version 150

#moj_import <quasar_lib:medium_optics.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D EffectSampler;
uniform sampler2D EmissionSampler;
uniform sampler2D BloomSampler;
uniform sampler2D MaskSampler;

uniform float Pass;
uniform float Threshold;
uniform float Intensity;
uniform float RadiusPixels;
uniform float Horizontal;
uniform float Solidity;
uniform float EmissionFog;
uniform float MaskStrength;
uniform float ViewportWidth;
uniform float ViewportHeight;

in vec2 texCoord;

out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec3 brightSample(vec2 uv, vec2 minUv, vec2 maxUv) {
    vec3 color = texture(EmissionSampler, clamp(uv, minUv, maxUv)).rgb;
    float bright = clamp((luminance(color) - Threshold) / max(1.0 - Threshold, 0.0001), 0.0, 1.0);
    return color * bright;
}

void main() {
    if (Pass < 0.5) {
        vec2 texel = 1.0 / vec2(textureSize(EmissionSampler, 0));
        vec2 minUv = texel * 0.5;
        vec2 maxUv = vec2(1.0) - minUv;
        vec2 sampleOffset = texel * 0.5;
        vec3 bright = brightSample(texCoord + vec2(-sampleOffset.x, -sampleOffset.y), minUv, maxUv);
        bright += brightSample(texCoord + vec2(sampleOffset.x, -sampleOffset.y), minUv, maxUv);
        bright += brightSample(texCoord + vec2(-sampleOffset.x, sampleOffset.y), minUv, maxUv);
        bright += brightSample(texCoord + vec2(sampleOffset.x, sampleOffset.y), minUv, maxUv);
        fragColor = vec4(bright * 0.25 * Intensity, 1.0);
        return;
    }

    if (Pass < 2.5) {
        vec2 bloomTexel = 1.0 / vec2(textureSize(BloomSampler, 0));
        vec2 minUv = bloomTexel * 0.5;
        vec2 maxUv = vec2(1.0) - minUv;
        vec2 axis = Horizontal > 0.5 ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
        vec2 screenTexel = vec2(
            1.0 / max(ViewportWidth, 1.0),
            1.0 / max(ViewportHeight, 1.0)
        );
        vec2 sampleStep = axis * screenTexel * RadiusPixels * 0.5;
        vec3 blurred = texture(BloomSampler, texCoord).rgb * 0.38774;
        blurred += texture(BloomSampler, clamp(texCoord + sampleStep, minUv, maxUv)).rgb * 0.24477;
        blurred += texture(BloomSampler, clamp(texCoord - sampleStep, minUv, maxUv)).rgb * 0.24477;
        blurred += texture(BloomSampler, clamp(texCoord + sampleStep * 2.0, minUv, maxUv)).rgb * 0.06136;
        blurred += texture(BloomSampler, clamp(texCoord - sampleStep * 2.0, minUv, maxUv)).rgb * 0.06136;
        fragColor = vec4(blurred, 1.0);
        return;
    }

    vec4 effect = texture(EffectSampler, texCoord);
    float coverage = clamp(texture(MaskSampler, texCoord).r * MaskStrength, 0.0, 1.0);
    vec3 emission = effect.rgb;
    float fogDistance = quasarMediumFogDistance(0.0);
    if (EmissionFog > 0.5) {
        emission = quasarMediumFog(emission, fogDistance);
    }

    // The scene is re-added exactly once. The effect layer only replaces it
    // through an explicitly solid footprint; pure emission is additive.
    float solidAlpha = coverage * clamp(effect.a, 0.0, 1.0) * clamp(Solidity, 0.0, 1.0);
    vec3 scene = texture(SceneSampler, texCoord).rgb;
    vec3 color = scene * (1.0 - solidAlpha) + emission * coverage;

    float bloomFade = EmissionFog > 0.5
        ? quasarMediumFogTransmission(fogDistance) : 1.0;
    color += texture(BloomSampler, texCoord).rgb * coverage * bloomFade;
    fragColor = vec4(color, 1.0);
}
