package mafisher.quasar.api.client;

/** Per-effect mask-composition inputs; color is diagnostic-only and strength applies to every operation. */
public record ScreenEffectMask(float red, float green, float blue, float strength) {
    public ScreenEffectMask {
        if (!isUnitInterval(red) || !isUnitInterval(green) || !isUnitInterval(blue)
            || !isUnitInterval(strength)) {
            throw new IllegalArgumentException("Screen effect mask values must be finite and in [0, 1]");
        }
    }

    private static boolean isUnitInterval(float value) {
        return Float.isFinite(value) && value >= 0.0F && value <= 1.0F;
    }
}
