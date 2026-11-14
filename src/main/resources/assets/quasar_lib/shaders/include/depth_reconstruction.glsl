#ifndef QUASAR_DEPTH_RECONSTRUCTION_GLSL
#define QUASAR_DEPTH_RECONSTRUCTION_GLSL

// Camera-group uniforms bound by EffectUniforms.CAMERA or
// ScreenEffectOperationContext.bindCameraInputs. Keep the far-plane ray and the
// view-distance reconstruction here so every screen shader uses one convention.
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;

const float QUASAR_DEPTH_W_EPSILON = 0.000001;
const float QUASAR_DEPTH_MIN_DISTANCE = 0.01;
const float QUASAR_DEPTH_MAX_DISTANCE = 1000000.0;

/**
 * Reconstructs a normalized world-space ray for normalized screen coordinates.
 * Samples the far plane: the projection can carry a view-space bob/hurt
 * translation, and inverting that on the near plane amplifies it into a large
 * angular error.
 */
vec3 quasarWorldRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    if (abs(view.w) < QUASAR_DEPTH_W_EPSILON) {
        return vec3(0.0, 0.0, -1.0);
    }
    view.xyz /= view.w;
    return normalize((InvViewMat * vec4(view.xyz, 0.0)).xyz);
}

/**
 * Reconstructs the camera-space distance of one sampled scene depth. A
 * degenerate clip-space w returns the far limit so the caller treats the pixel
 * as sky instead of producing a NaN or zero-distance surface.
 */
float quasarViewDistance(float depth, vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, depth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    if (abs(view.w) < QUASAR_DEPTH_W_EPSILON) {
        return QUASAR_DEPTH_MAX_DISTANCE;
    }
    view.xyz /= view.w;
    return clamp(length(view.xyz), QUASAR_DEPTH_MIN_DISTANCE,
        QUASAR_DEPTH_MAX_DISTANCE);
}

#endif
