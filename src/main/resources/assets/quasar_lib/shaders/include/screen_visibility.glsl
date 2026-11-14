#ifndef QUASAR_SCREEN_VISIBILITY_GLSL
#define QUASAR_SCREEN_VISIBILITY_GLSL

// Per-layer visibility for screen-space effects. The include deliberately
// declares no uniforms or samplers: callers sample their own captures and pass
// camera-relative distances, so the shader JSON binding contract stays local to
// each program.

const int QUASAR_LAYER_EMISSION = 0;
const int QUASAR_LAYER_EDGE = 1;
const int QUASAR_LAYER_FILL = 2;
const int QUASAR_LAYER_LENS = 3;
const int QUASAR_LAYER_SHADOW = 4;

// All layers currently share the former black-hole depth range. The layer
// argument keeps call sites explicit and lets a layer widen later.
const float QUASAR_VISIBILITY_RANGE_FACTOR = 2.0;

float quasarLayerScale(int layer, float effectDistance, float effectRadius) {
    return max(effectDistance + QUASAR_VISIBILITY_RANGE_FACTOR * effectRadius, 1.0);
}

float quasarEncodeLayerDepth(int layer, float entryDistance, float effectDistance,
    float effectRadius) {
    float scale = quasarLayerScale(layer, effectDistance, effectRadius);
    float entry = max(entryDistance, 0.0);
    return clamp(log2(1.0 + entry) / log2(1.0 + scale), 0.0, 1.0);
}

float quasarDecodeLayerDepth(int layer, float encoded, float effectDistance,
    float effectRadius) {
    float scale = quasarLayerScale(layer, effectDistance, effectRadius);
    return max(exp2(clamp(encoded, 0.0, 1.0) * log2(1.0 + scale)) - 1.0, 0.0);
}

// Pre-translucent opaque depth only. This is the full gate for EMISSION, EDGE,
// FILL, and SHADOW; a translucent foreground attenuates them instead of hiding
// them.
bool quasarLayerVisible(int layer, float entryDistance, float sceneDistance,
    float depthBias) {
    return sceneDistance >= max(entryDistance, 0.0) - max(depthBias, 0.0);
}

// Opaque depth plus the optional post-translucent depth and color delta. Only
// LENS is hard-gated by a foreground translucent layer.
bool quasarLayerVisibleThroughForeground(int layer, float entryDistance,
    float sceneDistance, float foregroundDistance, bool foregroundValid,
    bool foregroundDelta, float depthBias) {
    if (!quasarLayerVisible(layer, entryDistance, sceneDistance, depthBias)) {
        return false;
    }
    if (layer != QUASAR_LAYER_LENS) {
        return true;
    }
    if (foregroundDelta) {
        return false;
    }
    if (!foregroundValid) {
        return true;
    }
    return foregroundDistance >= max(entryDistance, 0.0) - max(depthBias, 0.0);
}

// Depth-only half of the foreground test. Callers that sample a color delta
// from textures can check this first and skip the extra samples when depth
// already proves a layer is in front.
bool quasarForegroundLayerDepth(float foregroundDistance, bool foregroundValid,
    float entryDistance, float tolerance) {
    return foregroundValid
        && foregroundDistance < entryDistance - max(tolerance, 0.0);
}

// Detects a translucent layer at a pixel. Post-translucent depth is preferred;
// the color delta catches glass or water whose depth write was skipped by the
// pipeline, a shaderpack, or a batching mod.
bool quasarForegroundLayer(float foregroundDistance, bool foregroundValid,
    float entryDistance, float tolerance, bool colorDelta) {
    return quasarForegroundLayerDepth(foregroundDistance, foregroundValid,
        entryDistance, tolerance) || colorDelta;
}

// Attenuates the effect's own emission behind a foreground translucent layer.
// The background is never tinted: the player already sees it through the glass.
vec3 quasarForegroundTint(bool foreground, vec3 seen, vec3 opaque, bool opaqueValid) {
    if (!foreground) {
        return vec3(1.0);
    }
    if (!opaqueValid) {
        return vec3(0.80);
    }
    vec3 ratio = clamp(seen / max(opaque, vec3(0.02)), vec3(0.0), vec3(1.0));
    // A detected layer always attenuates a little; bright stained glass can
    // return 1.0 per channel and would otherwise never darken the effect.
    return ratio * 0.80;
}

#endif
