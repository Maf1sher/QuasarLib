#ifndef QUASAR_BLACK_HOLE_OPTICS_GLSL
#define QUASAR_BLACK_HOLE_OPTICS_GLSL

// Keep the shipped shader and the headless CPU oracle on the same values. The
// oracle reads this file directly; change a constant here, not in the shader.
const float BH_B_CRITICAL = 2.59807621135;
const float BH_HORIZON_EPSILON = 1.02;
const float BH_RIPPLE_SLOPE = 0.02;

const float BH_STEP_NEAR_LIMIT = 6.0;
const float BH_STEP_NEAR_FACTOR = 0.045;
const float BH_STEP_NEAR_MIN = 0.008;
const float BH_STEP_NEAR_MAX = 0.20;
const float BH_STEP_FAR_FACTOR = 0.10;
const float BH_STEP_FAR_MIN = 0.02;
const float BH_STEP_FAR_MAX = 1.5;
const float BH_STEP_SPEED_FLOOR = 0.25;

const float BH_ESCAPE_FACTOR = 48.0;
const float BH_ESCAPE_ORIGIN_FACTOR = 1.25;

#endif
