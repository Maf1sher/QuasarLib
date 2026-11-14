package mafisher.quasar.content.blackhole;

import java.util.Map;
import mafisher.quasar.shader.GlslConstants;

/**
 * Test-only view of the shipped black-hole optics. The values are read from the
 * same include the fragment shader imports, so the CPU oracle cannot drift from
 * the shader constants.
 */
final class BlackHoleOptics {
    private static final String INCLUDE =
        "quasar_content:shaders/include/black_hole_optics.glsl";
    private static final String MEDIUM_INCLUDE =
        "quasar_lib:shaders/include/medium_optics.glsl";
    private static final Map<String, Double> VALUES = GlslConstants.load(INCLUDE);
    private static final Map<String, Double> MEDIUM = GlslConstants.load(MEDIUM_INCLUDE);

    static final double B_CRITICAL = value("BH_B_CRITICAL");
    static final double HORIZON_EPSILON = value("BH_HORIZON_EPSILON");
    static final double WATER_IOR = mediumValue("QUASAR_MEDIUM_WATER_IOR");
    static final double RIPPLE_SLOPE = value("BH_RIPPLE_SLOPE");
    static final double FRESNEL_FLOOR = mediumValue("QUASAR_MEDIUM_FRESNEL_FLOOR");
    static final double STEP_NEAR_LIMIT = value("BH_STEP_NEAR_LIMIT");
    static final double STEP_NEAR_FACTOR = value("BH_STEP_NEAR_FACTOR");
    static final double STEP_NEAR_MIN = value("BH_STEP_NEAR_MIN");
    static final double STEP_NEAR_MAX = value("BH_STEP_NEAR_MAX");
    static final double STEP_FAR_FACTOR = value("BH_STEP_FAR_FACTOR");
    static final double STEP_FAR_MIN = value("BH_STEP_FAR_MIN");
    static final double STEP_FAR_MAX = value("BH_STEP_FAR_MAX");
    static final double STEP_SPEED_FLOOR = value("BH_STEP_SPEED_FLOOR");
    static final double ESCAPE_FACTOR = value("BH_ESCAPE_FACTOR");
    static final double ESCAPE_ORIGIN_FACTOR = value("BH_ESCAPE_ORIGIN_FACTOR");

    private BlackHoleOptics() {
    }

    private static double value(String name) {
        return GlslConstants.require(VALUES, name);
    }

    private static double mediumValue(String name) {
        return GlslConstants.require(MEDIUM, name);
    }
}
