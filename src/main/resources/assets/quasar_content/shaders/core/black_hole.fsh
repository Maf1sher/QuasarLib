#version 150

#moj_import <quasar_lib:noise.glsl>
#moj_import <quasar_lib:effect_space.glsl>
#moj_import <quasar_lib:screen_visibility.glsl>
#moj_import <quasar_lib:medium_optics.glsl>
#moj_import <quasar_content:black_hole_optics.glsl>

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

uniform float BlackHoleRadius;
uniform float LensRadius;
uniform float LensWarpRadius;
uniform float DiskInnerRadius;
uniform float DiskOuterRadius;
uniform float DiskThickness;

uniform float AgeTicks;
uniform float Progress;
uniform float Seed;
uniform float Daylight;
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

uniform float ForegroundDepthValid;
uniform float OpaqueColorValid;

uniform float Pass;
uniform float EmissiveOnly;
uniform float BloomStrength;
uniform float BloomThreshold;
uniform float BloomRadiusPixels;

in vec2 texCoord;

out vec4 fragColor;

const int MAX_STEPS = 256;
const float PI = 3.14159265359;
const float DISK_OPACITY = 2.4;
const float DAY_EMISSION_SCALE = 0.55;
const float DAY_BLOOM_THRESHOLD = 0.50;
const float DAY_BLOOM_STRENGTH_SCALE = 0.35;
const float DAY_BLOOM_RADIUS_SCALE = 0.75;

float daylightFactor() {
    return clamp(Daylight, 0.0, 1.0);
}

float daylightEmissionScale() {
    return mix(1.0, DAY_EMISSION_SCALE, daylightFactor());
}

float daylightBloomThreshold() {
    return mix(BloomThreshold, DAY_BLOOM_THRESHOLD, daylightFactor());
}

float daylightBloomStrengthScale() {
    return mix(1.0, DAY_BLOOM_STRENGTH_SCALE, daylightFactor());
}

float daylightBloomRadiusScale() {
    return mix(1.0, DAY_BLOOM_RADIUS_SCALE, daylightFactor());
}
const float DISK_BODY_OCCLUSION = 0.98;
const float DISK_BODY_EMISSION = 0.20;

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

bool foregroundColorDelta(vec2 uv) {
    return OpaqueColorValid > 0.5
        && distance(texture(RawSceneSampler, uv).rgb,
            texture(OpaqueColorSampler, uv).rgb) > 0.02;
}

/**
 * Samples the translucent foreground inputs at one pixel. The shared include
 * owns the predicate; the black hole only supplies the sampled depth and the
 * pre/post-translucent color delta.
 */
bool foregroundAt(vec2 uv, float entryDistance, float tolerance) {
    float foreground = viewDistance(texture(ForegroundDepthSampler, uv).r, uv);
    if (quasarForegroundLayerDepth(foreground, ForegroundDepthValid > 0.5,
        entryDistance, tolerance)) {
        return true;
    }
    return foregroundColorDelta(uv);
}

/**
 * Returns the attenuation for the effect's own emission behind a foreground
 * translucent layer. The background is never tinted: it is the scene the player
 * already sees through the glass, and tinting it produces a dark halo.
 */
vec3 foregroundTintAt(vec2 uv, float entryDistance, float tolerance) {
    bool foreground = foregroundAt(uv, entryDistance, tolerance);
    return quasarForegroundTint(foreground,
        texture(RawSceneSampler, uv).rgb,
        texture(OpaqueColorSampler, uv).rgb,
        OpaqueColorValid > 0.5);
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

/**
 * Projects a point given as a camera-relative offset (camera to point); returns a
 * negative UV when it is behind the camera. Pass the camera-to-effect vector
 * (EffectOrigin), never the effect-relative camera origin used for tracing.
 */
vec2 projectPoint(vec3 point) {
    vec4 clip = WorldProjMat * vec4(mat3(ViewMat) * point, 1.0);
    if (clip.w <= 0.0001) {
        return vec2(-1.0);
    }
    return clip.xy / clip.w * 0.5 + 0.5;
}

/** Screen-space falloff of the drill around the effect centre; 1 at the core. */
float drillFalloff(vec2 uv, float radius, float holeDistance) {
    vec2 holeUv = projectPoint(EffectOrigin);
    if (holeUv.x < 0.0) {
        return 0.0;
    }
    float uvRadius = clamp(
        radius * abs(WorldProjMat[1][1]) / max(2.0 * holeDistance, 0.001),
        0.03,
        1.0
    );
    return clamp(1.0 - length(uv - holeUv) / uvRadius, 0.0, 1.0);
}

/**
 * Compact space drill: an inward spiral centred on the effect. Outside the
 * drill radius the input UV is returned unchanged, so the warp stays core-sized.
 */
vec2 drillUv(vec2 uv, float strength, float radius, float holeDistance) {
    float falloff = drillFalloff(uv, radius, holeDistance);
    if (falloff <= 0.0) {
        return uv;
    }
    vec2 holeUv = projectPoint(EffectOrigin);
    vec2 delta = uv - holeUv;
    float spiral = falloff * falloff * (3.0 - 2.0 * falloff);
    float angle = strength * 3.0 * spiral;
    float pull = clamp(0.5 * abs(strength) * spiral, 0.0, 0.75);
    float cs = cos(angle);
    float sn = sin(angle);
    return holeUv + mat2(cs, -sn, sn, cs) * delta * (1.0 - pull);
}

/**
 * Screen-space lensing resamples a 2D scene. Large UV offsets pull distant
 * pixels across the frame and read as blur or duplicated blocks, so fade those
 * samples out instead of clamping them to the screen edge.
 */
float lensSampleWeight(vec2 sourceUv, vec2 destinationUv) {
    if (any(lessThan(sourceUv, vec2(0.0))) || any(greaterThan(sourceUv, vec2(1.0)))) {
        return 0.0;
    }
    float displacement = length(sourceUv - destinationUv);
    return 1.0 - smoothstep(0.20, 0.42, displacement);
}

/** Fades the effect out for rays pointing away from the hole. */
float frontFacing(vec3 direction) {
    vec3 toHole = EffectOrigin / max(length(EffectOrigin), 0.001);
    return smoothstep(-0.02, 0.05, dot(toHole, direction));
}

/**
 * 1 when the camera sits inside the disk annulus and close to its plane.
 * The disk must fade there: a volumetric slab viewed from inside otherwise
 * fills the frame and hides the rest of the scene.
 */
float cameraInsideDisk(vec3 cameraRelative) {
    if (DiskOuterRadius <= DiskInnerRadius + 0.001 || DiskThickness <= 0.001) {
        return 0.0;
    }
    float height = dot(cameraRelative, EffectUp);
    vec3 radialVector = cameraRelative - EffectUp * height;
    float radial = length(radialVector);
    float insideAnnulus = smoothstep(
            DiskInnerRadius * 0.85, DiskInnerRadius * 1.05, radial)
        * (1.0 - smoothstep(
            DiskOuterRadius * 0.95, DiskOuterRadius * 1.15, radial));
    float nearPlane = 1.0 - smoothstep(
        DiskThickness * 1.5, DiskThickness * 6.0, abs(height));
    return insideAnnulus * nearPlane;
}

/**
 * Coverage of the optically thick body of the accretion disk. The rims stay
 * soft for the cinematic fade, but the body must suppress everything behind it:
 * an additive glow alone lets terrain show through the ring.
 */
float diskBodyCoverage(float radial) {
    float span = DiskOuterRadius - DiskInnerRadius;
    if (span <= 0.001) {
        return 0.0;
    }
    float t = clamp((radial - DiskInnerRadius) / span, 0.0, 1.0);
    return smoothstep(0.0, 0.05, t) * (1.0 - smoothstep(0.82, 1.0, t));
}

/**
 * Straight-ray footprint of the optically thick disk body. The raymarch already
 * accumulates optical depth, but a sparse slab, a bent path, or a low step
 * budget can still let the scene bleed through. This screen-space mask makes the
 * projected disk body opaque in the final composite as well.
 */
float diskScreenCoverage(vec3 origin, vec3 direction) {
    float denominator = dot(direction, EffectUp);
    if (abs(denominator) < 0.0001) {
        return 0.0;
    }
    float distance = dot(-origin, EffectUp) / denominator;
    if (distance <= 0.0) {
        return 0.0;
    }
    vec3 hit = origin + direction * distance;
    float radial = length(hit - EffectUp * dot(hit, EffectUp));
    return diskBodyCoverage(radial) * clamp(DiskBrightness, 0.0, 1.0);
}

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
    return normalize(vec3(slopeX * BH_RIPPLE_SLOPE, 1.0, slopeZ * BH_RIPPLE_SLOPE));
}

/**
 * Bends the view ray at the active water plane. {@code origin} is the camera
 * relative to the black hole while {@code MediumSurfaceY} is relative to the
 * camera; the returned origin stays relative to the hole so the geodesic
 * integration keeps one frame. The content-side wrapper only supplies the
 * world-stable ripple normal.
 */
QuasarMediumRay refractAtWater(vec3 origin, vec3 direction) {
    if (MediumSurfaceActive < 0.5
        || abs(direction.y) < QUASAR_MEDIUM_DIRECTION_EPSILON) {
        return quasarMediumRay(origin, direction);
    }
    float surfaceDistance = MediumSurfaceY / direction.y;
    if (surfaceDistance <= 0.0) {
        return quasarMediumRay(origin, direction);
    }
    vec3 hit = origin + direction * surfaceDistance;
    return quasarMediumRefract(origin, direction, waterSurfaceNormal(hit));
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
    float height = dot(point, EffectUp);
    vec3 radialVector = point - EffectUp * height;
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
    float phi = atan(dot(radialDir, EffectForward), dot(radialDir, EffectRight));
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
    float coarse = quasarFbm3(noisePosition * (1.0 + 0.6 * turbulenceScale));
    float fine = quasarFbm3(noisePosition * (2.6 + 0.9 * turbulenceScale) + vec3(11.0, 7.0, 3.0));

    float density = edge * (0.28 + 1.05 * coarse) * (0.55 + 0.65 * fine);
    float dust = smoothstep(0.34, 0.68, fine);
    density *= mix(0.45, 1.0, dust);

    float knots = pow(max(fine - 0.58, 0.0) * 2.4, 3.0);
    float stream = pow(max(cos(phi * 3.0 - radial * 0.08 + AgeTicks * 0.02), 0.0), 12.0);
    density *= (0.85 + 0.4 * stream) * vertical;
    density *= wrap;
    knots += stream * 0.4;

    vec3 tangent = normalize(cross(EffectUp, radialDir));
    vec3 toCamera = cameraRelative - point;
    float viewLength = length(toCamera);
    vec3 viewDir = viewLength > 0.0001 ? toCamera / viewLength : EffectUp;
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

    vec3 emission = color * (0.72 + 1.4 * knots) * doppler * DiskBrightness
        * daylightEmissionScale();
    return vec4(emission, density);
}

vec3 geodesicAcceleration(vec3 position, float h2, float horizonRadius) {
    float r2 = max(dot(position, position), 0.00000001);
    float r = sqrt(r2);
    float scale = -1.5 * horizonRadius * h2 / max(r2 * r2 * r, 0.0000000001);
    return position * scale;
}

float geodesicStep(float radius, float horizonRadius) {
    if (radius < BH_STEP_NEAR_LIMIT * horizonRadius) {
        return clamp(BH_STEP_NEAR_FACTOR * radius, BH_STEP_NEAR_MIN * horizonRadius,
            BH_STEP_NEAR_MAX * horizonRadius);
    }
    return clamp(BH_STEP_FAR_FACTOR * radius, BH_STEP_FAR_MIN * horizonRadius,
        BH_STEP_FAR_MAX * horizonRadius);
}

/**
 * Integrates one disk slab crossing. The crossing is found analytically between
 * the two geodesic samples, so a thin disk cannot be stepped over. The first
 * emitting crossing also records its camera distance, which lets the composite
 * depth-test the disk surface instead of a bounding-sphere entry.
 */
void accumulateDisk(vec3 before, vec3 after, vec3 cameraRelative, vec3 rayVelocity,
    inout vec3 emission, inout float transmittance, inout float nearestDiskEntry,
    inout float diskCoverage) {
    float beforeHeight = dot(before, EffectUp);
    float afterHeight = dot(after, EffectUp);
    float segmentLength = length(after - before);
    // Coplanar / slab rays never change the sign of the disk-plane distance. Sample
    // the volume while the segment is inside the slab instead of dropping the disk.
    if (abs(beforeHeight) <= DiskThickness && abs(afterHeight) <= DiskThickness) {
        vec3 midpoint = mix(before, after, 0.5);
        float radial = length(midpoint - EffectUp * dot(midpoint, EffectUp));
        vec4 slabDisk = sampleDisk(midpoint, cameraRelative);
        float slabOpacity = 1.0 - exp(-max(slabDisk.a, 0.0) * segmentLength * DISK_OPACITY);
        // The body is optically thick even where the procedural density is low;
        // otherwise the background shows through the ring's noise gaps. Fade
        // with the authored disk brightness so a forming disk cannot become an
        // opaque black band before its emission appears.
        float body = diskBodyCoverage(radial) * clamp(DiskBrightness, 0.0, 1.0);
        float occlusion = max(slabOpacity, body * DISK_BODY_OCCLUSION);
        if (occlusion > 0.001) {
            nearestDiskEntry = min(nearestDiskEntry, length(midpoint - cameraRelative));
        }
        // Keep the emission wispy but give the gaps a dim base so they read as
        // hot plasma, not as holes cut out of the ring.
        emission += slabDisk.rgb * max(slabOpacity, body * DISK_BODY_EMISSION) * transmittance;
        diskCoverage = max(diskCoverage, occlusion);
        transmittance *= (1.0 - occlusion);
        return;
    }
    if (beforeHeight * afterHeight > 0.0 || abs(beforeHeight - afterHeight) < 0.000001) {
        return;
    }
    float t = clamp(beforeHeight / (beforeHeight - afterHeight), 0.0, 1.0);
    vec3 hit = mix(before, after, t);
    float radial = length(hit - EffectUp * dot(hit, EffectUp));
    if (radial < DiskInnerRadius || radial > DiskOuterRadius) {
        return;
    }

    vec4 disk = sampleDisk(hit, cameraRelative);
    float velocityLength = length(rayVelocity);
    vec3 rayDirection = velocityLength > 0.000001 ? rayVelocity / velocityLength : EffectUp;
    float cosTheta = abs(dot(rayDirection, EffectUp));
    float thickness = max(DiskThickness, 0.001);
    float pathLength = clamp(2.0 * thickness / max(cosTheta, 0.15), 0.02, 8.0 * thickness);
    float opacity = 1.0 - exp(-max(disk.a, 0.0) * pathLength * DISK_OPACITY);
    float body = diskBodyCoverage(radial) * clamp(DiskBrightness, 0.0, 1.0);
    float occlusion = max(opacity, body * DISK_BODY_OCCLUSION);
    if (occlusion > 0.001) {
        nearestDiskEntry = min(nearestDiskEntry, length(hit - cameraRelative));
    }
    emission += disk.rgb * max(opacity, body * DISK_BODY_EMISSION) * transmittance;
    diskCoverage = max(diskCoverage, occlusion);
    transmittance *= (1.0 - occlusion);
}

vec4 renderBlackHole(vec2 uv) {
    vec4 sceneColor = texture(SceneSampler, uv);
    if (MediumSuppressAboveWater > 0.5) {
        // An above-water hole seen from below cannot be represented by a
        // screen-space refraction without reading as a floating reflection.
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }
    float coverage = clamp(texture(MaskSampler, uv).r * MaskStrength, 0.0, 1.0) * Fade;
    if (coverage < 0.002) {
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }

    vec3 direction = rayDirection(uv);
    float facing = frontFacing(direction);
    vec3 cameraRelative = -EffectOrigin;
    QuasarMediumRay ray = refractAtWater(cameraRelative, direction);
    if (ray.totalInternalReflection) {
        // The water surface mirrors the underwater scene; an above-water hole
        // cannot contribute here. This removes the floating reflection-like patch.
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }
    vec3 traceOrigin = ray.origin;
    vec3 traceDirection = ray.direction;
    float holeDistance = length(EffectOrigin);
    float radius = max(BlackHoleRadius, 0.001);
    float insideDisk = cameraInsideDisk(cameraRelative);
    // Check the horizon before any footprint or facing gate: inside the core the
    // whole frame must be dark regardless of the view direction.
    if (length(cameraRelative) <= radius * BH_HORIZON_EPSILON) {
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(vec3(0.02, 0.01, 0.03), 0.0);
    }
    float lensRadius = max(LensRadius, radius * 3.0);
    float depthScale = quasarLayerScale(QUASAR_LAYER_FILL, holeDistance, lensRadius);
    float tEntry = raySphereEntry(traceOrigin, traceDirection, lensRadius);
    bool insideLens = length(traceOrigin) <= lensRadius
        || length(cameraRelative) <= lensRadius;
    if (tEntry < 0.0 && !insideLens) {
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }

    float impact = length(cross(traceOrigin, traceDirection));
    float shadowImpact = radius * BH_B_CRITICAL;
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

    vec3 position = traceOrigin + traceDirection * max(tEntry, 0.0);
    vec3 velocity = traceDirection;
    float h2 = dot(cross(traceOrigin, traceDirection), cross(traceOrigin, traceDirection));
    float escapeRadius = max(max(DiskOuterRadius * 1.5, radius * BH_ESCAPE_FACTOR),
        length(traceOrigin) * BH_ESCAPE_ORIGIN_FACTOR);
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
    float diskCoverage = 0.0;
    float diskScreenMask = diskScreenCoverage(traceOrigin, traceDirection);

    for (int stepIndex = 0; stepIndex < MAX_STEPS; stepIndex++) {
        if (stepIndex >= steps) {
            break;
        }
        float distanceToHole = length(position);
        if (distanceToHole < closestHoleRadius) {
            closestHoleRadius = distanceToHole;
            closestEntry = length(position - traceOrigin);
        }
        if (distanceToHole <= radius * BH_HORIZON_EPSILON) {
            captured = true;
            break;
        }
        if (distanceToHole > escapeRadius && dot(position, velocity) > 0.0) {
            break;
        }

        float stepLength = geodesicStep(distanceToHole, radius)
            / max(length(velocity), BH_STEP_SPEED_FLOOR);
        // Midpoint integration keeps the photon orbit stable without four acceleration
        // evaluations per step.
        vec3 a1 = geodesicAcceleration(position, h2, radius);
        vec3 midVelocity = velocity + 0.5 * stepLength * a1;
        vec3 midPosition = position + 0.5 * stepLength * velocity;
        vec3 a2 = geodesicAcceleration(midPosition, h2, radius);
        vec3 nextPosition = position + stepLength * midVelocity;
        vec3 nextVelocity = velocity + stepLength * a2;

        accumulateDisk(position, nextPosition, traceOrigin, midVelocity,
            emission, transmittance, nearestDiskEntry, diskCoverage);
        position = nextPosition;
        velocity = nextVelocity;
        if (transmittance < 0.001) {
            break;
        }
    }

    // A single depth cannot represent every layer of the effect. A near-side
    // disk covers the horizon, while a far-side disk is still the surface the
    // pixel shows outside the shadow. Using the minimum of both painted the
    // ring over terrain between the hole and the disk; choose the layer that
    // actually owns the pixel instead. Entries stay relative to traceOrigin so
    // the water surface leg is added once, by the composite pass.
    bool diskVisible = diskCoverage > 0.02 && nearestDiskEntry < depthScale;
    float nearestEntry = closestEntry;
    if (diskVisible) {
        nearestEntry = captured ? min(closestEntry, nearestDiskEntry) : nearestDiskEntry;
    }

    // Photon-ring and inner-rim profiles are evaluated before the background is
    // re-added. A bright emissive ring is a surface: terrain behind it must not
    // shine through its glow.
    float stabilization = smoothstep(0.0, 0.10, Progress)
        * (1.0 - smoothstep(0.86, 1.0, Progress));
    float pixelWorld = max(0.0001,
        2.0 * max(holeDistance, radius) / max(abs(WorldProjMat[1][1]) * ViewportHeight, 1.0));
    float ringWidth = max(0.055 * radius, pixelWorld * 1.5);
    float primaryOffset = (impact - shadowImpact) / ringWidth;
    float secondaryOffset = (impact - shadowImpact - 0.35 * radius) / (ringWidth * 1.6);
    float primaryRing = exp(-primaryOffset * primaryOffset);
    float secondaryRing = exp(-secondaryOffset * secondaryOffset) * 0.30;
    float innerRimOffset = (impact - shadowImpact - 0.12 * radius) / (0.10 * radius);
    float innerRim = exp(-innerRimOffset * innerRimOffset);
    // Use the actual ring/inner-rim light amplitudes, not the raw Gaussian
    // profiles: the dim tails must stay additive instead of carving a dark band
    // out of the background. Gate by facing like the ring light itself.
    float ringGlow = (primaryRing + secondaryRing) * RingStrength * 0.18;
    float innerGlow = innerRim * RingStrength * 0.12;
    float ringOcclusion = smoothstep(0.02, 0.20,
        max(ringGlow, innerGlow) * stabilization) * facing;
    float coreOcclusion = facing * (1.0 - smoothstep(
        shadowImpact * 1.05, shadowImpact * 1.35, impact));
    float opaqueCoverage = max(max(diskScreenMask, coreOcclusion), ringOcclusion);

    vec3 emissionTint = foregroundTintAt(
        uv, nearestEntry + max(ray.surfaceDistance, 0.0), 0.5);
    // A scene-derived tint is only reliable when a foreground medium is known
    // from the camera-to-effect path, or when depth puts the layer in front.
    // Otherwise background water/ice would print its colors onto the opaque
    // disk/core/ring and read as terrain behind the black hole.
    if (MediumPathLength <= 0.0 && opaqueCoverage > 0.01) {
        float tintEntry = nearestEntry + max(ray.surfaceDistance, 0.0);
        float tintDepth = viewDistance(
            texture(ForegroundDepthSampler, uv).r, uv);
        // Background water/ice must not print its colors onto an opaque surface;
        // only a depth-confirmed layer in front may tint the emission.
        bool layerInFront = quasarForegroundLayer(
            tintDepth, ForegroundDepthValid > 0.5, tintEntry, 0.5, false);
        if (!layerInFront) {
            emissionTint = mix(emissionTint, vec3(1.0), opaqueCoverage);
        }
    }
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
        bool currentForeground = foregroundAt(uv, lensEntry, 0.5);
        // The background lens is only reliable from outside its own bounding
        // sphere. Inside it, the bent rays resample a tiny part of the frame and
        // read as blur or radial streaks.
        float lensProximity = smoothstep(
            lensRadius, lensRadius * 1.5, holeDistance);
        float lensBlend = (ray.surfaceDistance > 0.0 || currentForeground
            || !quasarMediumWarpAllowed())
            ? 0.0 : clamp(LensStrength, 0.0, 1.0) * warpFalloff * skyWarp
                * facing * lensProximity * (1.0 - insideDisk);
        vec3 lensDirection = normalize(mix(direction, outDirection, lensBlend));
        // Lens from the accumulated composition, not the pristine frame: batches
        // are ordered farthest-first, so this bends the image of the black holes
        // behind this one instead of replacing them with the raw scene.
        vec3 background = sceneColor.rgb;
        vec2 lensUv;
        if (projectDirection(lensDirection, lensUv)) {
            vec2 sourceUv = lensUv;
            if (ray.surfaceDistance <= 0.0 && quasarMediumWarpAllowed()
                && !currentForeground && abs(VortexStrength) > 0.001) {
                vec2 candidate =
                    drillUv(sourceUv, VortexStrength, VortexRadius, holeDistance);
                if (all(greaterThanEqual(candidate, vec2(0.0)))
                    && all(lessThanEqual(candidate, vec2(1.0)))) {
                    sourceUv = candidate;
                }
            }
            float sampleWeight = lensSampleWeight(sourceUv, uv);
            if (sampleWeight > 0.001) {
                float sourceDistance = viewDistance(
                    texture(DepthSampler, sourceUv).r, sourceUv);
                bool sourceForeground = foregroundAt(sourceUv, lensEntry, 0.5);
                // Only bend light that is actually behind the traced surface. The old
                // lens-shell entry accepted samples in front of the hole and dragged
                // foreground entities into the lens.
                if (sourceDistance >= lensEntry - 0.5
                    && !sourceForeground && !currentForeground
                    && quasarMediumWarpAllowed()) {
                    float edgeFade = smoothstep(0.0, 0.03, lensUv.x)
                        * smoothstep(0.0, 0.03, 1.0 - lensUv.x)
                        * smoothstep(0.0, 0.03, lensUv.y)
                        * smoothstep(0.0, 0.03, 1.0 - lensUv.y);
                    background = mix(sceneColor.rgb,
                        texture(SceneSampler, sourceUv).rgb,
                        edgeFade * sampleWeight);
                }
            }
        }
        backgroundLight = background * transmittance * (1.0 - coreOcclusion)
            * (1.0 - ringOcclusion) * (1.0 - diskScreenMask);
    }

    // A dark shockwave absorbs the lensed background inside its annulus. The thin
    // bright rim is added later, so the wave reads as light being eaten, not emitted.
    if (ShockAbsorb > 0.001 && ShockRadius > 0.001) {
        float absorbWidth = max(ShockRadius * 0.08, pixelWorld * 2.0);
        float absorbOffset = (impact - ShockRadius) / absorbWidth;
        float absorb = exp(-absorbOffset * absorbOffset) * ShockAbsorb;
        float absorbFactor = mix(1.0, 1.0 - clamp(absorb, 0.0, 0.85), facing);
        color *= absorbFactor;
        backgroundLight *= absorbFactor;
    }

    // Cinematic photon-ring glow. It is symmetric in the impact parameter and is not
    // suppressed by disk alpha, so the back side cannot disappear independently.
    vec3 ringColor = mix(vec3(1.0, 0.93, 0.78), vec3(0.72, 0.92, 1.0), Charge);
    float pulse = 0.85 + 0.15 * sin(AgeTicks * 0.08 + Seed * 6.2831853);
    vec3 ringLight = ringColor * (primaryRing + secondaryRing) * RingStrength * 0.18
        * pulse * stabilization * (0.35 + 0.65 * transmittance);
    color += (ringLight / (vec3(1.0) + ringLight)) * emissionTint * facing;

    // A hot inner rim bridges the shadow and the disk inner edge. Its profile
    // already contributes to ringOcclusion, so the background cannot show
    // through the thin gap.
    vec3 innerRimColor = mix(vec3(1.0, 0.86, 0.62), vec3(0.75, 0.90, 1.0), Charge);
    vec3 innerRimLight = innerRimColor * innerRim * RingStrength * 0.12 * stabilization;
    color += (innerRimLight / (vec3(1.0) + innerRimLight)) * emissionTint * facing;

    float haloOffset = (impact - shadowImpact) / (3.5 * radius);
    float halo = exp(-haloOffset * haloOffset) * 0.08;
    vec3 haloColor = mix(vec3(1.0, 0.45, 0.15), vec3(0.5, 0.2, 1.0), Charge);
    vec3 haloLight = haloColor * halo * RingStrength * 0.04 * stabilization;
    color += (haloLight / (vec3(1.0) + haloLight)) * emissionTint * facing;

    // Cinematic ignition and implosion flash. It is emissive-only so the bloom
    // layer catches it without washing out the lensed background.
    if (CoreFlash > 0.001) {
        float flashRadius = max(FlashRadius, pixelWorld * 2.0);
        float flashFalloff = exp(-pow(impact / flashRadius, 2.0) * 2.5);
        vec3 flashColor = mix(vec3(0.55, 0.66, 0.92), ringColor, 0.30);
        color += flashColor * flashFalloff * CoreFlash * 0.25 * emissionTint * facing;
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
        color += shockColor * (shock + wake) * ShockStrength * emissionTint * facing;
    }

    // Polar jets at the moment the horizon disappears. The exponent keeps them
    // as two narrow beams instead of a full-screen wash.
    if (OutroJet > 0.001) {
        float axis = abs(dot(direction, EffectUp));
        float jet = pow(axis, 32.0)
            * (0.35 + 0.65 * exp(-pow(impact / max(radius * 2.5, 0.001), 2.0)));
        float knot = exp(-pow((impact - radius * 0.35) / max(radius * 0.6, 0.001), 2.0));
        vec3 jetColor = mix(vec3(0.75, 0.90, 1.0), vec3(1.0, 0.85, 0.55), Charge);
        color += jetColor * (jet * 0.7 + knot * 0.3) * OutroJet * emissionTint * facing;
    }

    // Medium fog belongs to the effect's emission only. The lensed background is
    // the scene the player already sees, already fogged by the world renderer;
    // fogging it again paints a large dark circle over the sky.
    float fogDistance = quasarMediumFogDistance(
        max(nearestEntry + max(ray.surfaceDistance, 0.0), 0.0));
    color = quasarMediumFog(color, fogDistance);
    color += backgroundLight;

    return vec4(color,
        quasarEncodeLayerDepth(QUASAR_LAYER_FILL, nearestEntry, holeDistance, lensRadius));
}

void main() {
    if (Pass < 0.5) {
        fragColor = renderBlackHole(texCoord);
        return;
    }
    if (MediumSuppressAboveWater > 0.5) {
        vec3 scene = texture(SceneSampler, texCoord).rgb;
        scene *= 1.0 - clamp(Darkness, 0.0, 0.75);
        fragColor = vec4(scene, 1.0);
        return;
    }

    vec3 cameraRelative = -EffectOrigin;
    float holeDistance = length(EffectOrigin);
    bool insideHorizon = holeDistance <= max(BlackHoleRadius, 0.001) * BH_HORIZON_EPSILON;
    vec3 direction = rayDirection(texCoord);
    QuasarMediumRay ray = refractAtWater(cameraRelative, direction);
    vec4 effectColor = texture(EffectSampler, texCoord);
    float lensRadius = max(LensRadius, BlackHoleRadius * 3.0);
    float depthScale = quasarLayerScale(QUASAR_LAYER_FILL, holeDistance, lensRadius);
    float effectEntry = quasarDecodeLayerDepth(
        QUASAR_LAYER_FILL, effectColor.a, holeDistance, lensRadius);
    float sceneDistance = viewDistance(texture(DepthSampler, texCoord).r, texCoord);
    // One quantisation step keeps interpolated alpha from flickering at silhouettes.
    float depthBias = max(0.05, sceneDistance * 0.001)
        + (1.0 + effectEntry) * log(1.0 + depthScale) / 255.0;
    // effectEntry is measured from the refracted origin, so add the surface leg
    // before comparing it with the camera-space scene depth.
    float entryDistance = effectEntry + max(ray.surfaceDistance, 0.0);
    float foregroundDistance = viewDistance(
        texture(ForegroundDepthSampler, texCoord).r, texCoord);
    bool foregroundDelta = foregroundColorDelta(texCoord);
    bool lensBlocked = !quasarLayerVisibleThroughForeground(
        QUASAR_LAYER_LENS, entryDistance, sceneDistance, foregroundDistance,
        ForegroundDepthValid > 0.5, foregroundDelta, depthBias);
    vec2 sceneUv = texCoord;
    bool drillActive = !insideHorizon
        && quasarMediumWarpAllowed() && !ray.totalInternalReflection
        && ray.surfaceDistance <= 0.0 && abs(VortexStrength) > 0.001
        && !lensBlocked;
    if (drillActive) {
        // Do not pull a foreground glass/water surface into the swirl.
        vec2 candidate = clamp(
            drillUv(sceneUv, VortexStrength, VortexRadius, holeDistance),
            vec2(0.0), vec2(1.0));
        float swirlForeground = viewDistance(
            texture(ForegroundDepthSampler, candidate).r, candidate);
        if (swirlForeground >= entryDistance - depthBias) {
            sceneUv = candidate;
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
    float diskScreenMask = diskScreenCoverage(traceOrigin, traceDirection);
    float coreMask = (1.0 - smoothstep(
            max(BlackHoleRadius, 0.001) * BH_B_CRITICAL * 1.05,
            max(BlackHoleRadius, 0.001) * BH_B_CRITICAL * 1.35,
            impact)) * frontFacing(direction);
    float opaqueMask = max(diskScreenMask, coreMask);
    bool footprint = insideLens || tEntry >= 0.0;
    bool visible = footprint
        && quasarLayerVisible(QUASAR_LAYER_FILL, entryDistance, sceneDistance, depthBias);
    // The disk and shadow are opaque surfaces: force their pixels over the scene
    // even if the mask coverage is soft, but never over genuine foreground depth.
    float alpha = visible ? max(coverage, opaqueMask) : 0.0;

    // Physically motivated surface transmission instead of a fixed blue wash.
    float surfaceTransmittance = ray.surfaceDistance > 0.0
        ? quasarMediumSurfaceTransmittance(ray.incidenceCosine) : 1.0;

    vec3 color = effectColor.rgb;
    // At grazing angles the surface reflects the scene already in sceneColor.
    // Blend toward that reflection instead of painting a black disc. Opaque
    // disk/core pixels keep their effect color even across a water surface.
    color = mix(sceneColor.rgb, color, max(surfaceTransmittance, opaqueMask));

    vec3 center = mix(sceneColor.rgb, color, alpha);
    center *= 1.0 - clamp(Darkness, 0.0, 0.75);
    // A subtle funnel shade keeps the drill readable against a uniform sky.
    if (drillActive) {
        float drill = drillFalloff(texCoord, VortexRadius, holeDistance);
        float funnel = drill * drill * (3.0 - 2.0 * drill);
        center *= 1.0 - 0.15 * clamp(abs(VortexStrength), 0.0, 1.5) * funnel;
    }
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
            vec2 offset = vec2(float(x), float(y)) * texel * BloomRadiusPixels
                * daylightBloomRadiusScale();
            vec3 sampleColor = texture(
                BloomSampler,
                clamp(texCoord + offset, vec2(0.0), vec2(1.0))
            ).rgb;
            bloom += max(sampleColor - vec3(daylightBloomThreshold()), vec3(0.0));
        }
    }
    bloom /= 9.0;
    // The glow is emissive too; it must fade with the water surface and with
    // medium fog. Attenuate the additive bloom instead of adding the fog colour
    // again, so the re-added background stays untouched.
    float bloomFade = surfaceTransmittance * quasarMediumFogTransmission(
        quasarMediumFogDistance(entryDistance));
    fragColor = vec4(center + bloom * BloomStrength * daylightBloomStrengthScale()
        * alpha * bloomFade, 1.0);
}
