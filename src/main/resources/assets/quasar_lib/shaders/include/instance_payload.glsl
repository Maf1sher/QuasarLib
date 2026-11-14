#ifndef QUASAR_INSTANCE_PAYLOAD_GLSL
#define QUASAR_INSTANCE_PAYLOAD_GLSL

// Six RGBA32F texels per instance: four camera-relative model-matrix
// columns, one RGBA color, and one effect-data vector.
const int QUASAR_INSTANCE_TEXELS = 6;

mat4 quasarInstanceMatrix(samplerBuffer instances, int instance) {
    int base = instance * QUASAR_INSTANCE_TEXELS;
    return mat4(
        texelFetch(instances, base),
        texelFetch(instances, base + 1),
        texelFetch(instances, base + 2),
        texelFetch(instances, base + 3)
    );
}

vec4 quasarInstanceColor(samplerBuffer instances, int instance) {
    return texelFetch(instances, instance * QUASAR_INSTANCE_TEXELS + 4);
}

vec4 quasarInstanceData(samplerBuffer instances, int instance) {
    return texelFetch(instances, instance * QUASAR_INSTANCE_TEXELS + 5);
}

#endif
