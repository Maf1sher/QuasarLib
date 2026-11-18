package mafisher.quasar.content.orbital;

import mafisher.quasar.api.effect.budget.EffectQualityTier;

/**
 * Quality and LOD selection for the orbital-laser ring platform.
 *
 * <p>The platform is a screen-space SDF, so it gets its own trace budget instead of sharing the
 * beam volume's {@code MarchSteps}. The floor keeps the outer ring, hub, barrel, and muzzle
 * silhouette in every tier; lower quality only trims secondary rings, trusses, and light detail.
 * The shader mirror lives in {@code orbital_laser_platform.glsl}.</p>
 */
final class ContentOrbitalLaserPlatformGeometry {
    /** Trace steps that always keep the platform silhouette readable. */
    static final int MIN_TRACE_STEPS = 20;
    /** Hard cap shared with {@code QUASAR_PLATFORM_MAX_STEPS}. */
    static final int MAX_TRACE_STEPS = 56;

    private static final int LOD_STEP_PENALTY = 3;
    private static final float LOD_DETAIL_PENALTY = 0.08F;
    private static final float MIN_DETAIL = 0.25F;

    private ContentOrbitalLaserPlatformGeometry() {
    }

    /** Returns the platform SDF trace budget for one distance-LOD and quality level. */
    static int traceSteps(int lodLevel, EffectQualityTier quality) {
        int base = switch (quality) {
            case LOW -> 28;
            case MEDIUM -> 36;
            case HIGH -> 44;
            case ULTRA -> 50;
        };
        int penalty = Math.max(0, lodLevel) * LOD_STEP_PENALTY;
        return clamp(base - penalty, MIN_TRACE_STEPS, MAX_TRACE_STEPS);
    }

    /**
     * Returns the secondary-detail factor for one distance-LOD and quality level.
     *
     * <p>The shader keeps the outer ring, hub, barrel, and muzzle at every value. Values below
     * the shader's inner-ring threshold drop the counter-rotating ring; values below the truss
     * threshold keep one truss pair, and the remainder scales lights and energy arcs.</p>
     */
    static float detail(int lodLevel, EffectQualityTier quality) {
        float base = switch (quality) {
            case LOW -> 0.35F;
            case MEDIUM -> 0.70F;
            case HIGH -> 0.90F;
            case ULTRA -> 1.0F;
        };
        float penalty = Math.max(0, lodLevel) * LOD_DETAIL_PENALTY;
        return clamp(base - penalty, MIN_DETAIL, 1.0F);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
