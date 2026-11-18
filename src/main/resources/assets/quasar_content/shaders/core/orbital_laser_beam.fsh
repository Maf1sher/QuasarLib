#version 150

#moj_import <quasar_lib:noise.glsl>
#moj_import <quasar_lib:effect_space.glsl>
#moj_import <quasar_lib:depth_reconstruction.glsl>
#moj_import <quasar_lib:screen_visibility.glsl>
#moj_import <quasar_lib:medium_optics.glsl>
#moj_import <quasar_content:orbital_laser_optics.glsl>
#moj_import <quasar_content:orbital_laser_platform.glsl>

uniform sampler2D DepthSampler;
uniform sampler2D ForegroundDepthSampler;
uniform sampler2D OpaqueColorSampler;
uniform sampler2D RawSceneSampler;

uniform mat4 ViewMat;
uniform mat4 WorldProjMat;

uniform float ViewportWidth;
uniform float ViewportHeight;

uniform float AgeTicks;
uniform float Progress;
uniform float Seed;
uniform float Daylight;
uniform float QualityLevel;
uniform float LodLevel;

uniform float BeamRadius;
uniform float BeamTopHeight;
uniform float BeamCoreRadius;
uniform float BeamGlowRadius;
uniform float ShockMaxRadius;
uniform float FlashRadius;
uniform float MarchSteps;
uniform float TipDepth;

uniform float BeamTip;
uniform float BeamStrength;
uniform float BeamWidth;
uniform float ImpactFlash;
uniform float FinalFlash;
uniform float ShockRadiusScale;
uniform float ShockStrength;
uniform float ContactGlow;
uniform float RimGlow;

uniform float ForegroundDepthValid;
uniform float OpaqueColorValid;

in vec2 texCoord;

out vec4 fragColor;

const int MAX_STEPS = 56;
const float DIRECTION_EPSILON = 0.000001;
const float SURFACE_EPSILON = 0.0001;

bool foregroundColorDelta(vec2 uv) {
    return OpaqueColorValid > 0.5
        && distance(texture(RawSceneSampler, uv).rgb,
            texture(OpaqueColorSampler, uv).rgb) > 0.02;
}

vec2 projectPoint(vec3 cameraRelative) {
    vec4 clip = WorldProjMat * vec4(mat3(ViewMat) * cameraRelative, 1.0);
    if (clip.w <= SURFACE_EPSILON) {
        return vec2(-1.0);
    }
    return clip.xy / clip.w * 0.5 + 0.5;
}

vec3 platformShade(vec3 point, vec3 normal, float muzzleY, float daylight) {
    vec3 hull = mix(vec3(0.16, 0.18, 0.21), vec3(0.38, 0.41, 0.45), daylight);
    vec3 lightDirection = normalize(vec3(0.35, 0.82, 0.45));
    float diffuse = max(dot(normal, lightDirection), 0.0);
    float ambient = 0.38 + 0.18 * abs(normal.y);
    vec3 color = hull * (ambient + 0.72 * diffuse);

    float radial = length(point.xz);
    float angle = atan(point.z, point.x);
    float segmentPulse = 0.5 + 0.5 * cos(angle * float(QUASAR_PLATFORM_SIDES));
    float underside = max(-normal.y, 0.0);
    float ringLight = 1.0 - smoothstep(0.0, 1.8,
        abs(radial - (PlatformOuterRadius - 1.2)));
    float innerLight = 1.0 - smoothstep(0.0, 1.6,
        abs(radial - PlatformInnerRadius));
    float hubLight = 1.0 - smoothstep(0.0, 1.5,
        abs(radial - (QUASAR_PLATFORM_HUB_RADIUS - 0.8)));
    float aperture = 1.0 - smoothstep(0.0, 2.4,
        length(vec2(radial - QUASAR_PLATFORM_BARREL_RADIUS, point.y - muzzleY)));
    float eye = (1.0 - smoothstep(0.0, 2.2, radial))
        * smoothstep(0.2, 1.0, underside);

    vec3 cyan = mix(vec3(0.10, 0.55, 0.85), vec3(0.45, 0.95, 1.0),
        daylight * 0.4);
    vec3 hot = mix(vec3(1.0, 0.55, 0.25), vec3(1.0, 0.92, 0.75),
        daylight * 0.3);
    float runningLights = ringLight * (0.55 + 0.45 * segmentPulse)
        * (0.35 + 0.65 * underside);
    color += cyan * (runningLights * 0.9 + hubLight * 1.1 + innerLight * 1.4);
    color = mix(color, vec3(1.0, 0.06, 0.03), eye * 0.85);
    color += hot * aperture * 2.2;
    return color;
}

float raySphereEntry(vec3 origin, vec3 direction, vec3 center, float radius) {
    vec3 offset = origin - center;
    float b = dot(offset, direction);
    float c = dot(offset, offset) - radius * radius;
    float discriminant = b * b - c;
    if (discriminant < 0.0) {
        return -1.0;
    }
    float root = sqrt(discriminant);
    float farHit = -b + root;
    if (farHit < 0.0) {
        return -1.0;
    }
    return max(-b - root, 0.0);
}

bool rayCylinder(vec3 origin, vec3 direction, vec3 center, vec3 axis, float radius,
    float tipHeight, float topHeight, float maxDistance, out float tEnter, out float tExit) {
    tEnter = 0.0;
    tExit = 0.0;
    vec3 offset = origin - center;
    float offsetAxis = dot(offset, axis);
    float directionAxis = dot(direction, axis);
    vec3 offsetRadial = offset - axis * offsetAxis;
    vec3 directionRadial = direction - axis * directionAxis;
    float a = dot(directionRadial, directionRadial);
    float b = 2.0 * dot(offsetRadial, directionRadial);
    float c = dot(offsetRadial, offsetRadial) - radius * radius;

    float nearHit;
    float farHit;
    if (a < DIRECTION_EPSILON) {
        if (c > 0.0) {
            return false;
        }
        nearHit = -1000000.0;
        farHit = 1000000.0;
    } else {
        float discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) {
            return false;
        }
        float root = sqrt(discriminant);
        nearHit = (-b - root) / (2.0 * a);
        farHit = (-b + root) / (2.0 * a);
    }

    float minimumHeight = tipHeight;
    float maximumHeight = max(topHeight, minimumHeight);
    if (abs(directionAxis) < DIRECTION_EPSILON) {
        if (offsetAxis < minimumHeight || offsetAxis > maximumHeight) {
            return false;
        }
    } else {
        float firstHeight = (minimumHeight - offsetAxis) / directionAxis;
        float secondHeight = (maximumHeight - offsetAxis) / directionAxis;
        nearHit = max(nearHit, min(firstHeight, secondHeight));
        farHit = min(farHit, max(firstHeight, secondHeight));
    }

    tEnter = max(nearHit, 0.0);
    tExit = min(farHit, maxDistance);
    return tExit > tEnter;
}

void main() {
    if (MediumSuppressAboveWater > 0.5) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 direction = quasarWorldRay(texCoord);
    if (dot(direction, direction) < DIRECTION_EPSILON) {
        fragColor = vec4(0.0);
        return;
    }
    direction = normalize(direction);

    float rawSceneDepth = texture(DepthSampler, texCoord).r;
    float sceneDistance = quasarViewDistance(rawSceneDepth, texCoord);
    float depthBias = max(0.05, sceneDistance * 0.001);
    // The clear depth is the far plane, not infinity. The platform sits beyond the
    // vanilla far plane at low render distances, so sky pixels must not occlude it.
    bool skyPixel = rawSceneDepth >= 0.9999995;
    float platformMaxDistance = skyPixel ? QUASAR_DEPTH_MAX_DISTANCE : sceneDistance;
    float platformDepthBias = skyPixel ? 0.05 : depthBias;
    float foregroundDistance = quasarViewDistance(
        texture(ForegroundDepthSampler, texCoord).r, texCoord);
    bool foregroundDepthValid = ForegroundDepthValid > 0.5;
    bool foregroundDelta = !foregroundDepthValid && foregroundColorDelta(texCoord);
    vec3 base = EffectOrigin;
    vec3 axis = EffectUp;
    if (dot(axis, axis) < DIRECTION_EPSILON) {
        axis = vec3(0.0, 1.0, 0.0);
    } else {
        axis = normalize(axis);
    }

    float beamWidth = clamp(BeamWidth, 0.0, 1.0);
    float fullBeamRadius = max(BeamRadius, 0.001);
    float beamRadius = max(fullBeamRadius * beamWidth, 0.001);
    float topHeight = max(BeamTopHeight, fullBeamRadius);
    float tipHeight = clamp(BeamTip, 0.0, 1.0) * topHeight;
    float tipDepth = max(TipDepth, 0.0);
    float bottomHeight = tipDepth > 0.001 ? -tipDepth : tipHeight;
    float beamStrength = clamp(BeamStrength, 0.0, 1.0);
    float flash = max(clamp(ImpactFlash, 0.0, 1.0), clamp(FinalFlash, 0.0, 1.0));
    float finalFlash = clamp(FinalFlash, 0.0, 1.0);
    float contact = clamp(ContactGlow, 0.0, 1.0);
    float ringStrength = clamp(ShockStrength, 0.0, 1.0);
    float rimGlow = clamp(RimGlow, 0.0, 1.0);
    float platformVisible = clamp(PlatformVisible, 0.0, 1.0);
    if (beamStrength < 0.001 && flash < 0.001 && contact < 0.001
        && ringStrength < 0.001 && rimGlow < 0.001
        && platformVisible < 0.001) {
        fragColor = vec4(0.0);
        return;
    }

    float daylight = clamp(Daylight, 0.0, 1.0);
    float quality = clamp(QualityLevel, 0.0, 3.0) / 3.0;
    float lod = clamp(LodLevel, 0.0, 3.0) / 3.0;
    float detail = mix(0.55, 1.0, quality) * (1.0 - 0.08 * lod);
    float time = AgeTicks * 0.06;

    vec3 coreColor = mix(vec3(1.0, 0.50, 0.20), vec3(1.0, 0.92, 0.78),
        daylight * 0.45);
    vec3 glowColor = mix(vec3(0.75, 0.08, 0.03), vec3(1.0, 0.35, 0.14),
        daylight * 0.35);
    vec3 contactColor = mix(vec3(1.0, 0.30, 0.08), vec3(1.0, 0.78, 0.46),
        daylight * 0.35);
    vec3 flashColor = mix(vec3(1.0, 0.56, 0.22), vec3(1.0, 0.93, 0.76),
        daylight * 0.35);
    vec3 rimColor = mix(vec3(1.0, 0.12, 0.02), vec3(1.0, 0.62, 0.22),
        daylight * 0.40);
    vec3 ringColor = mix(vec3(1.0, 0.25, 0.06), vec3(1.0, 0.72, 0.36),
        daylight * 0.30);
    vec3 activeFlashColor = mix(flashColor, vec3(1.0, 0.96, 0.86),
        finalFlash * 0.65);

    float nearestEntry = sceneDistance;
    vec3 emission = vec3(0.0);

    float tEnter;
    float tExit;
    if (beamStrength > 0.001
        && rayCylinder(vec3(0.0), direction, base, axis, beamRadius, bottomHeight,
            topHeight, sceneDistance, tEnter, tExit)) {
        nearestEntry = min(nearestEntry, tEnter);
        int steps = int(clamp(MarchSteps, 8.0, float(MAX_STEPS)));
        int count = max(steps, 1);
        float stepLength = (tExit - tEnter) / float(count);
        float transmittance = 1.0;
        float topFadeStart = topHeight * (1.0 - QUASAR_ORBITAL_TOP_FADE_FRACTION);
        float headRadius = max(beamRadius * QUASAR_ORBITAL_HEAD_RADIUS_FACTOR, 0.001);
        float coreRadius = max(BeamCoreRadius * beamWidth, 0.001);
        float glowRadius = max(BeamGlowRadius * beamWidth, coreRadius);

        for (int index = 0; index < MAX_STEPS; index++) {
            if (index >= count) {
                break;
            }
            float distance = tEnter + (float(index) + 0.5) * stepLength;
            vec3 point = direction * distance;
            vec3 relative = point - base;
            float height = dot(relative, axis);
            vec3 radialVector = relative - axis * height;
            float radial = length(radialVector);
            float angle = atan(dot(radialVector, EffectForward),
                dot(radialVector, EffectRight));

            float topFade = 1.0 - smoothstep(topFadeStart, topHeight, height);
            float tipFade = smoothstep(bottomHeight, bottomHeight + headRadius * 0.5, height);
            float head = exp(-pow((height - bottomHeight) / headRadius, 2.0)) * topFade;
            float normalizedRadius = radial / coreRadius;
            float coreProfile = exp(-pow(normalizedRadius, 4.0)
                * QUASAR_ORBITAL_CORE_PROFILE);
            float innerProfile = exp(-pow(radial
                / max(coreRadius * QUASAR_ORBITAL_INNER_CORE_FACTOR, 0.001), 2.0)
                * QUASAR_ORBITAL_INNER_PROFILE);
            float glowProfile = exp(-pow(radial / glowRadius, 2.0)
                * QUASAR_ORBITAL_GLOW_PROFILE);
            float coarse = quasarValueNoise3(vec3(
                angle * 1.6, height * 0.11 + time, Seed * 11.0));
            float fine = quasarValueNoise3(vec3(
                angle * 5.2, height * 0.33 - time * 1.7, Seed * 23.0));
            float filament = quasarValueNoise3(vec3(
                angle * 7.0, height * 0.26 - time * 2.4, Seed * 37.0));
            float clump = quasarValueNoise3(vec3(
                angle * 2.4, height * 0.08 + time * 0.8, Seed * 17.0));
            float plasmaPulse = 0.92 + 0.08 * sin(AgeTicks * 0.5
                + height * 0.08 + Seed * 6.2831853);
            float turbulence = (0.60 + 0.40 * coarse)
                * (0.78 + 0.22 * fine)
                * (0.72 + 0.28 * filament)
                * (0.84 + 0.16 * clump)
                * plasmaPulse;
            float body = beamStrength * topFade * tipFade * turbulence;
            float coreDensity = max(coreProfile, innerProfile
                * QUASAR_ORBITAL_INNER_CORE_WEIGHT) * body;
            float glowDensity = glowProfile * body;
            float sampleAbsorption = 1.0 - exp(-coreDensity
                * QUASAR_ORBITAL_CORE_ABSORPTION * stepLength);
            vec3 plasmaColor = mix(coreColor, vec3(1.0, 0.88, 0.62), 0.45);
            float filamentGlow = smoothstep(0.52, 0.82, filament) * coreProfile;
            float rimOffset = (radial - coreRadius * 0.88)
                / max(coreRadius * 0.22, 0.001);
            float rim = exp(-rimOffset * rimOffset) * body;
            emission += plasmaColor * coreDensity * QUASAR_ORBITAL_CORE_EMISSION
                * stepLength * transmittance;
            emission += glowColor * glowDensity * QUASAR_ORBITAL_GLOW_EMISSION
                * stepLength * transmittance;
            emission += plasmaColor * filamentGlow
                * QUASAR_ORBITAL_FILAMENT_STRENGTH * stepLength * transmittance;
            emission += plasmaColor * rim * QUASAR_ORBITAL_RIM_STRENGTH
                * stepLength * transmittance;
            emission += flashColor * head * 0.004 * stepLength
                * detail * beamStrength * transmittance;
            transmittance *= 1.0 - sampleAbsorption;
        }
    }

    vec3 bottom = base + axis * bottomHeight;
    float bottomDistance = length(bottom);
    float planeDenominator = dot(direction, axis);
    float bottomPlaneDistance = bottomDistance > 0.0
        && abs(planeDenominator) > DIRECTION_EPSILON
        ? dot(bottom, axis) / planeDenominator
        : -1.0;
    if (bottomPlaneDistance > 0.0 && bottomPlaneDistance < sceneDistance) {
        vec3 hit = direction * bottomPlaneDistance;
        vec3 relative = hit - bottom;
        float radial = length(relative - axis * dot(relative, axis));
        float contactRadius = max(beamRadius * QUASAR_ORBITAL_CONTACT_RADIUS_FACTOR, 0.001);
        float contactDisk = exp(-pow(radial / contactRadius, 2.0) * 2.5);
        if (quasarLayerVisible(QUASAR_LAYER_FILL, bottomPlaneDistance, sceneDistance,
            depthBias)) {
            emission += contactColor * contactDisk * contact
                * QUASAR_ORBITAL_CONTACT_EMISSION;
            nearestEntry = min(nearestEntry, bottomPlaneDistance);
        }
    }

    float baseDistance = length(base);
    float basePlaneDenominator = dot(direction, axis);
    float basePlaneDistance = baseDistance > 0.0 && abs(basePlaneDenominator) > DIRECTION_EPSILON
        ? dot(base, axis) / basePlaneDenominator
        : -1.0;
    if (basePlaneDistance > 0.0 && basePlaneDistance < sceneDistance) {
        vec3 hit = direction * basePlaneDistance;
        vec3 relative = hit - base;
        float radial = length(relative - axis * dot(relative, axis));
        float ringRadius = clamp(ShockRadiusScale, 0.0, 1.0) * max(ShockMaxRadius, 0.001);
        float ringWidth = max(max(ShockMaxRadius * QUASAR_ORBITAL_RING_WIDTH_FACTOR,
            fullBeamRadius * 0.15), 0.001);
        float ringOffset = (radial - ringRadius) / ringWidth;
        float ring = exp(-ringOffset * ringOffset);
        if (ringStrength > 0.001 && ringRadius > 0.001
            && quasarLayerVisible(QUASAR_LAYER_EDGE, basePlaneDistance, sceneDistance,
                depthBias)) {
            emission += ringColor * ring * ringStrength
                * QUASAR_ORBITAL_RING_EMISSION;
            nearestEntry = min(nearestEntry, basePlaneDistance);
        }
    }

    if (rimGlow > 0.001 && basePlaneDistance > 0.0
        && basePlaneDistance < sceneDistance) {
        vec3 hit = direction * basePlaneDistance;
        vec3 relative = hit - base;
        float radial = length(relative - axis * dot(relative, axis));
        float rimRadius = max(fullBeamRadius, 0.001);
        float rimWidth = max(rimRadius * QUASAR_ORBITAL_RIM_WIDTH_FACTOR, 0.35);
        float inner = smoothstep(rimRadius - rimWidth, rimRadius, radial);
        float outer = 1.0 - smoothstep(rimRadius, rimRadius + rimWidth * 1.8, radial);
        float rim = inner * outer;
        float angle = atan(dot(relative, EffectForward), dot(relative, EffectRight));
        float flicker = 0.72 + 0.28 * quasarValueNoise3(vec3(
            angle * 2.4, AgeTicks * 0.08 + Seed * 3.0, Seed * 17.0));
        if (rim > 0.001
            && quasarLayerVisible(QUASAR_LAYER_EDGE, basePlaneDistance, sceneDistance,
                depthBias)) {
            emission += rimColor * rim * rimGlow * flicker
                * QUASAR_ORBITAL_RIM_EMISSION;
            nearestEntry = min(nearestEntry, basePlaneDistance);
        }
    }

    if (flash > 0.001) {
        float flashEntry = raySphereEntry(vec3(0.0), direction, base,
            max(FlashRadius, 0.001));
        if (flashEntry >= 0.0 && flashEntry < sceneDistance) {
            vec3 hit = direction * flashEntry;
            float normalized = length(hit - base) / max(FlashRadius, 0.001);
            float sphereFlash = exp(-normalized * normalized * 2.5) * flash;
            emission += activeFlashColor * sphereFlash * QUASAR_ORBITAL_FLASH_EMISSION;
            nearestEntry = min(nearestEntry, flashEntry);
        }

        vec2 baseUv = projectPoint(base);
        if (baseUv.x >= 0.0 && baseDistance < sceneDistance + depthBias) {
            float pixelSize = max(1.0 / max(ViewportWidth, 1.0),
                1.0 / max(ViewportHeight, 1.0));
            float width = max(abs(WorldProjMat[1][1]) * FlashRadius
                / max(2.0 * baseDistance, 0.001), pixelSize * 1.5);
            float offset = length(texCoord - baseUv) / width;
            float halo = exp(-offset * offset * 2.0) * flash;
            emission += activeFlashColor * halo * QUASAR_ORBITAL_SCREEN_HALO;
        }
    }

    vec3 platformEmission = vec3(0.0);
    float platformEntry = sceneDistance;
    float platformBlock = 0.0;
    float hullAlpha = 0.0;
    bool platformForeground = false;
    if (platformVisible > 0.001) {
        vec3 platformOrigin = -quasarWorldDirectionToEffect(EffectOrigin)
            - vec3(0.0, PlatformAltitude, 0.0);
        vec3 platformDirection = quasarWorldDirectionToEffect(direction);
        if (dot(platformDirection, platformDirection) > DIRECTION_EPSILON) {
            platformDirection = normalize(platformDirection);
            float muzzleY = min(0.0, BeamTopHeight - PlatformAltitude);
            float hitDistance;
            float closestDistance;
            vec3 hitPoint;
            vec3 closestPoint;
            bool hit = quasarOrbitalPlatformTrace(platformOrigin, platformDirection,
                muzzleY, platformMaxDistance, int(clamp(MarchSteps, 8.0, float(MAX_STEPS))),
                hitDistance, closestDistance, hitPoint, closestPoint);
            float surfaceDistance = hit
                ? hitDistance : length(closestPoint - platformOrigin);
            float pixelWorld = max(2.0 * surfaceDistance
                / max(abs(WorldProjMat[1][1]) * max(ViewportHeight, 1.0), 1.0), 0.02);
            float coverage = hit ? 1.0
                : 1.0 - smoothstep(0.0, pixelWorld, closestDistance);
            if (coverage > 0.001) {
                bool depthVisible = quasarLayerVisible(QUASAR_LAYER_FILL,
                    surfaceDistance, platformMaxDistance, platformDepthBias)
                    && (hit || surfaceDistance
                        < platformMaxDistance - platformDepthBias);
                bool foreground = quasarForegroundLayer(foregroundDistance,
                    foregroundDepthValid, surfaceDistance, 0.5, foregroundDelta);
                platformForeground = foreground;
                if (depthVisible) {
                    float visible = coverage * platformVisible;
                    vec3 surfacePoint = hit ? hitPoint : closestPoint;
                    vec3 normal = quasarOrbitalPlatformNormal(surfacePoint, muzzleY);
                    vec3 shade = platformShade(surfacePoint, normal, muzzleY, daylight);
                    platformBlock = visible;
                    platformEntry = surfaceDistance;
                    float additive = visible * (foreground
                        ? QUASAR_ORBITAL_PLATFORM_FOREGROUND_EMISSION : 1.0);
                    platformEmission = shade * additive;
                    hullAlpha = foreground ? 0.0 : visible;
                }
            }
        }
    }

    // A nearer solid hull blocks the additive beam and impact light from above.
    if (platformBlock > 0.001 && platformEntry < nearestEntry) {
        emission *= 1.0 - platformBlock;
    }

    float waterCosine = abs(dot(direction, vec3(0.0, 1.0, 0.0)));
    if (MediumSurfaceActive > 0.5 && abs(direction.y) > DIRECTION_EPSILON) {
        float surfaceDistance = MediumSurfaceY / direction.y;
        if (surfaceDistance > 0.0 && surfaceDistance < sceneDistance) {
            waterCosine = abs(direction.y);
        }
    }
    float surfaceTransmission = quasarMediumSurfaceTransmittance(waterCosine);
    emission *= surfaceTransmission;
    platformEmission *= surfaceTransmission;
    emission = quasarMediumFog(emission, quasarMediumFogDistance(nearestEntry));
    platformEmission = quasarMediumFog(platformEmission,
        quasarMediumFogDistance(platformEntry));

    // Only a depth-confirmed translucent layer in front may tint the emission.
    // Background water, ice, clouds, or weather also differ from the opaque
    // color; treating that delta as foreground prints them onto the beam.
    float foregroundEntry = max(nearestEntry, 0.0);
    bool foreground = quasarForegroundLayer(
        foregroundDistance, foregroundDepthValid, foregroundEntry, 0.5, foregroundDelta);
    vec3 rawScene = texture(RawSceneSampler, texCoord).rgb;
    vec3 opaqueScene = texture(OpaqueColorSampler, texCoord).rgb;
    bool opaqueValid = OpaqueColorValid > 0.5;
    emission *= quasarForegroundTint(foreground, rawScene, opaqueScene, opaqueValid);
    platformEmission *= quasarForegroundTint(platformForeground, rawScene, opaqueScene,
        opaqueValid);
    emission += platformEmission;
    emission *= 1.0 - smoothstep(0.965, 1.0, Progress);

    if (all(lessThan(emission, vec3(0.0001))) && hullAlpha < 0.0001) {
        fragColor = vec4(0.0);
        return;
    }
    // The beam remains additive light with zero coverage alpha. Only a
    // depth-confirmed platform hull hit writes coverage, so the solid layer
    // cannot promote the fullscreen mask or replace a foreground surface.
    fragColor = vec4(emission, hullAlpha);
}
