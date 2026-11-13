package mafisher.quasar.content.blackhole;

/** Content-owned daylight compensation for black-hole mesh emission. */
final class ContentBlackHoleLighting {
    private static final float DAY_EMISSION_SCALE = 0.55F;

    private ContentBlackHoleLighting() {
    }

    static float emissionScale(float daylight) {
        float factor = clampDaylight(daylight);
        return lerp(1.0F, DAY_EMISSION_SCALE, factor);
    }

    private static float lerp(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    private static float clampDaylight(float daylight) {
        if (!Float.isFinite(daylight)) {
            throw new IllegalArgumentException("Daylight must be finite");
        }
        return Math.max(0.0F, Math.min(1.0F, daylight));
    }
}
