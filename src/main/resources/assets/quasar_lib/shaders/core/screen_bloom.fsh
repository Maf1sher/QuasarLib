#version 150

uniform sampler2D SceneSampler;
uniform sampler2D MaskSampler;
uniform sampler2D BloomSampler;
uniform float Pass;
uniform float Threshold;
uniform float Intensity;
uniform float RadiusPixels;
uniform float MaskStrength;
uniform float Horizontal;
uniform float ViewportWidth;
uniform float ViewportHeight;

in vec2 texCoord;

out vec4 fragColor;

vec3 extractBrightSample(vec2 uv, vec2 minUv, vec2 maxUv) {
    vec2 sampleUv = clamp(uv, minUv, maxUv);
    vec4 sceneColor = texture(SceneSampler, sampleUv);
    float maskCoverage = clamp(texture(MaskSampler, sampleUv).r * MaskStrength, 0.0, 1.0);
    float luminance = dot(sceneColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    float brightPass = clamp((luminance - Threshold) / max(1.0 - Threshold, 0.0001), 0.0, 1.0);
    return sceneColor.rgb * maskCoverage * brightPass;
}

void main() {
    if (Pass < 0.5) {
        vec2 sceneTexelSize = 1.0 / vec2(textureSize(SceneSampler, 0));
        vec2 minUv = sceneTexelSize * 0.5;
        vec2 maxUv = vec2(1.0) - minUv;
        vec2 sampleOffset = sceneTexelSize * 0.5;
        vec3 brightColor = extractBrightSample(texCoord + vec2(-sampleOffset.x, -sampleOffset.y), minUv, maxUv);
        brightColor += extractBrightSample(texCoord + vec2(sampleOffset.x, -sampleOffset.y), minUv, maxUv);
        brightColor += extractBrightSample(texCoord + vec2(-sampleOffset.x, sampleOffset.y), minUv, maxUv);
        brightColor += extractBrightSample(texCoord + vec2(sampleOffset.x, sampleOffset.y), minUv, maxUv);
        fragColor = vec4(brightColor * 0.25, 1.0);
        return;
    }

    if (Pass < 2.5) {
        vec2 bloomTexelSize = 1.0 / vec2(textureSize(BloomSampler, 0));
        vec2 minUv = bloomTexelSize * 0.5;
        vec2 maxUv = vec2(1.0) - minUv;
        vec2 axis = Horizontal > 0.5 ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
        vec2 screenTexelSize = vec2(
            1.0 / max(ViewportWidth, 1.0),
            1.0 / max(ViewportHeight, 1.0)
        );
        vec2 sampleStep = axis * screenTexelSize * RadiusPixels * 0.5;
        vec3 blurredColor = texture(BloomSampler, texCoord).rgb * 0.38774;
        blurredColor += texture(BloomSampler, clamp(texCoord + sampleStep, minUv, maxUv)).rgb * 0.24477;
        blurredColor += texture(BloomSampler, clamp(texCoord - sampleStep, minUv, maxUv)).rgb * 0.24477;
        blurredColor += texture(BloomSampler, clamp(texCoord + sampleStep * 2.0, minUv, maxUv)).rgb * 0.06136;
        blurredColor += texture(BloomSampler, clamp(texCoord - sampleStep * 2.0, minUv, maxUv)).rgb * 0.06136;
        fragColor = vec4(blurredColor, 1.0);
        return;
    }

    vec4 sceneColor = texture(SceneSampler, texCoord);
    vec3 bloomColor = texture(BloomSampler, texCoord).rgb;
    fragColor = vec4(sceneColor.rgb + bloomColor * Intensity, sceneColor.a);
}
