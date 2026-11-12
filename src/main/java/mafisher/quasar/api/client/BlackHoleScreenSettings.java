package mafisher.quasar.api.client;

import java.util.Objects;

/** Bounded compositor inputs for the cinematic black-hole effect. */
public record BlackHoleScreenSettings(
    float lensStrength,
    float lensRadiusScale,
    float maxDistortionPixels,
    BloomSettings bloomSettings
) {
    public static final float MAX_LENS_STRENGTH = 1.0F;
    public static final float MAX_LENS_RADIUS_SCALE = 3.0F;
    public static final float MAX_DISTORTION_PIXELS = 48.0F;
    public static final BlackHoleScreenSettings CINEMATIC_DEFAULT = new BlackHoleScreenSettings(
        0.82F, 1.45F, 24.0F, new BloomSettings(0.32F, 1.8F, 18.0F)
    );

    public BlackHoleScreenSettings {
        if (!Float.isFinite(lensStrength) || lensStrength < 0.0F || lensStrength > MAX_LENS_STRENGTH
            || !Float.isFinite(lensRadiusScale) || lensRadiusScale <= 0.0F
            || lensRadiusScale > MAX_LENS_RADIUS_SCALE
            || !Float.isFinite(maxDistortionPixels) || maxDistortionPixels < 0.0F
            || maxDistortionPixels > MAX_DISTORTION_PIXELS) {
            throw new IllegalArgumentException("Invalid black-hole screen settings");
        }
        Objects.requireNonNull(bloomSettings, "bloomSettings");
    }
}
