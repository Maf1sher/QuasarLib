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

const float TAU = 6.28318530718;
const float PI = 3.14159265359;

float quasarRing(float radius, float centre, float halfWidth) {
    return 1.0 - smoothstep(halfWidth, halfWidth + 0.018, abs(radius - centre));
}

void main() {
    vec2 local = (texCoord0 - vec2(0.5)) * 2.0;
    float radius = length(local);
    float angle = atan(local.y, local.x);
    float lock = clamp(effectData.x, 0.0, 1.0);
    float seed = clamp(effectData.y, 0.0, 1.0);
    float antialias = max(fwidth(radius) * 1.5, 0.002);

    float outerRing = quasarRing(radius, 0.92, 0.028);
    float innerRing = quasarRing(radius, 0.58, 0.014);
    float axis = min(abs(local.x), abs(local.y));
    float crosshair = (1.0 - smoothstep(0.008, 0.008 + antialias * 3.0, axis))
        * smoothstep(0.18, 0.20, radius)
        * (1.0 - smoothstep(0.78, 0.80, radius));
    float centreDot = 1.0 - smoothstep(0.045, 0.045 + antialias * 3.0, radius);

    float sweepStart = -PI * 0.5 + seed * TAU;
    float sweepProgress = lock * TAU;
    float sweepDistance = mod(angle - sweepStart, TAU);
    float sweepHead = 1.0 - smoothstep(0.0, 0.09, abs(sweepDistance - sweepProgress));
    float sweep = 0.0;
    if (lock > 0.001 && lock < 0.999) {
        sweep = (1.0 - smoothstep(sweepProgress, sweepProgress + antialias, sweepDistance))
            * quasarRing(radius, 0.75, 0.009)
            * (0.35 + 0.65 * sweepHead);
    }

    float shape = max(max(outerRing, innerRing), max(crosshair, max(centreDot, sweep)));
    float glow = exp(-abs(radius - 0.92) * 22.0) * 0.16;
    float alpha = clamp(shape + glow, 0.0, 1.0) * vertexColor.a * ColorModulator.a;
    vec4 texel = texture(Sampler0, texCoord0);
    alpha *= texel.a;

    vec3 red = vec3(1.0, 0.07, 0.035);
    vec3 hot = vec3(1.0, 0.50, 0.26);
    float heat = clamp(centreDot + sweep * 0.6, 0.0, 1.0);
    vec3 color = mix(red, hot, heat) * vertexColor.rgb * ColorModulator.rgb;
    fragColor = linear_fog(vec4(color, alpha), vertexDistance, FogStart, FogEnd, FogColor);
}
