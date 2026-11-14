#ifndef QUASAR_SOFT_PARTICLES_GLSL
#define QUASAR_SOFT_PARTICLES_GLSL

// Pure soft-particle helpers. Callers sample their own depth texture and pass
// the camera-relative scene distance, usually from quasarViewDistance(). The
// module declares no uniforms or samplers so it cannot change a program's
// binding contract.
const float QUASAR_SOFT_PARTICLE_MIN_SOFTNESS = 0.001;
const float QUASAR_SOFT_PARTICLE_MIN_NEAR_RANGE = 0.0001;

/**
 * Fades a particle or sprite as it approaches the scene surface behind it.
 * particleDistance is measured from the same camera, in the same frame, as
 * sceneDistance. Sky and far-limit scene distances keep the particle visible.
 */
float quasarSoftParticleFade(float sceneDistance, float particleDistance,
    float softness) {
    float thickness = max(softness, QUASAR_SOFT_PARTICLE_MIN_SOFTNESS);
    return smoothstep(0.0, thickness, sceneDistance - particleDistance);
}

/**
 * Fades a particle that is too close to the camera. Degenerate ranges return
 * one so a bad threshold cannot erase the particle.
 */
float quasarParticleNearFade(float cameraDistance, float fadeStart,
    float fadeEnd) {
    if (fadeEnd <= fadeStart + QUASAR_SOFT_PARTICLE_MIN_NEAR_RANGE) {
        return 1.0;
    }
    return smoothstep(fadeStart, fadeEnd, cameraDistance);
}

#endif
