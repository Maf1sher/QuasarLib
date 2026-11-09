#version 150

uniform sampler2D SceneSampler;
uniform sampler2D RawSceneSampler;
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
uniform float ShockRadius;
uniform float ShockStrength;
uniform float CoreFlash;
uniform float FlashRadius;
uniform float OutroJet;
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
uniform float SurfaceY;
uniform float SurfaceActive;

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
 * the two geodesic samples, so a thin disk cannot be stepped over.
 */
void accumulateDisk(vec3 before, vec3 after, vec3 cameraRelative, vec3 rayVelocity,
    inout vec3 emission, inout float transmittance) {
    float beforeHeight = dot(before, DiskNormal);
    float afterHeight = dot(after, DiskNormal);
    float segmentLength = length(after - before);
    // Coplanar / slab rays never change the sign of the disk-plane distance. Sample
    // the volume while the segment is inside the slab instead of dropping the disk.
    if (abs(beforeHeight) <= DiskThickness && abs(afterHeight) <= DiskThickness) {
        vec3 midpoint = mix(before, after, 0.5);
        vec4 slabDisk = sampleDisk(midpoint, cameraRelative);
        float slabOpacity = 1.0 - exp(-max(slabDisk.a, 0.0) * segmentLength * DISK_OPACITY);
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
    emission += disk.rgb * opacity * transmittance;
    transmittance *= (1.0 - opacity);
}

vec4 renderBlackHole(vec2 uv) {
    vec4 sceneColor = texture(SceneSampler, uv);
    vec4 rawSceneColor = texture(RawSceneSampler, uv);
    float coverage = clamp(texture(MaskSampler, uv).r * MaskStrength, 0.0, 1.0) * Fade;
    if (coverage < 0.002) {
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }

    vec3 direction = rayDirection(uv);
    vec3 cameraRelative = -BlackHoleOffset;
    float holeDistance = length(BlackHoleOffset);
    float radius = max(BlackHoleRadius, 0.001);
    float lensRadius = max(LensRadius, radius * 3.0);
    float tEntry = raySphereEntry(cameraRelative, direction, lensRadius);
    bool insideLens = length(cameraRelative) <= lensRadius;
    if (tEntry < 0.0 && !insideLens) {
        return EmissiveOnly > 0.5 ? vec4(0.0) : vec4(sceneColor.rgb, 0.0);
    }

    float impact = length(cross(cameraRelative, direction));
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
        // The camera is inside the horizon: keep the frame stable and dark.
        return vec4(vec3(0.02, 0.01, 0.03), coverage);
    }

    vec3 position = cameraRelative + direction * max(tEntry, 0.0);
    vec3 velocity = direction;
    float h2 = dot(cross(cameraRelative, direction), cross(cameraRelative, direction));
    float escapeRadius = max(max(DiskOuterRadius * 1.5, radius * 48.0),
        length(cameraRelative) * 1.25);
    float transmittance = 1.0;
    vec3 emission = vec3(0.0);
    bool captured = false;
    int steps = int(StepCount + 0.5);

    for (int stepIndex = 0; stepIndex < MAX_STEPS; stepIndex++) {
        if (stepIndex >= steps) {
            break;
        }
        float distanceToHole = length(position);
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

        accumulateDisk(position, nextPosition, cameraRelative, midVelocity,
            emission, transmittance);
        position = nextPosition;
        velocity = nextVelocity;
        if (transmittance < 0.001) {
            break;
        }
    }

    vec3 color = emission;
    if (!captured && EmissiveOnly < 0.5) {
        float outgoingSpeed = length(velocity);
        vec3 outDirection = outgoingSpeed > 0.000001 ? velocity / outgoingSpeed : direction;
        // The physical geodesic is the default; LensStrength only blends it toward the
        // straight ray for an optional stylized look.
        float lensBlend = clamp(LensStrength, 0.0, 1.0) * warpFalloff * skyWarp;
        vec3 lensDirection = normalize(mix(direction, outDirection, lensBlend));
        vec3 background = rawSceneColor.rgb;
        vec2 lensUv;
        if (projectDirection(lensDirection, lensUv)) {
            vec2 sourceUv = clamp(lensUv, vec2(0.0), vec2(1.0));
            float sourceDistance = viewDistance(texture(DepthSampler, sourceUv).r, sourceUv);
            if (insideLens || tEntry <= 0.0 || sourceDistance >= tEntry - 0.5) {
                float edgeFade = smoothstep(0.0, 0.03, lensUv.x)
                    * smoothstep(0.0, 0.03, 1.0 - lensUv.x)
                    * smoothstep(0.0, 0.03, lensUv.y)
                    * smoothstep(0.0, 0.03, 1.0 - lensUv.y);
                background = mix(rawSceneColor.rgb,
                    texture(RawSceneSampler, sourceUv).rgb,
                    edgeFade);
            }
        }
        float innerOcclusion = 1.0 - smoothstep(
            shadowImpact * 1.05, shadowImpact * 1.35, impact
        );
        color += background * transmittance * (1.0 - innerOcclusion);
    }

    // Cinematic photon-ring glow. It is symmetric in the impact parameter and is not
    // suppressed by disk alpha, so the back side cannot disappear independently.
    float stabilization = smoothstep(0.0, 0.10, Progress)
        * (1.0 - smoothstep(0.86, 1.0, Progress));
    float pixelWorld = max(0.0001,
        2.0 * max(holeDistance, radius) / max(abs(WorldProjMat[1][1]) * ViewportHeight, 1.0));
    float ringWidth = max(0.055 * radius, pixelWorld * 1.5);
    float primaryOffset = (impact - shadowImpact) / ringWidth;
    float secondaryOffset = (impact - shadowImpact - 0.35 * radius) / (ringWidth * 1.6);
    float primaryRing = exp(-primaryOffset * primaryOffset);
    float secondaryRing = exp(-secondaryOffset * secondaryOffset) * 0.30;
    vec3 ringColor = mix(vec3(1.0, 0.93, 0.78), vec3(0.72, 0.92, 1.0), Charge);
    float pulse = 0.85 + 0.15 * sin(AgeTicks * 0.08 + Seed * 6.2831853);
    vec3 ringLight = ringColor * (primaryRing + secondaryRing) * RingStrength * 0.18
        * pulse * stabilization * (0.35 + 0.65 * transmittance);
    color += ringLight / (vec3(1.0) + ringLight);

    // A hot inner rim bridges the shadow and the disk inner edge. It also hides
    // the thin background sliver that would otherwise show through the gap.
    float innerRimOffset = (impact - shadowImpact - 0.12 * radius) / (0.10 * radius);
    float innerRim = exp(-innerRimOffset * innerRimOffset);
    vec3 innerRimColor = mix(vec3(1.0, 0.86, 0.62), vec3(0.75, 0.90, 1.0), Charge);
    vec3 innerRimLight = innerRimColor * innerRim * RingStrength * 0.12 * stabilization;
    color += innerRimLight / (vec3(1.0) + innerRimLight);

    float haloOffset = (impact - shadowImpact) / (3.5 * radius);
    float halo = exp(-haloOffset * haloOffset) * 0.08;
    vec3 haloColor = mix(vec3(1.0, 0.45, 0.15), vec3(0.5, 0.2, 1.0), Charge);
    vec3 haloLight = haloColor * halo * RingStrength * 0.04 * stabilization;
    color += haloLight / (vec3(1.0) + haloLight);

    // Cinematic ignition and implosion flash. It is emissive-only so the bloom
    // layer catches it without washing out the lensed background.
    if (CoreFlash > 0.001) {
        float flashRadius = max(FlashRadius, pixelWorld * 2.0);
        float flashFalloff = exp(-pow(impact / flashRadius, 2.0) * 2.5);
        vec3 flashColor = mix(vec3(1.0, 0.94, 0.82), ringColor, 0.45);
        color += flashColor * flashFalloff * CoreFlash * 3.0;
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
        color += shockColor * (shock + wake) * ShockStrength;
    }

    // Polar jets at the moment the horizon disappears. The exponent keeps them
    // as two narrow beams instead of a full-screen wash.
    if (OutroJet > 0.001) {
        float axis = abs(dot(direction, DiskNormal));
        float jet = pow(axis, 32.0)
            * (0.35 + 0.65 * exp(-pow(impact / max(radius * 2.5, 0.001), 2.0)));
        float knot = exp(-pow((impact - radius * 0.35) / max(radius * 0.6, 0.001), 2.0));
        vec3 jetColor = mix(vec3(0.75, 0.90, 1.0), vec3(1.0, 0.85, 0.55), Charge);
        color += jetColor * (jet * 1.6 + knot * 0.6) * OutroJet;
    }

    return vec4(color, coverage);
}

void main() {
    if (Pass < 0.5) {
        fragColor = renderBlackHole(texCoord);
        return;
    }

    vec4 sceneColor = texture(SceneSampler, texCoord);
    vec4 effectColor = texture(EffectSampler, texCoord);
    vec3 direction = rayDirection(texCoord);
    vec3 cameraRelative = -BlackHoleOffset;
    float lensRadius = max(LensRadius, BlackHoleRadius * 3.0);
    float tEntry = raySphereEntry(cameraRelative, direction, lensRadius);
    bool insideLens = length(cameraRelative) <= lensRadius;
    float sceneDistance = viewDistance(texture(DepthSampler, texCoord).r, texCoord);
    float depthBias = max(0.05, sceneDistance * 0.001);
    // Ring, disk, and lens-only pixels sit at different depths. A single centre
    // depth made the ring appear in front of or behind trees as the player moved.
    float shadowRadius = max(BlackHoleRadius, 0.001) * B_CRITICAL;
    float shadowEntry = raySphereEntry(cameraRelative, direction, shadowRadius * 1.35);
    float diskEntry = -1.0;
    float planeDenominator = dot(direction, DiskNormal);
    if (abs(planeDenominator) > 0.0001) {
        float planeDistance = dot(-cameraRelative, DiskNormal) / planeDenominator;
        if (planeDistance > 0.0) {
            vec3 hit = cameraRelative + direction * planeDistance;
            float radial = length(hit - DiskNormal * dot(hit, DiskNormal));
            if (radial >= DiskInnerRadius && radial <= DiskOuterRadius) {
                diskEntry = planeDistance;
            }
        }
    }
    float impact = length(cross(cameraRelative, direction));
    float effectEntry;
    if (impact <= shadowRadius * 1.35 && shadowEntry >= 0.0) {
        effectEntry = shadowEntry;
    } else if (diskEntry > 0.0) {
        effectEntry = diskEntry;
    } else {
        effectEntry = tEntry;
    }
    bool footprint = insideLens || tEntry >= 0.0;
    bool visible = footprint && sceneDistance >= effectEntry - depthBias;
    float alpha = effectColor.a * (visible ? 1.0 : 0.0);

    vec3 color = effectColor.rgb;
    float fogDistance = max(effectEntry, 0.0);
    if (SurfaceActive > 0.5 && abs(direction.y) > 0.001) {
        float surfaceDistance = SurfaceY / direction.y;
        if (surfaceDistance > 0.0 && surfaceDistance < effectEntry) {
            // Only the submerged part of the path receives the active water fog.
            fogDistance = CameraSubmerged > 0.5
                ? surfaceDistance
                : max(effectEntry - surfaceDistance, 0.0);
            // A simple surface tint; full wave refraction is a separate polish pass.
            float fresnel = pow(1.0 - min(abs(direction.y), 1.0), 3.0);
            vec3 surfaceColor = mix(MediumFogColor, vec3(0.72, 0.88, 1.0), 0.35);
            color = mix(color, surfaceColor, clamp(0.12 + 0.28 * fresnel, 0.0, 0.45));
        }
    }
    if (MediumFogActive > 0.5 && (CameraSubmerged > 0.5 || EffectSubmerged > 0.5)) {
        float fog = clamp((fogDistance - MediumFogStart) / max(MediumFogEnd - MediumFogStart, 1.0), 0.0, 1.0);
        fog = fog * fog * (3.0 - 2.0 * fog);
        color = mix(color, MediumFogColor, fog);
    }

    vec3 center = mix(sceneColor.rgb, color, alpha);
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
    fragColor = vec4(center + bloom * BloomStrength * alpha, 1.0);
}
