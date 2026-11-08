package mafisher.quasar.content;

/** Tuning for the content-owned black-hole raymarch and screen mask. */
record ContentBlackHoleSettings(
    float diskInnerFactor,
    float diskOuterFactor,
    float diskThicknessFactor,
    float lensStrength,
    float lensGravity,
    float diskBrightness,
    float ringBrightness,
    float turbulence,
    float charge,
    float maskRadiusFactor,
    float bloomStrength
) {
    static final ContentBlackHoleSettings DEFAULT = new ContentBlackHoleSettings(
        3.0F,
        9.0F,
        0.32F,
        1.8F,
        0.5F,
        1.35F,
        4.0F,
        1.0F,
        0.55F,
        15.0F,
        0.65F
    );

    ContentBlackHoleSettings {
        if (!Float.isFinite(diskInnerFactor) || diskInnerFactor <= 0.0F
            || !Float.isFinite(diskOuterFactor) || diskOuterFactor <= diskInnerFactor
            || !Float.isFinite(diskThicknessFactor) || diskThicknessFactor < 0.0F
            || diskThicknessFactor > 0.5F
            || !Float.isFinite(lensStrength) || lensStrength < 0.0F || lensStrength > 4.0F
            || !Float.isFinite(lensGravity) || lensGravity < 0.05F || lensGravity > 1.0F
            || !Float.isFinite(diskBrightness) || diskBrightness < 0.0F || diskBrightness > 8.0F
            || !Float.isFinite(ringBrightness) || ringBrightness < 0.0F || ringBrightness > 16.0F
            || !Float.isFinite(turbulence) || turbulence < 0.0F || turbulence > 3.0F
            || !Float.isFinite(charge) || charge < 0.0F || charge > 1.0F
            || !Float.isFinite(maskRadiusFactor) || maskRadiusFactor <= 0.0F
            || maskRadiusFactor > 32.0F
            || !Float.isFinite(bloomStrength) || bloomStrength < 0.0F || bloomStrength > 2.0F) {
            throw new IllegalArgumentException("Invalid content black-hole settings");
        }
    }
}
