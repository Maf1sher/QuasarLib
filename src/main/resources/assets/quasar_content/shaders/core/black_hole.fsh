#version 150

uniform sampler2D SceneSampler;
uniform sampler2D RawSceneSampler;
uniform sampler2D MaskSampler;
uniform sampler2D DepthSampler;
uniform sampler2D EffectSampler;

uniform mat4 ViewMat;
uniform mat4 WorldProjMat;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;

uniform float ViewportWidth;
uniform float ViewportHeight;
uniform float MaskStrength;

uniform vec3 BlackHoleOffset;
uniform float BlackHoleRadius;
uniform float DiskInnerRadius;
uniform float DiskOuterRadius;
uniform float DiskThickness;
uniform vec3 DiskRight;
uniform vec3 DiskForward;
uniform vec3 DiskNormal;

uniform float AgeTicks;
uniform float Progress;
uniform float Seed;
uniform float Charge;
uniform float Fade;
uniform float LensStrength;
uniform float LensGravity;
uniform float RingStrength;
uniform float DiskBrightness;
uniform float Turbulence;
uniform float StepCount;

uniform float Pass;
uniform float BloomStrength;
uniform float BloomThreshold;
uniform float BloomRadiusPixels;

in vec2 texCoord;

out vec4 fragColor;

const int MAX_STEPS = 160;
const float PI = 3.14159265359;
const float DISK_OPACITY = 1.5;

float hash13(vec3 point) {
    point = fract(point * 0.1031);
    point += dot(point, point.yzx + 33.33);
    return fract((point.x + point.y) * point.z);
}

float valueNoise3(vec3 point) {
    vec3 cell = floor(point);
    vec3 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    float n000 = hash13(cell);
    float n100 = hash13(cell + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(cell + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(cell + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(cell + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(cell + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(cell + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(cell + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, local.x);
    float nx10 = mix(n010, n110, local.x);
    float nx01 = mix(n001, n101, local.x);
    float nx11 = mix(n011, n111, local.x);
    float nxy0 = mix(nx00, nx10, local.y);
    float nxy1 = mix(nx01, nx11, local.y);
    return mix(nxy0, nxy1, local.z);
}

float fbm3(vec3 point) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        value += amplitude * valueNoise3(point);
        point = point * 2.03 + vec3(1.7, 9.2, 3.4);
        amplitude *= 0.5;
    }
    return value;
}

vec3 rayDirection(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, -1.0, 1.0);
    vec4 view = InvProjMat * clip;
    view.xyz /= view.w;
    return normalize((InvViewMat * vec4(view.xyz, 0.0)).xyz);
}

float viewDistance(float depth, vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, depth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    if (abs(view.w) < 0.000001) {
        return 1000000.0;
    }
    view.xyz /= view.w;
    return clamp(length(view.xyz), 0.01, 1000000.0);
}

/** Distance from the camera to the entry point of a sphere, or -1 when the ray misses it. */
float raySphereEntry(vec3 origin, vec3 direction, float sphereRadius) {
    float b = dot(origin, direction);
    float c = dot(origin, origin) - sphereRadius * sphereRadius;
    float discriminant = b * b - c;
    if (discriminant < 0.0) {
        return -1.0;
    }
    float root = sqrt(discriminant);
    float nearHit = -b - root;
    float farHit = -b + root;
    if (farHit < 0.0) {
        return -1.0;
    }
    return max(nearHit, 0.0);
}

vec3 paletteColor(vec3 gold, vec3 amber, vec3 neon, float charge) {
    vec3 warm = mix(gold, amber, clamp(charge * 2.0, 0.0, 1.0));
    return mix(warm, neon, clamp((charge - 0.5) * 2.0, 0.0, 1.0));
}

/**
 * Samples the volumetric accretion disk at a point relative to the black hole.
 * Returns emission in rgb and optical density in a; the caller integrates density over the ray step.
 */
vec4 sampleDisk(vec3 point, vec3 cameraRelative) {
    float height = dot(point, DiskNormal);
    vec3 radialVector = point - DiskNormal * height;
    float radial = length(radialVector);
    if (radial < DiskInnerRadius || radial > DiskOuterRadius) {
        return vec4(0.0);
    }

    float t = (radial - DiskInnerRadius) / max(DiskOuterRadius - DiskInnerRadius, 0.001);
    float edge = smoothstep(0.0, 0.06, t) * (1.0 - smoothstep(0.72, 1.0, t));
    if (edge <= 0.001) {
        return vec4(0.0);
    }

    float normalizedRadius = clamp(radial / max(DiskOuterRadius, 0.001), 0.0, 1.0);
    float halfThickness = max(DiskThickness * (0.35 + 0.65 * normalizedRadius), 0.001);
    float vertical = exp(-(height * height) / (halfThickness * halfThickness));
    if (vertical <= 0.001) {
        return vec4(0.0);
    }

    vec3 radialDir = radialVector / max(radial, 0.001);
    float phi = atan(dot(radialDir, DiskForward), dot(radialDir, DiskRight));
    float spin = AgeTicks * 0.035
        * pow(max(DiskInnerRadius, 0.001) / max(radial, 0.001), 1.5);
    phi -= spin;

    float turbulenceScale = mix(0.7, 1.5, Turbulence);
    vec2 ringPosition = vec2(cos(phi), sin(phi))
        * (radial / max(DiskOuterRadius, 0.001)) * 7.0;
    vec3 noisePosition = vec3(ringPosition * 1.7, AgeTicks * 0.012 + Seed * 31.0);
    float coarse = fbm3(noisePosition * (1.0 + 0.6 * turbulenceScale));
    float fine = fbm3(noisePosition * (2.6 + 0.9 * turbulenceScale) + vec3(11.0, 7.0, 3.0));

    float density = edge * (0.28 + 1.05 * coarse) * (0.55 + 0.65 * fine);
    float dust = smoothstep(0.34, 0.68, fine);
    density *= mix(0.45, 1.0, dust);

    float knots = pow(max(fine - 0.58, 0.0) * 2.4, 3.0);
    float stream = pow(max(cos(phi * 3.0 - radial * 0.08 + AgeTicks * 0.02), 0.0), 12.0);
    density *= (0.85 + 0.4 * stream) * vertical;
    knots += stream * 0.4;

    vec3 tangent = normalize(cross(DiskNormal, radialDir));
    vec3 viewDir = normalize(cameraRelative - point);
    float approach = dot(tangent, viewDir);
    float doppler = clamp(1.0 + 0.85 * approach, 0.35, 1.9);

    vec3 inner = paletteColor(
        vec3(1.0, 0.94, 0.76),
        vec3(1.0, 0.98, 0.90),
        vec3(0.78, 0.97, 1.0),
        Charge
    );
    vec3 middle = paletteColor(
        vec3(1.0, 0.58, 0.18),
        vec3(1.0, 0.38, 0.09),
        vec3(0.38, 0.78, 1.0),
        Charge
    );
    vec3 outer = paletteColor(
        vec3(0.42, 0.16, 0.05),
        vec3(0.36, 0.06, 0.02),
        vec3(0.22, 0.08, 0.62),
        Charge
    );
    vec3 color = mix(inner, middle, smoothstep(0.0, 0.55, t));
    color = mix(color, outer, smoothstep(0.45, 1.0, t));
    color = mix(color, color * vec3(0.80, 0.90, 1.20), max(approach, 0.0) * 0.25);
    color = mix(color, color * vec3(1.20, 0.82, 0.65), max(-approach, 0.0) * 0.25);
    color = mix(color, vec3(1.0, 0.97, 0.90), (1.0 - smoothstep(0.0, 0.16, t)) * 0.7);

    vec3 emission = color * (0.72 + 1.4 * knots) * doppler * DiskBrightness;
    return vec4(emission, density);
}

vec4 renderBlackHole(vec2 uv) {
    vec4 sceneColor = texture(SceneSampler, uv);
    // Background lensing must read the untouched frame. Sampling the current
    // composition would make stacked black holes see each other's lens output
    // and produce a recursive picture-in-picture.
    vec4 rawSceneColor = texture(RawSceneSampler, uv);
    float coverage = clamp(texture(MaskSampler, uv).r * MaskStrength, 0.0, 1.0);
    if (coverage < 0.002 || Fade <= 0.001) {
        return vec4(sceneColor.rgb, 0.0);
    }

    vec4 holeView = ViewMat * vec4(BlackHoleOffset, 0.0);
    if (holeView.z >= -0.001) {
        return vec4(sceneColor.rgb, 0.0);
    }
    vec4 holeClip = WorldProjMat * holeView;
    if (holeClip.w <= 0.0) {
        return vec4(sceneColor.rgb, 0.0);
    }
    vec2 holeUv = holeClip.xy / holeClip.w * 0.5 + 0.5;
    float projectedRadius = abs(WorldProjMat[0][0] * DiskOuterRadius / holeView.z)
        * max(ViewportWidth, 1.0) * 0.5;
    vec2 viewport = vec2(max(ViewportWidth, 1.0), max(ViewportHeight, 1.0));
    if (length((uv - holeUv) * viewport) > projectedRadius * 1.6 + 8.0) {
        return vec4(sceneColor.rgb, 0.0);
    }

    float sceneDistance = viewDistance(texture(DepthSampler, uv).r, uv);
    vec3 direction = rayDirection(uv);
    vec3 cameraRelative = -BlackHoleOffset;

    float radius = max(BlackHoleRadius, 0.001);
    float shadowImpact = 2.6 * radius;
    float impact = length(cross(BlackHoleOffset, direction));
    float holeDistance = length(BlackHoleOffset);
    bool forward = dot(-BlackHoleOffset, direction) > 0.0;

    // Start inside the effect's bounding volume so traversal cost never depends on camera distance.
    // Keep the entry radius beyond the screen-fade footprint so the outer halo is never clipped.
    float entryRadius = max(DiskOuterRadius * 1.5, radius * 16.0);
    float entryDistance = raySphereEntry(cameraRelative, direction, entryRadius);
    if (entryDistance < 0.0) {
        return vec4(sceneColor.rgb, 0.0);
    }

    // The flat mask no longer resolves foreground occlusion; compare scene depth per pixel.
    float shadowEntry = raySphereEntry(cameraRelative, direction, shadowImpact);
    bool shadowOccluded = shadowEntry >= 0.0 && sceneDistance < shadowEntry - 0.05;
    float lensEntry = raySphereEntry(cameraRelative, direction, radius * 8.0);
    bool lensOccluded = lensEntry >= 0.0 && sceneDistance < lensEntry - 0.05;

    vec3 position = cameraRelative + direction * entryDistance;
    vec3 velocity = direction;
    float travelled = entryDistance;
    float alpha = 0.0;
    vec3 emission = vec3(0.0);
    bool captured = false;

    int steps = int(StepCount + 0.5);
    for (int stepIndex = 0; stepIndex < MAX_STEPS; stepIndex++) {
        if (stepIndex >= steps) {
            break;
        }

        float distanceToHole = length(position);
        if (distanceToHole < radius * 1.03) {
            captured = true;
            break;
        }
        if (distanceToHole > radius * 32.0 && dot(position, velocity) > 0.0) {
            break;
        }

        float stepLength = distanceToHole > radius * 8.0
            ? clamp(0.12 * distanceToHole, 0.5 * radius, 1.2 * radius)
            : clamp(0.04 * distanceToHole, 0.02 * radius, 0.25 * radius);
        vec3 previous = position;
        position += velocity * stepLength;

        float squaredDistance = max(dot(position, position), radius * radius * 0.2);
        vec3 acceleration = -normalize(position) * (radius * LensGravity) / squaredDistance;
        velocity = normalize(velocity + acceleration * stepLength * LensStrength);
        travelled += stepLength;

        float sampleDistance = travelled - stepLength * 0.5;
        if (alpha < 0.985 && sampleDistance < sceneDistance + 0.5) {
            vec4 disk = sampleDisk(mix(previous, position, 0.5), cameraRelative);
            float opacity = 1.0 - exp(-max(disk.a, 0.0) * stepLength * DISK_OPACITY);
            emission += disk.rgb * opacity * (1.0 - alpha);
            alpha += opacity * (1.0 - alpha);
        }
    }

    vec3 lensedScene = rawSceneColor.rgb;
    float lensAmount = 0.0;
    if (!captured && !lensOccluded) {
        vec2 lensUv = uv;
        vec4 clip = WorldProjMat * (ViewMat * vec4(velocity, 0.0));
        if (clip.w > 0.0001) {
            lensUv = clip.xy / clip.w * 0.5 + 0.5;
        }

        vec2 lensDelta = lensUv - uv;
        float maxLensDelta = 0.02;
        float lensDeltaLength = length(lensDelta);
        if (lensDeltaLength > maxLensDelta) {
            lensDelta *= maxLensDelta / lensDeltaLength;
        }
        lensUv = clamp(uv + lensDelta, vec2(0.0), vec2(1.0));

        float edgeFade = smoothstep(0.0, 0.06, lensUv.x)
            * smoothstep(0.0, 0.06, 1.0 - lensUv.x)
            * smoothstep(0.0, 0.06, lensUv.y)
            * smoothstep(0.0, 0.06, 1.0 - lensUv.y);
        lensedScene = texture(RawSceneSampler, lensUv).rgb;

        float deflection = acos(clamp(dot(normalize(velocity), normalize(direction)), -1.0, 1.0));
        float lensFalloff = 1.0 - smoothstep(2.0 * radius, 8.0 * radius, impact);
        lensAmount = clamp(deflection * 2.4, 0.0, 1.0)
            * clamp(LensStrength * 0.6, 0.0, 1.0) * lensFalloff * edgeFade * 0.65;
    }

    bool analyticShadow = forward && impact < shadowImpact && holeDistance > shadowImpact * 1.05;
    bool insideShadow = !shadowOccluded && (captured || analyticShadow);
    vec3 background = insideShadow
        ? vec3(0.0)
        : mix(sceneColor.rgb, lensedScene, lensAmount);
    vec3 color = emission + background * (1.0 - clamp(alpha, 0.0, 1.0));

    float stabilization = smoothstep(0.0, 0.10, Progress)
        * (1.0 - smoothstep(0.86, 1.0, Progress));
    float ringVisibility = shadowOccluded ? 0.0 : 1.0;
    float pixelWorld = max(0.0001,
        2.0 * abs(holeView.z) / max(abs(WorldProjMat[1][1]) * ViewportHeight, 1.0));
    float ringWidth = max(0.045 * radius, pixelWorld * 1.5);
    float primaryOffset = (impact - shadowImpact) / ringWidth;
    float secondaryOffset = (impact - shadowImpact - 0.38 * radius) / (ringWidth * 1.6);
    float tertiaryOffset = (impact - shadowImpact - 0.78 * radius) / (ringWidth * 2.2);
    float primaryRing = exp(-primaryOffset * primaryOffset);
    float secondaryRing = exp(-secondaryOffset * secondaryOffset) * 0.30;
    float tertiaryRing = exp(-tertiaryOffset * tertiaryOffset) * 0.12;
    vec3 ringColor = mix(vec3(1.0, 0.93, 0.78), vec3(0.72, 0.92, 1.0), Charge);
    float pulse = 0.85 + 0.15 * sin(AgeTicks * 0.08 + Seed * 6.2831853);
    vec3 ringLight = ringColor * (primaryRing + secondaryRing + tertiaryRing)
        * RingStrength * pulse * (1.0 - alpha * 0.75) * stabilization * ringVisibility;
    color += ringLight / (vec3(1.0) + ringLight);

    float haloOffset = (impact - shadowImpact) / (3.5 * radius);
    float halo = exp(-haloOffset * haloOffset) * 0.08;
    vec3 haloColor = mix(vec3(1.0, 0.45, 0.15), vec3(0.5, 0.2, 1.0), Charge);
    vec3 haloLight = haloColor * halo * RingStrength * 0.25 * stabilization * ringVisibility;
    color += haloLight / (vec3(1.0) + haloLight);

    float diskPresence = clamp(alpha * 6.0, 0.0, 1.0);
    float effectStrength = insideShadow ? 1.0 : 0.0;
    effectStrength = max(effectStrength, diskPresence);
    effectStrength = max(effectStrength, lensAmount);
    effectStrength = max(effectStrength,
        clamp((primaryRing + secondaryRing + tertiaryRing) * 0.8, 0.0, 1.0) * ringVisibility);
    effectStrength = max(effectStrength, halo * 2.0 * ringVisibility);
    float screenRadius = length((uv - holeUv) * viewport);
    float screenFade = 1.0 - smoothstep(
        projectedRadius * 1.10, projectedRadius * 1.45, screenRadius
    );
    return vec4(
        mix(sceneColor.rgb, color, coverage),
        coverage * clamp(effectStrength, 0.0, 1.0) * screenFade
    );
}

void main() {
    if (Pass < 0.5) {
        fragColor = renderBlackHole(texCoord);
        return;
    }

    vec4 sceneColor = texture(SceneSampler, texCoord);
    vec4 effectColor = texture(EffectSampler, texCoord);
    vec2 effectTexel = vec2(
        1.0 / max(ViewportWidth, 1.0),
        1.0 / max(ViewportHeight, 1.0)
    );
    vec3 softEffect = effectColor.rgb * 0.40;
    softEffect += texture(EffectSampler, clamp(texCoord + vec2(effectTexel.x, 0.0), vec2(0.0), vec2(1.0))).rgb * 0.15;
    softEffect += texture(EffectSampler, clamp(texCoord - vec2(effectTexel.x, 0.0), vec2(0.0), vec2(1.0))).rgb * 0.15;
    softEffect += texture(EffectSampler, clamp(texCoord + vec2(0.0, effectTexel.y), vec2(0.0), vec2(1.0))).rgb * 0.15;
    softEffect += texture(EffectSampler, clamp(texCoord - vec2(0.0, effectTexel.y), vec2(0.0), vec2(1.0))).rgb * 0.15;
    vec3 center = mix(sceneColor.rgb, softEffect, effectColor.a * Fade);
    if (BloomStrength <= 0.0) {
        fragColor = vec4(center, 1.0);
        return;
    }

    vec2 texel = vec2(
        1.0 / max(ViewportWidth, 1.0),
        1.0 / max(ViewportHeight, 1.0)
    );
    vec3 bloom = vec3(0.0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 offset = vec2(float(x), float(y)) * texel * BloomRadiusPixels;
            vec3 sampleColor = texture(
                EffectSampler,
                clamp(texCoord + offset, vec2(0.0), vec2(1.0))
            ).rgb;
            bloom += max(sampleColor - vec3(BloomThreshold), vec3(0.0));
        }
    }
    bloom /= 9.0;
    fragColor = vec4(center + bloom * BloomStrength * effectColor.a * Fade, 1.0);
}
