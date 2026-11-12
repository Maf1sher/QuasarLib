package mafisher.quasar.content.blackhole;

/** Content-owned tuning for the black-hole attraction, destruction, and debris layers. */
record ContentBlackHoleGameplaySettings(
    float coreRadiusFactor,
    float attractionRadiusFactor,
    double inwardStrength,
    double tangentialStrength,
    double falloff,
    int maxForceTargets,
    int tearRaysPerTick,
    int maxConsumeTargets,
    int tearDebrisBurstsPerTick,
    int tearDebrisPieces,
    double debrisFallSpeed,
    int debrisMaxLifetimeTicks,
    float debrisPieceScale,
    boolean affectsCreativePlayers,
    float craterRadiusFactor,
    boolean craterJournal
) {
    static final ContentBlackHoleGameplaySettings DEFAULT = new ContentBlackHoleGameplaySettings(
        1.0F,
        18.0F,
        0.22,
        0.10,
        1.25,
        128,
        64,
        64,
        6,
        8,
        1.0,
        120,
        8.0F,
        true,
        12.0F,
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
            || tearRaysPerTick < 0 || tearRaysPerTick > 512
            || maxConsumeTargets < 1 || maxConsumeTargets > 4_096
            || tearDebrisBurstsPerTick < 0 || tearDebrisBurstsPerTick > 16
            || tearDebrisPieces < 1 || tearDebrisPieces > 64
            || !Double.isFinite(debrisFallSpeed) || debrisFallSpeed < 0.1
            || debrisFallSpeed > 4.0
            || debrisMaxLifetimeTicks < 10 || debrisMaxLifetimeTicks > 200
            || !Float.isFinite(debrisPieceScale) || debrisPieceScale < 1.0F
            || debrisPieceScale > 8.0F
            || !Float.isFinite(craterRadiusFactor) || craterRadiusFactor < 1.0F
            || craterRadiusFactor > 32.0F) {
            throw new IllegalArgumentException("Invalid content black-hole gameplay settings");
        }
    }

    double coreRadius(float scale) {
        return Math.max(1.0, scale * (double)this.coreRadiusFactor);
    }

    double attractionRadius(float scale) {
        return Math.min(1_024.0, Math.max(coreRadius(scale), scale * (double)this.attractionRadiusFactor));
    }

    /**
     * The requested destruction always follows the authored horizon. The destruction plan fits
     * the sphere into the remaining timeline and engine budgets by shrinking the radius, so there
     * is no fixed gameplay cap here.
     */
    double craterRadius(float scale) {
        return Math.max(4.0, scale * (double)this.craterRadiusFactor);
    }
}
