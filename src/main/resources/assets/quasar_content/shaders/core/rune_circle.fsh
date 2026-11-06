#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
flat in vec2 effectData;

out vec4 fragColor;

float hash21(vec2 point) {
    point = fract(point * vec2(123.34, 456.21));
    point += dot(point, point + 34.345);
    return fract(point.x * point.y);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    return mix(
        mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x),
        mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0, 1.0)), local.x),
        local.y
    );
}

void main() {
    float progress = clamp(effectData.x, 0.0, 1.0);
    float seed = clamp(effectData.y, 0.0, 1.0);
    vec2 noiseOffset = vec2(seed * 47.0 + progress * 73.0, seed * 83.0 - progress * 61.0);
    float noise = valueNoise(texCoord0 * 28.0 + noiseOffset);

    vec2 centeredUv = texCoord0 - vec2(0.5);
    float radius = length(centeredUv);
    float angle = atan(centeredUv.y, centeredUv.x);
    float ringDistance = min(abs(radius - 0.395), min(abs(radius - 0.285), abs(radius - 0.11)));
    float ringEdge = 1.0 - smoothstep(0.01, 0.04, ringDistance);
    float flicker = 0.55 + 0.45 * sin(angle * 17.0 + progress * 180.0 + seed * 6.2831853);

    vec4 texel = texture(Sampler0, texCoord0);
    float sourceAlpha = texel.a * vertexColor.a * ColorModulator.a;
    sourceAlpha *= (0.58 + noise * 0.84) * mix(1.0, flicker, ringEdge * 0.9);

    float outerGlow = exp(-abs(radius - 0.395) * 34.0) * 0.2;
    float middleGlow = exp(-abs(radius - 0.285) * 42.0) * 0.17;
    float innerGlow = exp(-abs(radius - 0.11) * 54.0) * 0.13;
    float glowPulse = 0.7 + 0.3 * sin(progress * 16.0 + seed * 6.2831853);
    float glowAlpha = (outerGlow + middleGlow + innerGlow) * glowPulse * (0.68 + noise * 0.32);
    glowAlpha *= vertexColor.a * ColorModulator.a;

    float combinedAlpha = sourceAlpha + glowAlpha * (1.0 - sourceAlpha);
    vec3 sourceColor = texel.rgb * vertexColor.rgb * ColorModulator.rgb * (0.55 + noise * 0.9);
    vec3 glowColor = vec3(0.18, 0.72, 1.0);
    vec3 combinedColor = (sourceColor * sourceAlpha
        + glowColor * glowAlpha * (1.0 - sourceAlpha)) / max(combinedAlpha, 0.0001);

    vec4 color = vec4(combinedColor, combinedAlpha);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
