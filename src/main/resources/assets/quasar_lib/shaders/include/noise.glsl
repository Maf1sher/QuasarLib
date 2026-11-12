#ifndef QUASAR_NOISE_GLSL
#define QUASAR_NOISE_GLSL

float quasarHash21(vec2 point, float salt) {
    point = fract(point * vec2(123.34, 456.21));
    point += dot(point, point + salt);
    return fract(point.x * point.y);
}

float quasarValueNoise(vec2 point, float salt) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    return mix(
        mix(quasarHash21(cell, salt), quasarHash21(cell + vec2(1.0, 0.0), salt), local.x),
        mix(quasarHash21(cell + vec2(0.0, 1.0), salt),
            quasarHash21(cell + vec2(1.0, 1.0), salt), local.x),
        local.y
    );
}

float quasarHash13(vec3 point) {
    point = fract(point * 0.1031);
    point += dot(point, point.yzx + 33.33);
    return fract((point.x + point.y) * point.z);
}

float quasarValueNoise3(vec3 point) {
    vec3 cell = floor(point);
    vec3 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    float n000 = quasarHash13(cell);
    float n100 = quasarHash13(cell + vec3(1.0, 0.0, 0.0));
    float n010 = quasarHash13(cell + vec3(0.0, 1.0, 0.0));
    float n110 = quasarHash13(cell + vec3(1.0, 1.0, 0.0));
    float n001 = quasarHash13(cell + vec3(0.0, 0.0, 1.0));
    float n101 = quasarHash13(cell + vec3(1.0, 0.0, 1.0));
    float n011 = quasarHash13(cell + vec3(0.0, 1.0, 1.0));
    float n111 = quasarHash13(cell + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, local.x);
    float nx10 = mix(n010, n110, local.x);
    float nx01 = mix(n001, n101, local.x);
    float nx11 = mix(n011, n111, local.x);
    float nxy0 = mix(nx00, nx10, local.y);
    float nxy1 = mix(nx01, nx11, local.y);
    return mix(nxy0, nxy1, local.z);
}

float quasarFbm3(vec3 point) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        value += amplitude * quasarValueNoise3(point);
        point = point * 2.03 + vec3(1.7, 9.2, 3.4);
        amplitude *= 0.5;
    }
    return value;
}

#endif
