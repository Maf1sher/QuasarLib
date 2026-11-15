#version 150

#moj_import <fog.glsl>
#moj_import <quasar_lib:particle_field.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ParticleTransform;
uniform int ParticleSeedLow;
uniform int ParticleSeedHigh;
uniform float ParticleAge;
uniform float ParticleLifetime;
uniform int ParticleMode;
uniform int ParticleShape;
uniform vec3 ParticleShapeA;
uniform vec3 ParticleShapeB;
uniform float ParticleShapeR0;
uniform float ParticleShapeR1;
uniform float ParticleShapeFlags;
uniform int ParticleVelocityMode;
uniform vec3 ParticleDirection;
uniform vec2 ParticleSpeed;
uniform float ParticleSpread;
uniform float ParticleGravity;
uniform float ParticleDrag;
uniform float ParticleTurbulence;
uniform vec2 ParticleSize;
uniform vec4 ParticleColorStart;
uniform vec4 ParticleColorEnd;
uniform vec4 ColorModulator;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float particleViewDistance;

void main() {
    uint seedLow = uint(ParticleSeedLow);
    uint seedHigh = uint(ParticleSeedHigh);
    int index = gl_InstanceID;

    vec3 spawn = quasarParticleSpawn(ParticleShape, ParticleShapeA, ParticleShapeB,
        ParticleShapeR0, ParticleShapeR1, ParticleShapeFlags, seedLow, seedHigh, index);
    vec3 center = vec3(0.0);
    if (ParticleShape == 1 || ParticleShape == 3) {
        center = ParticleShapeA;
    } else if (ParticleShape == 2 || ParticleShape == 4) {
        center = (ParticleShapeA + ParticleShapeB) * 0.5;
    }
    vec3 randomDirection = quasarParticleRandomDirection(seedLow, seedHigh, index, 10);
    float speed = mix(ParticleSpeed.x, ParticleSpeed.y,
        quasarParticleRandom(seedLow, seedHigh, index, 12));
    vec3 velocity = quasarParticleVelocity(ParticleVelocityMode, ParticleDirection, spawn,
        center, randomDirection, speed, ParticleSpread);

    float phase = quasarParticleRandom(seedLow, seedHigh, index, 2);
    float age = quasarParticleAge(ParticleMode, ParticleAge, ParticleLifetime, phase);
    if (age < 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        vertexDistance = 0.0;
        vertexColor = vec4(0.0);
        texCoord0 = UV0;
        particleViewDistance = 0.0;
        return;
    }

    vec3 turbulencePhase = vec3(
        quasarParticleRandom(seedLow, seedHigh, index, 13),
        quasarParticleRandom(seedLow, seedHigh, index, 14),
        quasarParticleRandom(seedLow, seedHigh, index, 15)
    ) * QUASAR_PARTICLE_TWO_PI;
    vec3 local = quasarParticlePosition(spawn, velocity, ParticleGravity, ParticleDrag,
        ParticleTurbulence, age, turbulencePhase);
    vec3 world = (ParticleTransform * vec4(local, 1.0)).xyz;
    vec3 viewCenter = (ModelViewMat * vec4(world, 1.0)).xyz;

    float progress = clamp(age / ParticleLifetime, 0.0, 1.0);
    float size = mix(ParticleSize.x, ParticleSize.y, progress);
    vec2 corner = Position.xy - vec2(0.0, 0.5);
    vec3 viewPos = viewCenter + vec3(corner * size, 0.0);

    gl_Position = ProjMat * vec4(viewPos, 1.0);
    vertexDistance = fog_distance(world, FogShape);
    vertexColor = mix(ParticleColorStart, ParticleColorEnd, progress)
        * Color * ColorModulator;
    texCoord0 = UV0;
    particleViewDistance = length(viewPos);
}
