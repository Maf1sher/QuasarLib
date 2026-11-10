package mafisher.quasar.content;

/** Content-owned tuning for the black-hole attraction, destruction, and debris layers. */
record ContentBlackHoleGameplaySettings(
    float coreRadiusFactor,
    float attractionRadiusFactor,
    double inwardStrength,
    double tangentialStrength,
    double falloff,
    int maxForceTargets,
    float tearStartFactor,
    int tearRaysPerTick,
    int coreBlocksPerTick,
    int maxConsumeTargets,
    int debrisPiecesPerBurst,
    int debrisBurstsPerTick,
    int debrisLifetimeTicks,
    boolean affectsCreativePlayers,
    float craterRadiusFactor,
    float craterDepthFactor,
    float surfaceScrapeRadiusFactor,
    int surfaceScrapeDepth,
    double craterImmediateSyncRadius,
    boolean craterJournal
) {
    static final ContentBlackHoleGameplaySettings DEFAULT = new ContentBlackHoleGameplaySettings(
        1.0F,
        18.0F,
        0.22,
        0.10,
        1.25,
        128,
        1.35F,
        64,
        4_096,
        64,
        64,
        8,
        110,
        true,
        6.0F,
        10.0F,
        1.5F,
        3,
        96.0,
        true
    );

    ContentBlackHoleGameplaySettings {
        if (!Float.isFinite(coreRadiusFactor) || coreRadiusFactor <= 0.0F || coreRadiusFactor > 4.0F
            || !Float.isFinite(attractionRadiusFactor) || attractionRadiusFactor < 1.0F
            || attractionRadiusFactor > 32.0F
            || !Double.isFinite(inwardStrength) || inwardStrength < 0.0 || inwardStrength > 8.0
            || !Double.isFinite(tangentialStrength) || tangentialStrength < 0.0
            || tangentialStrength > 8.0
            || !Double.isFinite(falloff) || falloff < 0.0 || falloff > 16.0
            || maxForceTargets < 1 || maxForceTargets > 4_096
            || !Float.isFinite(tearStartFactor) || tearStartFactor < 1.0F || tearStartFactor > 8.0F
            || tearRaysPerTick < 0 || tearRaysPerTick > 512
            || coreBlocksPerTick < 1 || coreBlocksPerTick > 16_384
            || maxConsumeTargets < 1 || maxConsumeTargets > 4_096
            || debrisPiecesPerBurst < 1 || debrisPiecesPerBurst > 64
            || debrisBurstsPerTick < 1 || debrisBurstsPerTick > 32
            || debrisLifetimeTicks < 1 || debrisLifetimeTicks > 200
            || !Float.isFinite(craterRadiusFactor) || craterRadiusFactor < 1.0F
            || craterRadiusFactor > 32.0F
            || !Float.isFinite(craterDepthFactor) || craterDepthFactor < 1.0F
            || craterDepthFactor > 32.0F
            || !Float.isFinite(surfaceScrapeRadiusFactor) || surfaceScrapeRadiusFactor < 1.0F
            || surfaceScrapeRadiusFactor > 4.0F
            || surfaceScrapeDepth < 0 || surfaceScrapeDepth > 32
            || !Double.isFinite(craterImmediateSyncRadius)
            || craterImmediateSyncRadius < 0.0 || craterImmediateSyncRadius > 512.0) {
            throw new IllegalArgumentException("Invalid content black-hole gameplay settings");
        }
    }

    double coreRadius(float scale) {
        return Math.max(1.0, scale * (double)this.coreRadiusFactor);
    }

    double attractionRadius(float scale) {
        return Math.min(1_024.0, Math.max(coreRadius(scale), scale * (double)this.attractionRadiusFactor));
    }

    double craterRadius(float scale) {
        // Keep very large author scales from turning one effect into an unbounded world edit.
        return Math.min(96.0, Math.max(4.0, scale * (double)this.craterRadiusFactor));
    }

    double craterDepth(float scale) {
        return Math.min(128.0, Math.max(4.0, scale * (double)this.craterDepthFactor));
    }
}
