#ifndef QUASAR_ORBITAL_PLATFORM_GLSL
#define QUASAR_ORBITAL_PLATFORM_GLSL

// Content-owned signed-distance field for the orbital-laser ring platform. The
// module is self-contained so every shader test can compile it on its own; the
// consuming stage owns the camera/effect-space conversion and all depth tests.

uniform float PlatformAltitude;
uniform float PlatformOuterRadius;
uniform float PlatformInnerRadius;
uniform float PlatformVisible;
uniform float PlatformReveal;
uniform float PlatformSpin;
uniform float PlatformRecoil;
uniform float PlatformExit;
uniform float PlatformDetail;

const int QUASAR_PLATFORM_MAX_STEPS = 56;
const int QUASAR_PLATFORM_MIN_STEPS = 20;
const int QUASAR_PLATFORM_SIDES = 12;
const float QUASAR_PLATFORM_TAU = 6.283185307179586;
const float QUASAR_PLATFORM_INNER_RING_DETAIL = 0.45;
const float QUASAR_PLATFORM_TRUSS_DETAIL = 0.60;
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
const float QUASAR_PLATFORM_SPIN_TURNS = 1.15;
const float QUASAR_PLATFORM_COUNTER_SPIN = 1.7;
const float QUASAR_PLATFORM_RECOIL_LIFT = 1.4;
const float QUASAR_PLATFORM_CLIP_MARGIN = 2.5;
const float QUASAR_PLATFORM_SCAN_WIDTH = 2.4;
const float QUASAR_PLATFORM_EXIT_WIDTH = 2.0;
const float QUASAR_PLATFORM_BOB_SPEED = 0.045;
const float QUASAR_PLATFORM_BOB_HEIGHT = 0.35;
const float QUASAR_PLATFORM_APERTURE_GROWTH = 2.6;
const float QUASAR_PLATFORM_MUZZLE_FLASH_RADIUS = 9.0;

vec2 quasarPlatformRotate(vec2 point, float angle) {
    float cosine = cos(angle);
    float sine = sin(angle);
    return vec2(cosine * point.x - sine * point.y,
        sine * point.x + cosine * point.y);
}

float quasarPlatformSpinAngle() {
    return PlatformSpin * QUASAR_PLATFORM_SPIN_TURNS * QUASAR_PLATFORM_TAU;
}

float quasarPlatformCounterAngle() {
    return -quasarPlatformSpinAngle() * QUASAR_PLATFORM_COUNTER_SPIN;
}

float quasarPlatformRevealLimit(float muzzleY) {
    float bottom = min(muzzleY, -QUASAR_PLATFORM_HULL_HALF_THICKNESS)
        - QUASAR_PLATFORM_CLIP_MARGIN;
    float top = QUASAR_PLATFORM_HULL_HALF_THICKNESS + QUASAR_PLATFORM_RECOIL_LIFT
        + QUASAR_PLATFORM_CLIP_MARGIN;
    return mix(bottom, top, clamp(PlatformReveal, 0.0, 1.0));
}

float quasarPlatformExitLimit(float muzzleY) {
    float bottom = min(muzzleY, -QUASAR_PLATFORM_HULL_HALF_THICKNESS)
        - QUASAR_PLATFORM_CLIP_MARGIN;
    float top = QUASAR_PLATFORM_HULL_HALF_THICKNESS + QUASAR_PLATFORM_RECOIL_LIFT
        + QUASAR_PLATFORM_CLIP_MARGIN;
    return mix(top, bottom, clamp(PlatformExit, 0.0, 1.0));
}

float quasarPlatformSdBox2D(vec2 point, vec2 halfSize) {
    vec2 delta = abs(point) - halfSize;
    return length(max(delta, vec2(0.0))) + min(max(delta.x, delta.y), 0.0);
}

float quasarPlatformOuterRing(vec3 point, float outerRadius) {
    float halfBand = QUASAR_PLATFORM_RING_BAND * 0.5;
    float midRadius = max(outerRadius - halfBand, 0.001);
    float sector = QUASAR_PLATFORM_TAU / float(QUASAR_PLATFORM_SIDES);
    float angle = atan(point.z, point.x) - quasarPlatformSpinAngle();
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

float quasarPlatformTrusses(vec3 point, float outerRadius, int count) {
    float distance = 1000000.0;
    float spinAngle = quasarPlatformSpinAngle();
    for (int index = 0; index < 3; index++) {
        if (index >= count) {
            break;
        }
        float angle = float(index) * 3.141592653589793 / 3.0 + spinAngle;
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

float quasarOrbitalPlatformSdf(vec3 point, float muzzleY, float hullLift) {
    // The stage passes the idle bob and recoil lift. They move the hull and
    // trusses while the barrel stays locked on target; the barrel and muzzle
    // are sampled at the original point.
    vec3 hullPoint = point;
    hullPoint.y -= hullLift;
    float detail = clamp(PlatformDetail, 0.0, 1.0);
    float distance = quasarPlatformOuterRing(hullPoint, PlatformOuterRadius);
    if (detail >= QUASAR_PLATFORM_INNER_RING_DETAIL) {
        distance = min(distance, quasarPlatformInnerRing(hullPoint, PlatformInnerRadius));
    }
    int trussCount = detail >= QUASAR_PLATFORM_TRUSS_DETAIL ? 3 : 1;
    distance = min(distance, quasarPlatformTrusses(hullPoint, PlatformOuterRadius, trussCount));
    distance = min(distance, quasarPlatformHub(hullPoint));
    distance = min(distance, quasarPlatformBarrel(point, muzzleY));
    distance = min(distance, quasarPlatformMuzzle(point, muzzleY));

    // Reveal and exit are half-space cuts. A horizontal cut keeps the distance
    // field exact, so the raymarch cannot overstep the scan edge.
    float limit = min(quasarPlatformRevealLimit(muzzleY),
        quasarPlatformExitLimit(muzzleY));
    return max(distance, point.y - limit);
}

vec3 quasarOrbitalPlatformNormal(vec3 point, float muzzleY, float hullLift) {
    float epsilon = 0.15;
    vec3 gradient = vec3(
        quasarOrbitalPlatformSdf(point + vec3(epsilon, 0.0, 0.0), muzzleY, hullLift)
            - quasarOrbitalPlatformSdf(point - vec3(epsilon, 0.0, 0.0), muzzleY, hullLift),
        quasarOrbitalPlatformSdf(point + vec3(0.0, epsilon, 0.0), muzzleY, hullLift)
            - quasarOrbitalPlatformSdf(point - vec3(0.0, epsilon, 0.0), muzzleY, hullLift),
        quasarOrbitalPlatformSdf(point + vec3(0.0, 0.0, epsilon), muzzleY, hullLift)
            - quasarOrbitalPlatformSdf(point - vec3(0.0, 0.0, epsilon), muzzleY, hullLift)
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
    float hullLift, float maxDistance, int maxSteps, out float hitDistance,
    out float closestDistance, out vec3 hitPoint, out vec3 closestPoint) {
    hitDistance = -1.0;
    closestDistance = 1000000.0;
    hitPoint = origin;
    closestPoint = origin;

    float reach = PlatformOuterRadius + QUASAR_PLATFORM_BOUND_MARGIN;
    float bottom = min(muzzleY, -QUASAR_PLATFORM_HULL_HALF_THICKNESS)
        - QUASAR_PLATFORM_BOUND_MARGIN;
    float top = QUASAR_PLATFORM_HULL_HALF_THICKNESS + QUASAR_PLATFORM_BOUND_MARGIN
        + QUASAR_PLATFORM_RECOIL_LIFT;
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
    float limit = min(quasarPlatformRevealLimit(muzzleY),
        quasarPlatformExitLimit(muzzleY));
    float distance = start;
    for (int index = 0; index < QUASAR_PLATFORM_MAX_STEPS; index++) {
        if (index >= steps) {
            break;
        }
        vec3 point = origin + direction * distance;
        float field = quasarOrbitalPlatformSdf(point, muzzleY, hullLift);
        // Only the visible half-space may seed edge coverage; the clipped part
        // is still in the unclipped distance field and would ghost the scan.
        if (point.y <= limit + QUASAR_PLATFORM_SURFACE_EPSILON
            && field < closestDistance) {
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
