#version 150

#moj_import <fog.glsl>

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
    point += dot(point, point + 45.32);
    return fract(point.x * point.y);
}

float smoothNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    float lower = mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x);
    float upper = mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0, 1.0)), local.x);
    return mix(lower, upper, local.y);
}

void main() {
    float progress = clamp(effectData.x, 0.0, 1.0);
    float seed = clamp(effectData.y, 0.0, 1.0);
    float brightestChannel = max(max(vertexColor.r, vertexColor.g), vertexColor.b);

    if (brightestChannel < 0.08) {
        // The event-horizon silhouette stays truly dark; the surrounding plasma defines its shape.
        fragColor = vec4(0.0002, 0.0001, 0.0005, 1.0);
        return;
    }

    vec2 centeredUv = (texCoord0 - vec2(0.5)) * 2.0;
    float radius = length(centeredUv);
    float angle = atan(centeredUv.y, centeredUv.x);
    vec2 angularDirection = vec2(cos(angle), sin(angle));
    float noiseA = smoothNoise(angularDirection * 4.0
        + vec2(seed * 9.0 - progress * 4.0, radius * 15.0));
    float noiseB = smoothNoise(angularDirection * 8.0
        + vec2(radius * 11.0 + seed * 3.0, radius * 31.0 - progress * 3.0));
    float density = noiseA * 0.62 + noiseB * 0.38;
    float spiral = 0.5 + 0.5 * sin(angle * 7.0 - radius * 42.0 + progress * 22.0
        + (noiseA - 0.5) * 4.0);
    float filament = pow(max(spiral, 0.0), 12.0);
    float innerRim = exp(-abs(radius - 0.337) * 72.0);
    float innerFade = smoothstep(0.305, 0.355, radius);
    float outerFade = 1.0 - smoothstep(0.96, 1.0, radius);
    float approachingSide = 0.74 + 0.26 * smoothstep(-0.9, 0.9, cos(angle - progress * 1.8));

    vec3 outerPlasma = mix(vec3(0.22, 0.012, 0.003), vec3(1.0, 0.24, 0.025), density);
    vec3 innerPlasma = mix(vec3(1.15, 0.38, 0.055), vec3(0.72, 0.88, 1.25),
        1.0 - smoothstep(0.34, 0.48, radius));
    vec3 color = mix(outerPlasma, innerPlasma, 1.0 - smoothstep(0.36, 0.86, radius));
    color += vec3(1.35, 1.12, 0.82) * innerRim;
    color += vec3(0.75, 0.43, 0.15) * filament * (0.35 + density);
    color *= approachingSide * vertexColor.rgb * ColorModulator.rgb;
    float alpha = vertexColor.a * innerFade * outerFade * (0.28 + density * 0.43 + filament * 0.30);

    fragColor = linear_fog(vec4(color, alpha * ColorModulator.a), vertexDistance, FogStart, FogEnd, FogColor);
}
