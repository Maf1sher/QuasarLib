#ifndef QUASAR_PARTICLE_FIELD_GLSL
#define QUASAR_PARTICLE_FIELD_GLSL

// Stateless micro-particle field math. Every value is derived from the field seed,
// the particle instance index, and the particle age. The module declares no
// uniforms or samplers; the consuming stage owns the binding contract.
const float QUASAR_PARTICLE_TWO_PI = 6.28318530717958647692;
const float QUASAR_PARTICLE_MIN_DRAG = 0.0001;
const float QUASAR_PARTICLE_EPSILON = 0.000000000001;

uint quasarParticleHash(uint seedLow, uint seedHigh, int index, int stream) {
    uint value = seedLow ^ (seedHigh * 0x9E3779B9u)
        ^ (uint(index) * 0x85EBCA6Bu) ^ (uint(stream) * 0xC2B2AE35u);
    value ^= value >> 16u;
    value *= 0x7FEB352Du;
    value ^= value >> 15u;
    value *= 0x846CA68Bu;
    value ^= value >> 16u;
    return value;
}

float quasarParticleRandom(uint seedLow, uint seedHigh, int index, int stream) {
    return float(quasarParticleHash(seedLow, seedHigh, index, stream))
        * (1.0 / 4294967296.0);
}

vec3 quasarParticleTangent(vec3 normal) {
    vec3 reference = abs(normal.y) > 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    return normalize(cross(normal, reference));
}

vec3 quasarParticleRandomDirection(uint seedLow, uint seedHigh, int index, int stream) {
    float z = quasarParticleRandom(seedLow, seedHigh, index, stream) * 2.0 - 1.0;
    float angle = quasarParticleRandom(seedLow, seedHigh, index, stream + 1)
        * QUASAR_PARTICLE_TWO_PI;
    float radial = sqrt(max(0.0, 1.0 - z * z));
    return vec3(cos(angle) * radial, sin(angle) * radial, z);
}

/**
 * Samples one local spawn point. Shapes: 0 point, 1 sphere, 2 box, 3 disk,
 * 4 line. For a sphere, a=centre, r0=min radius, r1=max radius and flags
 * selects the surface. For a disk, a=centre and b=normal.
 */
vec3 quasarParticleSpawn(int shape, vec3 a, vec3 b, float r0, float r1, float flags,
    uint seedLow, uint seedHigh, int index) {
    if (shape == 1) {
        float z = quasarParticleRandom(seedLow, seedHigh, index, 0) * 2.0 - 1.0;
        float angle = quasarParticleRandom(seedLow, seedHigh, index, 1)
            * QUASAR_PARTICLE_TWO_PI;
        float radial = sqrt(max(0.0, 1.0 - z * z));
        float radius = flags > 0.5
            ? r1
            : mix(r0, r1, pow(quasarParticleRandom(seedLow, seedHigh, index, 2), 1.0 / 3.0));
        return a + vec3(cos(angle) * radial, sin(angle) * radial, z) * radius;
    }
    if (shape == 2) {
        return mix(a, b, vec3(
            quasarParticleRandom(seedLow, seedHigh, index, 0),
            quasarParticleRandom(seedLow, seedHigh, index, 1),
            quasarParticleRandom(seedLow, seedHigh, index, 2)
        ));
    }
    if (shape == 3) {
        vec3 normal = normalize(b);
        vec3 tangent = quasarParticleTangent(normal);
        vec3 bitangent = cross(normal, tangent);
        float angle = quasarParticleRandom(seedLow, seedHigh, index, 0)
            * QUASAR_PARTICLE_TWO_PI;
        float radius = mix(r0, r1, sqrt(quasarParticleRandom(seedLow, seedHigh, index, 1)));
        return a + tangent * (cos(angle) * radius) + bitangent * (sin(angle) * radius);
    }
    if (shape == 4) {
        vec3 direction = b - a;
        vec3 axis = length(direction) < QUASAR_PARTICLE_EPSILON
            ? vec3(0.0, 0.0, 1.0)
            : normalize(direction);
        vec3 tangent = quasarParticleTangent(axis);
        vec3 bitangent = cross(axis, tangent);
        float along = quasarParticleRandom(seedLow, seedHigh, index, 0);
        float angle = quasarParticleRandom(seedLow, seedHigh, index, 1)
            * QUASAR_PARTICLE_TWO_PI;
        float radius = r0 * sqrt(quasarParticleRandom(seedLow, seedHigh, index, 2));
        return a + direction * along + tangent * (cos(angle) * radius)
            + bitangent * (sin(angle) * radius);
    }
    return vec3(0.0);
}

vec3 quasarParticleVelocity(int mode, vec3 direction, vec3 spawn, vec3 center,
    vec3 randomDirection, float speed, float spread) {
    vec3 result = randomDirection;
    if (mode == 0 && length(direction) > QUASAR_PARTICLE_EPSILON) {
        result = normalize(mix(normalize(direction), randomDirection, spread));
    } else if (mode != 0) {
        vec3 offset = spawn - center;
        if (length(offset) > QUASAR_PARTICLE_EPSILON) {
            result = normalize(offset) * (mode == 1 ? 1.0 : -1.0);
        }
    }
    return result * speed;
}

float quasarParticleTravel(float drag, float age) {
    return drag <= QUASAR_PARTICLE_MIN_DRAG
        ? age
        : (1.0 - exp(-drag * age)) / drag;
}

float quasarParticleAge(int mode, float effectAge, float lifetime, float phase) {
    if (mode == 1) {
        return effectAge < lifetime ? effectAge : -1.0;
    }
    return mod(effectAge + phase * lifetime, lifetime);
}

vec3 quasarParticleTurbulence(float amplitude, float age, vec3 phase) {
    return vec3(
        sin(age * 0.7 + phase.x),
        sin(age * 0.9 + phase.y),
        sin(age * 1.1 + phase.z)
    ) * (amplitude * 0.5);
}

vec3 quasarParticlePosition(vec3 spawn, vec3 velocity, float gravity, float drag,
    float turbulence, float age, vec3 turbulencePhase) {
    vec3 position = spawn + velocity * quasarParticleTravel(drag, age);
    position.y -= 0.5 * gravity * age * age;
    return position + quasarParticleTurbulence(turbulence, age, turbulencePhase);
}

#endif
