#ifndef QUASAR_ORBITAL_PLATFORM_GLSL
#define QUASAR_ORBITAL_PLATFORM_GLSL

// Content-owned signed-distance field for the orbital-laser ring platform. The
// module is self-contained so every shader test can compile it on its own; the
// consuming stage owns the camera/effect-space conversion and all depth tests.

uniform float PlatformAltitude;
uniform float PlatformOuterRadius;
uniform float PlatformInnerRadius;
uniform float PlatformVisible;

const int QUASAR_PLATFORM_MAX_STEPS = 56;
const int QUASAR_PLATFORM_SIDES = 12;
const float QUASAR_PLATFORM_TAU = 6.283185307179586;
const float QUASAR_PLATFORM_HULL_HALF_THICKNESS = 2.5;
const float QUASAR_PLATFORM_RING_BAND = 8.0;
const float QUASAR_PLATFORM_INNER_TUBE = 2.2;
const float QUASAR_PLATFORM_TRUSS_HALF_WIDTH = 2.0;
const float QUASAR_PLATFORM_TRUSS_HALF_THICKNESS = 1.2;
const float QUASAR_PLATFORM_HUB_RADIUS = 13.0;
const float QUASAR_PLATFORM_HUB_HALF_HEIGHT = 3.6;
const float QUASAR_PLATFORM_BARREL_RADIUS = 7.0;
const float QUASAR_PLATFORM_MUZZLE_LIP_RADIUS = 1.3;
const float QUASAR_PLATFORM_BOUND_MARGIN = 2.5;
const float QUASAR_PLATFORM_SURFACE_EPSILON = 0.02;
const float QUASAR_PLATFORM_MIN_STEP = 0.02;

vec2 quasarPlatformRotate(vec2 point, float angle) {
    float cosine = cos(angle);
    float sine = sin(angle);
    return vec2(cosine * point.x - sine * point.y,
        sine * point.x + cosine * point.y);
}

float quasarPlatformSdBox2D(vec2 point, vec2 halfSize) {
    vec2 delta = abs(point) - halfSize;
    return length(max(delta, vec2(0.0))) + min(max(delta.x, delta.y), 0.0);
}

float quasarPlatformOuterRing(vec3 point, float outerRadius) {
    float halfBand = QUASAR_PLATFORM_RING_BAND * 0.5;
    float midRadius = max(outerRadius - halfBand, 0.001);
    float sector = QUASAR_PLATFORM_TAU / float(QUASAR_PLATFORM_SIDES);
    float angle = atan(point.z, point.x);
    float localAngle = mod(angle + sector * 0.5, sector) - sector * 0.5;
    vec2 segment = quasarPlatformRotate(point.xz, -localAngle);
    segment.x -= midRadius;
    float chord = midRadius * sin(sector * 0.5);
    float halfLength = max(chord - 1.0, 0.5);
    float distance = quasarPlatformSdBox2D(segment,
        vec2(halfBand, halfLength));
    return max(distance, abs(point.y) - QUASAR_PLATFORM_HULL_HALF_THICKNESS);
}

float quasarPlatformInnerRing(vec3 point, float innerRadius) {
    float radial = length(point.xz);
    return length(vec2(radial - innerRadius, point.y))
        - QUASAR_PLATFORM_INNER_TUBE;
}

float quasarPlatformTrusses(vec3 point, float outerRadius) {
    float distance = 1000000.0;
    for (int index = 0; index < 3; index++) {
        float angle = float(index) * 3.141592653589793 / 3.0;
        vec2 local = quasarPlatformRotate(point.xz, -angle);
        float truss = quasarPlatformSdBox2D(local,
            vec2(outerRadius, QUASAR_PLATFORM_TRUSS_HALF_WIDTH));
        distance = min(distance, max(truss,
            abs(point.y) - QUASAR_PLATFORM_TRUSS_HALF_THICKNESS));
    }
    return distance;
}

float quasarPlatformHub(vec3 point) {
    return max(length(point.xz) - QUASAR_PLATFORM_HUB_RADIUS,
        abs(point.y) - QUASAR_PLATFORM_HUB_HALF_HEIGHT);
}

float quasarPlatformBarrel(vec3 point, float muzzleY) {
    float top = -QUASAR_PLATFORM_HULL_HALF_THICKNESS * 0.5;
    float bottom = min(muzzleY, top - 0.5);
    float center = (top + bottom) * 0.5;
    float halfHeight = max((top - bottom) * 0.5, 0.5);
    return max(length(point.xz) - QUASAR_PLATFORM_BARREL_RADIUS,
        abs(point.y - center) - halfHeight);
}

float quasarPlatformMuzzle(vec3 point, float muzzleY) {
    float ringRadius = QUASAR_PLATFORM_BARREL_RADIUS + 0.9;
    return length(vec2(length(point.xz) - ringRadius, point.y - muzzleY))
        - QUASAR_PLATFORM_MUZZLE_LIP_RADIUS;
}

float quasarOrbitalPlatformSdf(vec3 point, float muzzleY) {
    float distance = quasarPlatformOuterRing(point, PlatformOuterRadius);
    distance = min(distance, quasarPlatformInnerRing(point, PlatformInnerRadius));
    distance = min(distance, quasarPlatformTrusses(point, PlatformOuterRadius));
    distance = min(distance, quasarPlatformHub(point));
    distance = min(distance, quasarPlatformBarrel(point, muzzleY));
    return min(distance, quasarPlatformMuzzle(point, muzzleY));
}

vec3 quasarOrbitalPlatformNormal(vec3 point, float muzzleY) {
    float epsilon = 0.15;
    vec3 gradient = vec3(
        quasarOrbitalPlatformSdf(point + vec3(epsilon, 0.0, 0.0), muzzleY)
            - quasarOrbitalPlatformSdf(point - vec3(epsilon, 0.0, 0.0), muzzleY),
        quasarOrbitalPlatformSdf(point + vec3(0.0, epsilon, 0.0), muzzleY)
            - quasarOrbitalPlatformSdf(point - vec3(0.0, epsilon, 0.0), muzzleY),
        quasarOrbitalPlatformSdf(point + vec3(0.0, 0.0, epsilon), muzzleY)
            - quasarOrbitalPlatformSdf(point - vec3(0.0, 0.0, epsilon), muzzleY)
    );
    float lengthSquared = dot(gradient, gradient);
    return lengthSquared > 0.00000001
        ? gradient * inversesqrt(lengthSquared) : vec3(0.0, 1.0, 0.0);
}

bool quasarPlatformRayBox(vec3 origin, vec3 direction, vec3 minimum, vec3 maximum,
    out float enter, out float exitDistance) {
    vec3 safeDirection = direction;
    if (abs(safeDirection.x) < 0.00000001) {
        safeDirection.x = 0.00000001;
    }
    if (abs(safeDirection.y) < 0.00000001) {
        safeDirection.y = 0.00000001;
    }
    if (abs(safeDirection.z) < 0.00000001) {
        safeDirection.z = 0.00000001;
    }
    vec3 inverse = 1.0 / safeDirection;
    vec3 first = (minimum - origin) * inverse;
    vec3 second = (maximum - origin) * inverse;
    vec3 nearPlane = min(first, second);
    vec3 farPlane = max(first, second);
    enter = max(max(nearPlane.x, nearPlane.y), nearPlane.z);
    exitDistance = min(min(farPlane.x, farPlane.y), farPlane.z);
    return exitDistance > max(enter, 0.0);
}

bool quasarOrbitalPlatformTrace(vec3 origin, vec3 direction, float muzzleY,
    float maxDistance, int maxSteps, out float hitDistance, out float closestDistance,
    out vec3 hitPoint, out vec3 closestPoint) {
    hitDistance = -1.0;
    closestDistance = 1000000.0;
    hitPoint = origin;
    closestPoint = origin;

    float reach = PlatformOuterRadius + QUASAR_PLATFORM_BOUND_MARGIN;
    float bottom = min(muzzleY, -QUASAR_PLATFORM_HULL_HALF_THICKNESS)
        - QUASAR_PLATFORM_BOUND_MARGIN;
    float top = QUASAR_PLATFORM_HULL_HALF_THICKNESS + QUASAR_PLATFORM_BOUND_MARGIN;
    float enter;
    float exitDistance;
    if (!quasarPlatformRayBox(origin, direction, vec3(-reach, bottom, -reach),
        vec3(reach, top, reach), enter, exitDistance)) {
        return false;
    }

    float start = max(enter, 0.0);
    float end = min(exitDistance, maxDistance);
    if (end <= start) {
        return false;
    }

    int steps = max(1, min(maxSteps, QUASAR_PLATFORM_MAX_STEPS));
    float distance = start;
    for (int index = 0; index < QUASAR_PLATFORM_MAX_STEPS; index++) {
        if (index >= steps) {
            break;
        }
        vec3 point = origin + direction * distance;
        float field = quasarOrbitalPlatformSdf(point, muzzleY);
        if (field < closestDistance) {
            closestDistance = field;
            closestPoint = point;
        }
        if (field < QUASAR_PLATFORM_SURFACE_EPSILON) {
            hitDistance = distance;
            hitPoint = point;
            closestDistance = 0.0;
            closestPoint = point;
            return true;
        }
        distance += max(field, QUASAR_PLATFORM_MIN_STEP);
        if (distance > end) {
            break;
        }
    }
    return false;
}

#endif
