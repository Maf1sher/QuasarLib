#ifndef QUASAR_EFFECT_SPACE_GLSL
#define QUASAR_EFFECT_SPACE_GLSL

// Camera-relative effect frame. EffectOrigin is the effect position relative to
// the camera; EffectRight/Up/Forward are the engine's Y * X * Z basis axes.
uniform vec3 EffectOrigin;
uniform vec3 EffectRight;
uniform vec3 EffectUp;
uniform vec3 EffectForward;

vec3 quasarEffectDirectionToWorld(vec3 direction) {
    return mat3(EffectRight, EffectUp, EffectForward) * direction;
}

vec3 quasarWorldDirectionToEffect(vec3 direction) {
    return transpose(mat3(EffectRight, EffectUp, EffectForward)) * direction;
}

#endif
