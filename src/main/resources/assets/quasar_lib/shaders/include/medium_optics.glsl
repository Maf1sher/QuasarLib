#ifndef QUASAR_MEDIUM_OPTICS_GLSL
#define QUASAR_MEDIUM_OPTICS_GLSL

// Standard water-medium optics for screen effects. The CPU mirror is
// ScreenEffectMediumOptics. Callers apply fog and surface transmission only to
// their emission layers, including bloom, and never to the re-added background.
uniform float MediumSurfaceActive;
uniform float MediumSurfaceY;
uniform float MediumWaterIor;
uniform float MediumSuppressAboveWater;
uniform float MediumPathLength;
uniform float MediumCameraSubmerged;
uniform float MediumFogStart;
uniform float MediumFogEnd;
uniform vec3 MediumFogColor;
uniform float MediumFogActive;

const float QUASAR_MEDIUM_WATER_IOR = 1.333;
const float QUASAR_MEDIUM_FRESNEL_FLOOR = 0.0001;
const float QUASAR_MEDIUM_DIRECTION_EPSILON = 0.001;
const float QUASAR_MEDIUM_TIR_EPSILON = 0.000001;
const float QUASAR_MEDIUM_FOG_RANGE_FLOOR = 0.0001;

struct QuasarMediumRay {
    vec3 origin;
    vec3 direction;
    vec3 normal;
    float surfaceDistance;
    float incidenceCosine;
    bool totalInternalReflection;
};

/** Unpolarised Fresnel reflectance; approaches one at the critical angle. */
float quasarMediumFresnel(float cosIncidence, float incidentIor, float transmittedIor) {
    float cosI = clamp(abs(cosIncidence), 0.0, 1.0);
    float sinT2 = (incidentIor * incidentIor) / (transmittedIor * transmittedIor)
        * max(0.0, 1.0 - cosI * cosI);
    if (sinT2 >= 1.0) {
        return 1.0;
    }
    float cosT = sqrt(1.0 - sinT2);
    float rs = (incidentIor * cosI - transmittedIor * cosT)
        / max(incidentIor * cosI + transmittedIor * cosT, QUASAR_MEDIUM_FRESNEL_FLOOR);
    float rp = (incidentIor * cosT - transmittedIor * cosI)
        / max(incidentIor * cosT + transmittedIor * cosI, QUASAR_MEDIUM_FRESNEL_FLOOR);
    return clamp(0.5 * (rs * rs + rp * rp), 0.0, 1.0);
}

/** Fresnel reflectance for the camera side of the active water surface. */
float quasarMediumFromCameraFresnel(float cosIncidence) {
    float incidentIor = mix(1.0, MediumWaterIor, MediumCameraSubmerged);
    float transmittedIor = mix(MediumWaterIor, 1.0, MediumCameraSubmerged);
    return quasarMediumFresnel(cosIncidence, incidentIor, transmittedIor);
}

/** Surface transmission; zero on total internal reflection. */
float quasarMediumSurfaceTransmittance(float cosIncidence) {
    if (MediumSurfaceActive < 0.5) {
        return 1.0;
    }
    return 1.0 - quasarMediumFromCameraFresnel(cosIncidence);
}

QuasarMediumRay quasarMediumRay(vec3 origin, vec3 direction) {
    QuasarMediumRay ray;
    ray.origin = origin;
    ray.direction = direction;
    ray.normal = vec3(0.0, 1.0, 0.0);
    ray.surfaceDistance = -1.0;
    ray.incidenceCosine = 1.0;
    ray.totalInternalReflection = false;
    return ray;
}

/**
 * Bends a camera-relative view ray at the active water plane. The caller supplies
 * the world-stable surface normal at the hit; the returned ray keeps the input
 * origin's frame. Total internal reflection leaves the straight direction and
 * sets the flag so the caller hides the effect.
 */
QuasarMediumRay quasarMediumRefract(vec3 origin, vec3 direction, vec3 surfaceNormal) {
    QuasarMediumRay ray = quasarMediumRay(origin, direction);
    if (MediumSurfaceActive < 0.5 || abs(direction.y) < QUASAR_MEDIUM_DIRECTION_EPSILON) {
        return ray;
    }
    float surfaceDistance = MediumSurfaceY / direction.y;
    if (surfaceDistance <= 0.0) {
        return ray;
    }
    vec3 normal = normalize(surfaceNormal);
    if (dot(normal, direction) > 0.0) {
        normal = -normal;
    }
    ray.normal = normal;
    ray.incidenceCosine = clamp(abs(dot(direction, normal)), 0.0, 1.0);
    float eta = MediumCameraSubmerged > 0.5 ? MediumWaterIor : 1.0 / MediumWaterIor;
    vec3 bent = refract(direction, normal, eta);
    ray.surfaceDistance = surfaceDistance;
    if (dot(bent, bent) < QUASAR_MEDIUM_TIR_EPSILON) {
        ray.totalInternalReflection = true;
        return ray;
    }
    ray.origin = origin + direction * surfaceDistance;
    ray.direction = normalize(bent);
    return ray;
}

/** Uses the whole submerged path, or a traced fallback when the path is empty. */
float quasarMediumFogDistance(float fallbackDistance) {
    return MediumPathLength > 0.0 ? MediumPathLength : max(fallbackDistance, 0.0);
}

float quasarMediumFogFactor(float distance) {
    if (MediumFogActive < 0.5) {
        return 0.0;
    }
    float range = max(MediumFogEnd - MediumFogStart, QUASAR_MEDIUM_FOG_RANGE_FLOOR);
    float value = clamp((distance - MediumFogStart) / range, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

/** Fog belongs to emission only; never call this on the lensed background. */
vec3 quasarMediumFog(vec3 emission, float distance) {
    return mix(emission, MediumFogColor, quasarMediumFogFactor(distance));
}

/**
 * Bloom is additive emission: attenuate it instead of adding the fog colour a
 * second time.
 */
float quasarMediumFogTransmission(float distance) {
    return 1.0 - quasarMediumFogFactor(distance);
}

/** Straight-ray lens and vortex work is invalid across or inside a medium. */
bool quasarMediumWarpAllowed() {
    return MediumSurfaceActive < 0.5
        && MediumCameraSubmerged < 0.5
        && MediumPathLength <= 0.0
        && MediumSuppressAboveWater < 0.5;
}

#endif
