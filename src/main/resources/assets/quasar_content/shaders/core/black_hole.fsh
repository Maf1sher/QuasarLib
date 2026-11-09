#version 150

uniform sampler2D SceneSampler;
uniform sampler2D RawSceneSampler;
uniform sampler2D OpaqueColorSampler;
uniform sampler2D ForegroundDepthSampler;
uniform sampler2D MaskSampler;
uniform sampler2D DepthSampler;
uniform sampler2D EffectSampler;
uniform sampler2D BloomSampler;

uniform mat4 ViewMat;
uniform mat4 WorldProjMat;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;

uniform float ViewportWidth;
uniform float ViewportHeight;
uniform float MaskStrength;

uniform vec3 BlackHoleOffset;
uniform float BlackHoleRadius;
uniform float LensRadius;
uniform float LensWarpRadius;
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
uniform float RingFormation;
uniform float ShockRadius;
uniform float ShockStrength;
uniform float ShockAbsorb;
uniform float CoreFlash;
uniform float FlashRadius;
uniform float OutroJet;
uniform float VortexStrength;
uniform float VortexRadius;
uniform float Darkness;
uniform float LensStrength;
uniform float RingStrength;
uniform float DiskBrightness;
uniform float Turbulence;
uniform float StepCount;

uniform float MediumFogStart;
uniform float MediumFogEnd;
uniform vec3 MediumFogColor;
uniform float MediumFogActive;
uniform float CameraSubmerged;
uniform float EffectSubmerged;
uniform float MediumPathLength;
uniform float ForegroundDepthValid;
uniform float OpaqueColorValid;
uniform float SurfaceY;
uniform float SurfaceActive;
uniform float SuppressAboveWater;

uniform float Pass;
uniform float EmissiveOnly;
uniform float BloomStrength;
uniform float BloomThreshold;
uniform float BloomRadiusPixels;

in vec2 texCoord;

out vec4 fragColor;

const int MAX_STEPS = 256;
const float PI = 3.14159265359;
const float DISK_OPACITY = 1.5;
const float B_CRITICAL = 2.59807621135;

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
    // Sample the far plane, not the near plane. The projection can carry a view-space
    // bob/hurt translation; inverting it on the 0.05-block near plane amplifies that
    // translation into a large angular error. A far-plane point keeps the true direction.
    vec4 clip = vec4(ndc, 1.0, 1.0);
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

/**
 * Detects a translucent layer at a pixel. Post-translucent depth is preferred,
 * but a color delta between the scene before and after translucency catches
 * glass/water whose depth write was skipped by the pipeline or a shaderpack.
 */
bool foregroundLayer(vec2 uv, float entryDistance, float tolerance) {
    if (ForegroundDepthValid > 0.5) {
        float foreground = viewDistance(texture(ForegroundDepthSampler, uv).r, uv);
        if (foreground < entryDistance - tolerance) {
            return true;
        }
    }
    vec3 opaque = texture(OpaqueColorSampler, uv).rgb;
    vec3 seen = texture(RawSceneSampler, uv).rgb;
    return distance(seen, opaque) > 0.02;
}

/**
 * Returns the attenuation for the effect's own emission behind a foreground
 * translucent layer. The background is never tinted: it is the scene the player
 * already sees through the glass, and tinting it produces a dark halo.
 */
vec3 foregroundTintAt(vec2 uv, float entryDistance, float tolerance) {
    if (!foregroundLayer(uv, entryDistance, tolerance)) {
        return vec3(1.0);
    }
    vec3 ratio = vec3(1.0);
    if (OpaqueColorValid > 0.5) {
        vec3 opaque = texture(OpaqueColorSampler, uv).rgb;
        vec3 seen = texture(RawSceneSampler, uv).rgb;
        ratio = clamp(seen / max(opaque, vec3(0.02)), vec3(0.0), vec3(1.0));
    }
    // A detected layer always attenuates a little; bright stained glass can
    // return 1.0 per channel and would otherwise never darken the effect.
    return ratio * 0.80;
}

/** Upper bound for the per-pixel effect entry stored in the raymarch alpha channel. */
float effectDepthScale(float holeDistance, float lensRadius) {
    return max(holeDistance + 2.0 * lensRadius, 1.0);
}

/**
 * The raymarch pass stores the nearest traced entry distance in alpha. A logarithmic
 * mapping keeps sub-block precision across both close and horizon-distance effects;
 * linear 8-bit storage loses too much accuracy for effects hundreds of blocks away.
 */
float encodeEffectDepth(float entry, float depthScale) {
    float scale = max(depthScale, 1.0);
    return clamp(log2(1.0 + max(entry, 0.0)) / log2(1.0 + scale), 0.0, 1.0);
}

float decodeEffectDepth(float encoded, float depthScale) {
    float scale = max(depthScale, 1.0);
    return max(exp2(clamp(encoded, 0.0, 1.0) * log2(1.0 + scale)) - 1.0, 0.0);
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

bool projectDirection(vec3 direction, out vec2 uv) {
    vec4 clip = WorldProjMat * (ViewMat * vec4(direction, 0.0));
    if (clip.w <= 0.0001) {
        return false;
    }
    uv = clip.xy / clip.w * 0.5 + 0.5;
    return all(greaterThanEqual(uv, vec2(-0.25))) && all(lessThanEqual(uv, vec2(1.25)));
}

/** Projects a camera-relative point; returns a negative UV when it is behind the camera. */
vec2 projectPoint(vec3 point) {
    vec4 clip = WorldProjMat * vec4(mat3(ViewMat) * point, 1.0);
    if (clip.w <= 0.0001) {
        return vec2(-1.0);
    }
    return clip.xy / clip.w * 0.5 + 0.5;
}

struct SceneRay {
    vec3 origin;
    vec3 direction;
    vec3 normal;
    float surfaceDistance;
    float incidenceCosine;
    bool totalInternalReflection;
};

/**
 * World-stable water ripples. The point must be relative to the black hole so the
 * wave phase does not slide when the camera moves.
 */
vec3 waterSurfaceNormal(vec3 holeRelative) {
    float time = AgeTicks * 0.05;
    vec2 p = holeRelative.xz;
    float slopeX = sin(p.x * 0.62 + time * 1.7)
        + 0.6 * sin(p.x * 1.37 - p.y * 0.41 - time * 1.1)
        + 0.35 * sin(p.y * 2.10 + time * 2.3);
    float slopeZ = cos(p.y * 0.58 - time * 1.5)
        + 0.6 * cos(p.y * 1.29 + p.x * 0.37 + time * 0.9)
        + 0.35 * cos(p.x * 1.90 - time * 2.1);
    // Keep the synthetic ripples subtle: they cannot match the vanilla water
    // animation, so a large tilt reads as a wobbling reflection.
    return normalize(vec3(slopeX * 0.02, 1.0, slopeZ * 0.02));
}

/** Unpolarised Fresnel reflectance; approaches one at the critical angle. */
float fresnelWater(float cosIncidence, float incidentIor, float transmittedIor) {
    float cosI = clamp(abs(cosIncidence), 0.0, 1.0);
    float sinT2 = (incidentIor * incidentIor) / (transmittedIor * transmittedIor)
        * max(0.0, 1.0 - cosI * cosI);
    if (sinT2 >= 1.0) {
        return 1.0;
    }
    float cosT = sqrt(1.0 - sinT2);
    float rs = (incidentIor * cosI - transmittedIor * cosT)
        / max(incidentIor * cosI + transmittedIor * cosT, 0.0001);
    float rp = (incidentIor * cosT - transmittedIor * cosI)
        / max(incidentIor * cosT + transmittedIor * cosI, 0.0001);
    return clamp(0.5 * (rs * rs + rp * rp), 0.0, 1.0);
}

/**
 * Bends the view ray at the active water plane. {@code origin} is the camera
 * relative to the black hole while {@code SurfaceY} is relative to the camera;
 * the returned origin is relative to the hole so the geodesic integration stays
 * in one frame. The screen-space lens and vortex are skipped across the surface,
 * because the scene texture was rendered along straight camera rays.
 */
SceneRay refractAtWater(vec3 origin, vec3 direction) {
    SceneRay ray;
    ray.origin = origin;
    ray.direction = direction;
    ray.normal = vec3(0.0, 1.0, 0.0);
    ray.surfaceDistance = -1.0;
    ray.incidenceCosine = 1.0;
    ray.totalInternalReflection = false;
    if (SurfaceActive < 0.5 || abs(direction.y) < 0.001) {
        return ray;
    }
    float surfaceDistance = SurfaceY / direction.y;
    if (surfaceDistance <= 0.0) {
        return ray;
    }
    vec3 hit = origin + direction * surfaceDistance;
    vec3 normal = waterSurfaceNormal(hit);
    if (dot(normal, direction) > 0.0) {
        normal = -normal;
    }
    ray.normal = normal;
    ray.incidenceCosine = clamp(abs(dot(direction, normal)), 0.0, 1.0);
    float eta = CameraSubmerged > 0.5 ? 1.333 : 1.0 / 1.333;
    vec3 bent = refract(direction, normal, eta);
    ray.surfaceDistance = surfaceDistance;
    if (dot(bent, bent) < 0.000001) {
        // Total internal reflection from below the surface: the above-water
        // effect is not visible in this direction, so the caller hides it.
        ray.totalInternalReflection = true;
        return ray;
    }
    ray.origin = hit;
    ray.direction = normalize(bent);
    return ray;
}

vec3 paletteColor(vec3 gold, vec3 amber, vec3 neon, float charge) {
    vec3 warm = mix(gold, amber, clamp(charge * 2.0, 0.0, 1.0));
    return mix(warm, neon, clamp((charge - 0.5) * 2.0, 0.0, 1.0));
}

/**
 * Samples the volumetric accretion disk at a point relative to the black hole.
 * Returns emission in rgb and optical density in a; the caller integrates density
 * over the disk thickness at a plane crossing.
 */
vec4 sampleDisk(vec3 point, vec3 cameraRelative) {
    float height = dot(point, DiskNormal);
    vec3 radialVector = point - DiskNormal * height;
    float radial = length(radialVector);
    if (radial < DiskInnerRadius || radial > DiskOuterRadius) {
        return vec4(0.0);
    }

    float t = (radial - DiskInnerRadius) / max(DiskOuterRadius - DiskInnerRadius, 0.001);
    float edge = smoothstep(0.0, 0.015, t) * (1.0 - smoothstep(0.72, 1.0, t));
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

    // The ring is accelerated matter: it appears as a wrapping segment after the
    // horizon exists, and retreats into the drain before the horizon collapses.
    float phiNorm = fract((phi + PI) / (2.0 * PI));
    float wrap = smoothstep(0.0, 1.0,
        clamp((RingFormation * 1.35 - phiNorm) / 0.25, 0.0, 1.0));

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
    density *= wrap;
    knots += stream * 0.4;

    vec3 tangent = normalize(cross(DiskNormal, radialDir));
    vec3 toCamera = cameraRelative - point;
    float viewLength = length(toCamera);
    vec3 viewDir = viewLength > 0.0001 ? toCamera / viewLength : DiskNormal;
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

vec3 geodesicAcceleration(vec3 position, float h2, float horizonRadius) {
    float r2 = max(dot(position, position), 0.00000001);
    float r = sqrt(r2);
    float scale = -1.5 * horizonRadius * h2 / max(r2 * r2 * r, 0.0000000001);
    return position * scale;
}

float geodesicStep(float radius, float horizonRadius) {
    if (radius < 6.0 * horizonRadius) {
        return clamp(0.045 * radius, 0.008 * horizonRadius, 0.20 * horizonRadius);
    }
    return clamp(0.10 * radius, 0.02 * horizonRadius, 1.5 * horizonRadius);
}

/**
 * Integrates one disk slab crossing. The crossing is found analytically between
 * the two geodesic samples, so a thin disk cannot be stepped over. The first
 * emitting crossing also records its camera distance, which lets the composite
 * depth-test the disk surface instead of a bounding-sphere entry.
 */
void accumulateDisk(vec3 before, vec3 after, vec3 cameraRelative, vec3 rayVelocity,
    inout vec3 emission, inout float transmittance, inout float nearestDiskEntry) {
    float beforeHeight = dot(before, DiskNormal);
    float afterHeight = dot(after, DiskNormal);
    float segmentLength = length(after - before);
    // Coplanar / slab rays never change the sign of the disk-plane distance. Sample
    // the volume while the segment is inside the slab instead of dropping the disk.
    if (abs(beforeHeight) <= DiskThickness && abs(afterHeight) <= DiskThickness) {
        vec3 midpoint = mix(before, after, 0.5);
        vec4 slabDisk = sampleDisk(midpoint, cameraRelative);
        float slabOpacity = 1.0 - exp(-max(slabDisk.a, 0.0) * segmentLength * DISK_OPACITY);
        if (slabOpacity > 0.001) {
            nearestDiskEntry = min(nearestDiskEntry, length(midpoint - cameraRelative));
        }
        emission += slabDisk.rgb * slabOpacity * transmittance;
        transmittance *= (1.0 - slabOpacity);
        return;
    }
    if (beforeHeight * afterHeight > 0.0 || abs(beforeHeight - afterHeight) < 0.000001) {
        return;
    }
    float t = clamp(beforeHeight / (beforeHeight - afterHeight), 0.0, 1.0);
    vec3 hit = mix(before, after, t);
    float radial = length(hit - DiskNormal * dot(hit, DiskNormal));
    if (radial < DiskInnerRadius || radial > DiskOuterRadius) {
        return;
    }

    vec4 disk = sampleDisk(hit, cameraRelative);
    float velocityLength = length(rayVelocity);
    vec3 rayDirection = velocityLength > 0.000001 ? rayVelocity / velocityLength : DiskNormal;
    float cosTheta = abs(dot(rayDirection, DiskNormal));
    float thickness = max(DiskThickness, 0.001);
    float pathLength = clamp(2.0 * thickness / max(cosTheta, 0.15), 0.02, 8.0 * thickness);
    float opacity = 1.0 - exp(-max(disk.a, 0.0) * pathLength * DISK_OPACITY);
    if (opacity > 0.001) {
        nearestDiskEntry = min(nearestDiskEntry, length(hit - cameraRelative));
    }
    emission += disk.rgb * opacity * transmittance;
    transmittance *= (1.0 - opacity);
}

vec4 renderBlackHole(vec2 uv) {
    vec4 sceneColor = texture(SceneSampler, uv);
    if (SuppressAboveWater > 0.5) {
        // An above-water hole seen from below cannot be represented by a
        // screen-space refraction without reading as a floating reflection.
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }
    float coverage = clamp(texture(MaskSampler, uv).r * MaskStrength, 0.0, 1.0) * Fade;
    if (coverage < 0.002) {
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }

    vec3 direction = rayDirection(uv);
    vec3 cameraRelative = -BlackHoleOffset;
    SceneRay ray = refractAtWater(cameraRelative, direction);
    if (ray.totalInternalReflection) {
        // The water surface mirrors the underwater scene; an above-water hole
        // cannot contribute here. This removes the floating reflection-like patch.
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }
    vec3 traceOrigin = ray.origin;
    vec3 traceDirection = ray.direction;
    float holeDistance = length(BlackHoleOffset);
    float radius = max(BlackHoleRadius, 0.001);
    float lensRadius = max(LensRadius, radius * 3.0);
    float depthScale = effectDepthScale(holeDistance, lensRadius);
    float tEntry = raySphereEntry(traceOrigin, traceDirection, lensRadius);
    bool insideLens = length(traceOrigin) <= lensRadius
        || length(cameraRelative) <= lensRadius;
    if (tEntry < 0.0 && !insideLens) {
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }

    float impact = length(cross(traceOrigin, traceDirection));
    float shadowImpact = radius * B_CRITICAL;
    // The background lens fades out well before the disk shell. This keeps the
    // cinematic shadow and disk but stops the huge circular space-warp footprint.
    float warpOuter = clamp(LensWarpRadius, shadowImpact + 0.001, lensRadius);
    float warpFalloff = 1.0 - smoothstep(shadowImpact, warpOuter, impact);
    // Daytime skies are uniform and bright; warping them creates a large pale
    // circle. Keep most of the warp for terrain and objects, but soften the sky.
    float straightDepth = texture(DepthSampler, uv).r;
    float skyFactor = smoothstep(0.9995, 1.0, straightDepth);
    float skyWarp = mix(1.0, 0.35, skyFactor);
    float edge = 1.0 - smoothstep(lensRadius * 0.88, lensRadius * 1.04, impact);
    coverage *= edge;
    if (coverage < 0.002) {
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }

    if (length(cameraRelative) <= radius * 1.02) {
        // The camera is inside the horizon: keep the frame stable and dark. An entry
        // of zero keeps the effect visible for any scene depth.
        return vec4(vec3(0.02, 0.01, 0.03), 0.0);
    }

    vec3 position = traceOrigin + traceDirection * max(tEntry, 0.0);
    vec3 velocity = traceDirection;
    float h2 = dot(cross(traceOrigin, traceDirection), cross(traceOrigin, traceDirection));
    float escapeRadius = max(max(DiskOuterRadius * 1.5, radius * 48.0),
        length(traceOrigin) * 1.25);
    float transmittance = 1.0;
    vec3 emission = vec3(0.0);
    bool captured = false;
    int steps = int(StepCount + 0.5);
    // Track the point where the traced path is nearest the hole. Its distance from the
    // camera is the real occluder for the shadow, ring, and lensed background. Ring and
    // lens pixels must not use the lens-shell entry: that surface sits far in front of
    // the hole and would paint the effect over entities standing in front of it.
    float closestHoleRadius = 1.0e30;
    float closestEntry = max(tEntry, 0.0);
    float nearestDiskEntry = depthScale;

    for (int stepIndex = 0; stepIndex < MAX_STEPS; stepIndex++) {
        if (stepIndex >= steps) {
            break;
        }
        float distanceToHole = length(position);
        if (distanceToHole < closestHoleRadius) {
            closestHoleRadius = distanceToHole;
            closestEntry = length(position - traceOrigin);
        }
        if (distanceToHole <= radius * 1.02) {
            captured = true;
            break;
        }
        if (distanceToHole > escapeRadius && dot(position, velocity) > 0.0) {
            break;
        }

        float stepLength = geodesicStep(distanceToHole, radius)
            / max(length(velocity), 0.25);
        // Midpoint integration keeps the photon orbit stable without four acceleration
        // evaluations per step.
        vec3 a1 = geodesicAcceleration(position, h2, radius);
        vec3 midVelocity = velocity + 0.5 * stepLength * a1;
        vec3 midPosition = position + 0.5 * stepLength * velocity;
        vec3 a2 = geodesicAcceleration(midPosition, h2, radius);
        vec3 nextPosition = position + stepLength * midVelocity;
        vec3 nextVelocity = velocity + stepLength * a2;

        accumulateDisk(position, nextPosition, traceOrigin, midVelocity,
            emission, transmittance, nearestDiskEntry);
        position = nextPosition;
        velocity = nextVelocity;
        if (transmittance < 0.001) {
            break;
        }
    }

    // The disk can sit in front of the closest-approach point; use whichever surface
    // the pixel actually shows. Entries stay relative to traceOrigin so the water
    // surface leg is added once, by the composite pass.
    float nearestEntry = min(closestEntry, nearestDiskEntry);

    vec3 emissionTint = foregroundTintAt(
        uv, nearestEntry + max(ray.surfaceDistance, 0.0), 0.5);
    // Tint only the effect's own emission. The lensed background is the scene the
    // player already sees through the glass; tinting it paints a dark halo.
    vec3 color = emission * emissionTint;
    vec3 backgroundLight = vec3(0.0);
    if (!captured && EmissiveOnly < 0.5) {
        float outgoingSpeed = length(velocity);
        vec3 outDirection = outgoingSpeed > 0.000001 ? velocity / outgoingSpeed : direction;
        // The physical geodesic is the default; LensStrength only blends it toward the
        // straight ray for an optional stylized look. Across a water surface, or from
        // inside water, the scene texture was rendered along straight rays, so a
        // screen-space warp would read as a floating reflection.
        float lensEntry = nearestEntry + max(ray.surfaceDistance, 0.0);
        // Looking through a translucent layer must not replace that layer with a
        // bent background. Check the current pixel and the source sample; the color
        // delta catches glass/water even when the depth copy misses it.
        bool currentForeground = foregroundLayer(uv, lensEntry, 0.5);
        float lensBlend = (ray.surfaceDistance > 0.0 || CameraSubmerged > 0.5
            || currentForeground || MediumPathLength > 0.0)
            ? 0.0 : clamp(LensStrength, 0.0, 1.0) * warpFalloff * skyWarp;
        vec3 lensDirection = normalize(mix(direction, outDirection, lensBlend));
        // Lens from the accumulated composition, not the pristine frame: batches
        // are ordered farthest-first, so this bends the image of the black holes
        // behind this one instead of replacing them with the raw scene.
        vec3 background = sceneColor.rgb;
        vec2 lensUv;
        if (projectDirection(lensDirection, lensUv)) {
            vec2 sourceUv = clamp(lensUv, vec2(0.0), vec2(1.0));
            if (ray.surfaceDistance <= 0.0 && CameraSubmerged < 0.5
                && !currentForeground && MediumPathLength <= 0.0
                && abs(VortexStrength) > 0.001) {
                vec2 holeUv = projectPoint(cameraRelative);
                if (holeUv.x >= 0.0) {
                    float uvRadius = clamp(
                        VortexRadius * abs(WorldProjMat[1][1])
                            / max(2.0 * holeDistance, 0.001),
                        0.03,
                        2.0
                    );
                    vec2 delta = sourceUv - holeUv;
                    float distanceToHole = length(delta);
                    float falloff = 1.0 - smoothstep(0.0, uvRadius, distanceToHole);
                    float angle = VortexStrength * 2.4 * falloff;
                    float cs = cos(angle);
                    float sn = sin(angle);
                    vec2 swirled = holeUv + mat2(cs, -sn, sn, cs) * delta
                        * (1.0 - 0.35 * VortexStrength * falloff);
                    sourceUv = clamp(swirled, vec2(0.0), vec2(1.0));
                }
            }
            float sourceDistance = viewDistance(texture(DepthSampler, sourceUv).r, sourceUv);
            bool sourceForeground = foregroundLayer(sourceUv, lensEntry, 0.5);
            // Only bend light that is actually behind the traced surface. The old
            // lens-shell entry accepted samples in front of the hole and dragged
            // foreground entities into the lens.
            if (sourceDistance >= lensEntry - 0.5
                && !sourceForeground && !currentForeground
                && MediumPathLength <= 0.0) {
                float edgeFade = smoothstep(0.0, 0.03, lensUv.x)
                    * smoothstep(0.0, 0.03, 1.0 - lensUv.x)
                    * smoothstep(0.0, 0.03, lensUv.y)
                    * smoothstep(0.0, 0.03, 1.0 - lensUv.y);
                background = mix(sceneColor.rgb,
                    texture(SceneSampler, sourceUv).rgb,
                    edgeFade);
            }
        }
        float innerOcclusion = 1.0 - smoothstep(
            shadowImpact * 1.05, shadowImpact * 1.35, impact
        );
        backgroundLight = background * transmittance * (1.0 - innerOcclusion);
    }

    // Cinematic photon-ring glow. It is symmetric in the impact parameter and is not
    // suppressed by disk alpha, so the back side cannot disappear independently.
    float stabilization = smoothstep(0.0, 0.10, Progress)
        * (1.0 - smoothstep(0.86, 1.0, Progress));
    float pixelWorld = max(0.0001,
        2.0 * max(holeDistance, radius) / max(abs(WorldProjMat[1][1]) * ViewportHeight, 1.0));

    // A dark shockwave absorbs the lensed background inside its annulus. The thin
    // bright rim is added later, so the wave reads as light being eaten, not emitted.
    if (ShockAbsorb > 0.001 && ShockRadius > 0.001) {
        float absorbWidth = max(ShockRadius * 0.08, pixelWorld * 2.0);
        float absorbOffset = (impact - ShockRadius) / absorbWidth;
        float absorb = exp(-absorbOffset * absorbOffset) * ShockAbsorb;
        float absorbFactor = 1.0 - clamp(absorb, 0.0, 0.85);
        color *= absorbFactor;
        backgroundLight *= absorbFactor;
    }

    float ringWidth = max(0.055 * radius, pixelWorld * 1.5);
    float primaryOffset = (impact - shadowImpact) / ringWidth;
    float secondaryOffset = (impact - shadowImpact - 0.35 * radius) / (ringWidth * 1.6);
    float primaryRing = exp(-primaryOffset * primaryOffset);
    float secondaryRing = exp(-secondaryOffset * secondaryOffset) * 0.30;
    vec3 ringColor = mix(vec3(1.0, 0.93, 0.78), vec3(0.72, 0.92, 1.0), Charge);
    float pulse = 0.85 + 0.15 * sin(AgeTicks * 0.08 + Seed * 6.2831853);
    vec3 ringLight = ringColor * (primaryRing + secondaryRing) * RingStrength * 0.18
        * pulse * stabilization * (0.35 + 0.65 * transmittance);
    color += (ringLight / (vec3(1.0) + ringLight)) * emissionTint;

    // A hot inner rim bridges the shadow and the disk inner edge. It also hides
    // the thin background sliver that would otherwise show through the gap.
    float innerRimOffset = (impact - shadowImpact - 0.12 * radius) / (0.10 * radius);
    float innerRim = exp(-innerRimOffset * innerRimOffset);
    vec3 innerRimColor = mix(vec3(1.0, 0.86, 0.62), vec3(0.75, 0.90, 1.0), Charge);
    vec3 innerRimLight = innerRimColor * innerRim * RingStrength * 0.12 * stabilization;
    color += (innerRimLight / (vec3(1.0) + innerRimLight)) * emissionTint;

    float haloOffset = (impact - shadowImpact) / (3.5 * radius);
    float halo = exp(-haloOffset * haloOffset) * 0.08;
    vec3 haloColor = mix(vec3(1.0, 0.45, 0.15), vec3(0.5, 0.2, 1.0), Charge);
    vec3 haloLight = haloColor * halo * RingStrength * 0.04 * stabilization;
    color += (haloLight / (vec3(1.0) + haloLight)) * emissionTint;

    // Cinematic ignition and implosion flash. It is emissive-only so the bloom
    // layer catches it without washing out the lensed background.
    if (CoreFlash > 0.001) {
        float flashRadius = max(FlashRadius, pixelWorld * 2.0);
        float flashFalloff = exp(-pow(impact / flashRadius, 2.0) * 2.5);
        vec3 flashColor = mix(vec3(0.55, 0.66, 0.92), ringColor, 0.30);
        color += flashColor * flashFalloff * CoreFlash * 0.25 * emissionTint;
    }

    // Expanding (intro) or contracting (outro) shockwave ring plus its wake.
    // The ring lives in impact space, so foreground terrain still occludes it.
    if (ShockStrength > 0.001 && ShockRadius > 0.001) {
        float shockWidth = max(ShockRadius * 0.035, pixelWorld * 1.4);
        float shockOffset = (impact - ShockRadius) / shockWidth;
        float shock = exp(-shockOffset * shockOffset);
        float wakeOffset = (impact - ShockRadius * 0.94) / (shockWidth * 2.5);
        float wake = exp(-wakeOffset * wakeOffset) * 0.35;
        vec3 shockColor = mix(vec3(1.0, 0.90, 0.72), ringColor, 0.35);
        color += shockColor * (shock + wake) * ShockStrength * emissionTint;
    }

    // Polar jets at the moment the horizon disappears. The exponent keeps them
    // as two narrow beams instead of a full-screen wash.
    if (OutroJet > 0.001) {
        float axis = abs(dot(direction, DiskNormal));
        float jet = pow(axis, 32.0)
            * (0.35 + 0.65 * exp(-pow(impact / max(radius * 2.5, 0.001), 2.0)));
        float knot = exp(-pow((impact - radius * 0.35) / max(radius * 0.6, 0.001), 2.0));
        vec3 jetColor = mix(vec3(0.75, 0.90, 1.0), vec3(1.0, 0.85, 0.55), Charge);
        color += jetColor * (jet * 0.7 + knot * 0.3) * OutroJet * emissionTint;
    }

    // Medium fog belongs to the effect's emission only. The lensed background is
    // the scene the player already sees, already fogged by the world renderer;
    // fogging it again paints a large dark circle over the sky.
    float fogDistance = max(nearestEntry + max(ray.surfaceDistance, 0.0), 0.0);
    if (MediumPathLength > 0.0) {
        fogDistance = MediumPathLength;
    }
    if (MediumFogActive > 0.5
        && (MediumPathLength > 0.0 || CameraSubmerged > 0.5 || EffectSubmerged > 0.5)) {
        float mediumFog = clamp((fogDistance - MediumFogStart)
            / max(MediumFogEnd - MediumFogStart, 1.0), 0.0, 1.0);
        mediumFog = mediumFog * mediumFog * (3.0 - 2.0 * mediumFog);
        color = mix(color, MediumFogColor, mediumFog);
    }
    color += backgroundLight;

    return vec4(color, encodeEffectDepth(nearestEntry, depthScale));
}

void main() {
    if (Pass < 0.5) {
        fragColor = renderBlackHole(texCoord);
        return;
    }
    if (SuppressAboveWater > 0.5) {
        vec3 scene = texture(SceneSampler, texCoord).rgb;
        scene *= 1.0 - clamp(Darkness, 0.0, 0.75);
        fragColor = vec4(scene, 1.0);
        return;
    }

    vec3 cameraRelative = -BlackHoleOffset;
    float holeDistance = length(BlackHoleOffset);
    vec3 direction = rayDirection(texCoord);
    SceneRay ray = refractAtWater(cameraRelative, direction);
    vec4 effectColor = texture(EffectSampler, texCoord);
    float lensRadius = max(LensRadius, BlackHoleRadius * 3.0);
    float depthScale = effectDepthScale(holeDistance, lensRadius);
    float effectEntry = decodeEffectDepth(effectColor.a, depthScale);
    float sceneDistance = viewDistance(texture(DepthSampler, texCoord).r, texCoord);
    // One quantisation step keeps interpolated alpha from flickering at silhouettes.
    float depthBias = max(0.05, sceneDistance * 0.001)
        + (1.0 + effectEntry) * log(1.0 + depthScale) / 255.0;
    // effectEntry is measured from the refracted origin, so add the surface leg
    // before comparing it with the camera-space scene depth.
    float entryDistance = effectEntry + max(ray.surfaceDistance, 0.0);
    float foregroundDistance = viewDistance(
        texture(ForegroundDepthSampler, texCoord).r, texCoord);
    bool foregroundInFront = foregroundDistance < entryDistance - depthBias;
    if (!foregroundInFront) {
        foregroundInFront = foregroundLayer(texCoord, entryDistance, depthBias);
    }
    vec2 sceneUv = texCoord;
    if (CameraSubmerged < 0.5 && !ray.totalInternalReflection
        && ray.surfaceDistance <= 0.0 && abs(VortexStrength) > 0.001
        && MediumPathLength <= 0.0 && !foregroundInFront) {
        vec2 holeUv = projectPoint(cameraRelative);
        if (holeUv.x >= 0.0) {
            float uvRadius = clamp(
                VortexRadius * abs(WorldProjMat[1][1])
                    / max(2.0 * holeDistance, 0.001),
                0.05,
                2.5
            );
            vec2 delta = sceneUv - holeUv;
            float falloff = 1.0 - smoothstep(0.0, uvRadius, length(delta));
            float angle = VortexStrength * 2.4 * falloff;
            float cs = cos(angle);
            float sn = sin(angle);
            vec2 swirled = holeUv + mat2(cs, -sn, sn, cs) * delta
                * (1.0 - clamp(0.20 * VortexStrength * falloff, 0.0, 0.65));
            // Do not pull a foreground glass/water surface into the swirl.
            vec2 candidate = clamp(swirled, vec2(0.0), vec2(1.0));
            float swirlForeground = viewDistance(
                texture(ForegroundDepthSampler, candidate).r, candidate);
            if (swirlForeground >= entryDistance - depthBias) {
                sceneUv = candidate;
            }
        }
    }
    vec4 sceneColor = texture(SceneSampler, sceneUv);
    if (ray.totalInternalReflection) {
        // Mirror region: the above-water effect is not visible. Keep the cinematic
        // dim but skip the vortex, lens, and bloom.
        vec3 hidden = sceneColor.rgb * (1.0 - clamp(Darkness, 0.0, 0.75));
        fragColor = vec4(hidden, 1.0);
        return;
    }
    vec3 traceOrigin = ray.origin;
    vec3 traceDirection = ray.direction;
    float tEntry = raySphereEntry(traceOrigin, traceDirection, lensRadius);
    bool insideLens = length(traceOrigin) <= lensRadius
        || length(cameraRelative) <= lensRadius;
    float impact = length(cross(traceOrigin, traceDirection));
    // Coverage is deterministic; the raymarch pass stores the nearest traced entry
    // in alpha instead, so recompute coverage here at full composite resolution.
    float coverage = clamp(texture(MaskSampler, texCoord).r * MaskStrength, 0.0, 1.0) * Fade;
    coverage *= 1.0 - smoothstep(lensRadius * 0.88, lensRadius * 1.04, impact);
    bool footprint = insideLens || tEntry >= 0.0;
    bool visible = footprint && sceneDistance >= entryDistance - depthBias;
    float alpha = coverage * (visible ? 1.0 : 0.0);

    // Physically motivated surface transmission instead of a fixed blue wash.
    float surfaceTransmittance = 1.0;
    if (ray.surfaceDistance > 0.0) {
        float incidentIor = CameraSubmerged > 0.5 ? 1.333 : 1.0;
        float transmittedIor = CameraSubmerged > 0.5 ? 1.0 : 1.333;
        surfaceTransmittance = 1.0 - fresnelWater(
            ray.incidenceCosine, incidentIor, transmittedIor);
    }

    vec3 color = effectColor.rgb;
    // At grazing angles the surface reflects the scene already in sceneColor.
    // Blend toward that reflection instead of painting a black disc.
    color = mix(sceneColor.rgb, color, surfaceTransmittance);

    vec3 center = mix(sceneColor.rgb, color, alpha);
    center *= 1.0 - clamp(Darkness, 0.0, 0.75);
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
                BloomSampler,
                clamp(texCoord + offset, vec2(0.0), vec2(1.0))
            ).rgb;
            bloom += max(sampleColor - vec3(BloomThreshold), vec3(0.0));
        }
    }
    bloom /= 9.0;
    // The glow is emissive too; it must fade with the water surface. Medium fog is
    // already applied to the emissive pass, so the background stays untouched.
    float bloomFade = surfaceTransmittance;
    fragColor = vec4(center + bloom * BloomStrength * alpha * bloomFade, 1.0);
}
