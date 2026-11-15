package mafisher.quasar.content.orbital;

import mafisher.quasar.api.effect.budget.EffectQualityTier;

/** Pure beam geometry helpers shared by the renderer, operation, and headless tests. */
final class ContentOrbitalLaserBeamGeometry {
    private static final int MIN_MARCH_STEPS = 8;
    private static final int MAX_MARCH_STEPS = 56;

    private ContentOrbitalLaserBeamGeometry() {
    }

    /** Returns the number of volume samples for one distance-LOD and quality level. */
    static int marchSteps(int lodLevel, EffectQualityTier quality) {
        int base = switch (quality) {
            case LOW -> 14;
            case MEDIUM -> 22;
            case HIGH -> 36;
            case ULTRA -> 48;
        };
        int lodPenalty = Math.max(0, lodLevel) * 4;
        return clamp(base - lodPenalty, MIN_MARCH_STEPS, MAX_MARCH_STEPS);
    }

    /** Returns the beam head height above the base for a normalized tip. */
    static float tipHeight(float tipFraction, float topHeight) {
        if (!Float.isFinite(tipFraction) || !Float.isFinite(topHeight) || topHeight <= 0.0F) {
            return 0.0F;
        }
        return clamp01(tipFraction) * topHeight;
    }

    /** Returns the maximum world-space radial footprint of the beam and impact effects. */
    static double maximumFootprint(ContentOrbitalLaserBeamSettings settings, float scale) {
        if (settings == null || !Float.isFinite(scale) || scale <= 0.0F) {
            return 0.0;
        }
        return settings.maximumFootprintFactor() * (double)scale;
    }

    /** Returns the conservative spatial radius for culling and LOD. */
    static double spatialRadius(ContentOrbitalLaserBeamSettings settings, float scale) {
        double footprint = maximumFootprint(settings, scale);
        return settings.topHeight() + footprint + 0.01;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
