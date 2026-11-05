#version 150

uniform sampler2D SceneSampler;
uniform sampler2D MaskSampler;
uniform sampler2D DepthSampler;
uniform sampler2D BlurSampler;
uniform float Operation;
uniform vec3 MaskColor;
uniform float MaskStrength;
uniform float WaveAmplitudePixels;
uniform float WaveFrequency;
uniform float WaveSpeed;
uniform float WaveTime;
uniform float ViewportWidth;
uniform float ViewportHeight;
uniform float BlurRadiusPixels;
uniform vec2 LensCenter;
uniform float LensRadiusPixels;
uniform float LensDepth;
uniform float LensStrength;
uniform float MaxDistortionPixels;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 sceneColor = texture(SceneSampler, texCoord);
    float mask = clamp(texture(MaskSampler, texCoord).r * MaskStrength, 0.0, 1.0);
    if (Operation < 0.5) {
        float sceneDepth = clamp(texture(DepthSampler, texCoord).r, 0.0, 1.0);
        vec3 depthTint = mix(MaskColor, vec3(1.0) - MaskColor, sceneDepth * 0.2);
        fragColor = vec4(mix(sceneColor.rgb, depthTint, mask), sceneColor.a);
        return;
    }

    if (Operation < 1.5) {
        float phase = (texCoord.y * WaveFrequency + WaveTime * WaveSpeed) * 6.2831853;
        float horizontalOffset = sin(phase) * WaveAmplitudePixels / max(ViewportWidth, 1.0);
        vec2 displacedUv = clamp(texCoord + vec2(horizontalOffset, 0.0), vec2(0.0), vec2(1.0));
        vec4 distortedColor = texture(SceneSampler, displacedUv);
        fragColor = vec4(mix(sceneColor.rgb, distortedColor.rgb, mask), sceneColor.a);
        return;
    }

    if (Operation > 3.5) {
        vec2 viewport = vec2(max(ViewportWidth, 1.0), max(ViewportHeight, 1.0));
        vec2 pixelDelta = (texCoord - LensCenter) * viewport;
        float distancePixels = length(pixelDelta);
        float normalizedRadius = distancePixels / max(LensRadiusPixels, 1.0);
        float innerFade = smoothstep(0.2, 0.36, normalizedRadius);
        float outerFade = 1.0 - smoothstep(0.88, 1.0, normalizedRadius);
        float sceneDepth = texture(DepthSampler, texCoord).r;
        float inFrontOfBackground = step(LensDepth + 0.00025, sceneDepth);
        float coverage = mask * innerFade * outerFade * inFrontOfBackground;
        vec2 direction = pixelDelta / max(distancePixels, 1.0);
        float displacementPixels = MaxDistortionPixels * LensStrength
            * (0.35 + 0.65 * (1.0 - smoothstep(0.22, 0.9, normalizedRadius)));
        vec2 displacedUv = clamp(texCoord - direction * displacementPixels / viewport,
            vec2(0.0), vec2(1.0));
        float displacedDepth = texture(DepthSampler, displacedUv).r;
        coverage *= step(LensDepth + 0.00025, displacedDepth);
        vec4 lensedColor = texture(SceneSampler, displacedUv);
        fragColor = vec4(mix(sceneColor.rgb, lensedColor.rgb, clamp(coverage, 0.0, 1.0)), sceneColor.a);
        return;
    }

    vec2 texelSize = vec2(1.0 / max(ViewportWidth, 1.0), 1.0 / max(ViewportHeight, 1.0));
    vec2 minUv = texelSize * 0.5;
    vec2 maxUv = vec2(1.0) - minUv;
    vec2 axis = Operation < 2.5 ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec2 sampleStep = axis * texelSize * BlurRadiusPixels * 0.5;
    vec3 blurredColor = texture(BlurSampler, texCoord).rgb * 0.38774;
    blurredColor += texture(BlurSampler, clamp(texCoord + sampleStep, minUv, maxUv)).rgb * 0.24477;
    blurredColor += texture(BlurSampler, clamp(texCoord - sampleStep, minUv, maxUv)).rgb * 0.24477;
    blurredColor += texture(BlurSampler, clamp(texCoord + sampleStep * 2.0, minUv, maxUv)).rgb * 0.06136;
    blurredColor += texture(BlurSampler, clamp(texCoord - sampleStep * 2.0, minUv, maxUv)).rgb * 0.06136;

    if (Operation < 2.5) {
        fragColor = vec4(blurredColor, sceneColor.a);
    } else {
        fragColor = vec4(mix(sceneColor.rgb, blurredColor, mask), sceneColor.a);
    }
}
