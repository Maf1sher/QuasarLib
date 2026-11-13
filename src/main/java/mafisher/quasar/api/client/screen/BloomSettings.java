package mafisher.quasar.api.client.screen;

/** Bounded parameters for a mask-scoped scene bloom. */
public record BloomSettings(float threshold, float intensity, float radiusPixels) {
    public static final float MAX_THRESHOLD = 1.0F;
    public static final float MAX_INTENSITY = 4.0F;
    public static final float MAX_RADIUS_PIXELS = 32.0F;
    public static final BloomSettings DEBUG_DEFAULT = new BloomSettings(0.35F, 1.5F, 12.0F);

    public BloomSettings {
        if (!Float.isFinite(threshold) || threshold < 0.0F || threshold > MAX_THRESHOLD
            || !Float.isFinite(intensity) || intensity < 0.0F || intensity > MAX_INTENSITY
            || !Float.isFinite(radiusPixels) || radiusPixels < 0.0F || radiusPixels > MAX_RADIUS_PIXELS) {
            throw new IllegalArgumentException("Invalid bloom settings");
        }
    }
}
