package mafisher.quasar.content;


import mafisher.quasar.api.client.BlackHoleScreenSettings;
import mafisher.quasar.api.client.BloomSettings;

/** Content-owned daylight compensation for black-hole emission and bloom, without weakening its lensing. */
final class ContentBlackHoleLighting {
    private static final float DAY_BLOOM_THRESHOLD = 0.50F;
    private static final float DAY_BLOOM_INTENSITY_SCALE = 0.35F;
    private static final float DAY_BLOOM_RADIUS_SCALE = 0.75F;
    private static final float DAY_EMISSION_SCALE = 0.55F;

    private ContentBlackHoleLighting() {
    }

    static float emissionScale(float daylight) {
        validateDaylight(daylight);
        return lerp(1.0F, DAY_EMISSION_SCALE, daylight);
    }

    static BlackHoleScreenSettings screenSettings(BlackHoleScreenSettings base, float daylight) {
        validateDaylight(daylight);
        BloomSettings baseBloom = base.bloomSettings();
        BloomSettings adaptedBloom = new BloomSettings(
            lerp(baseBloom.threshold(), DAY_BLOOM_THRESHOLD, daylight),
            baseBloom.intensity() * lerp(1.0F, DAY_BLOOM_INTENSITY_SCALE, daylight),
            baseBloom.radiusPixels() * lerp(1.0F, DAY_BLOOM_RADIUS_SCALE, daylight)
        );
        return new BlackHoleScreenSettings(base.lensStrength(), base.lensRadiusScale(),
            base.maxDistortionPixels(), adaptedBloom);
    }

    private static float lerp(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    private static void validateDaylight(float daylight) {
        if (!Float.isFinite(daylight) || daylight < 0.0F || daylight > 1.0F) {
            throw new IllegalArgumentException("Daylight must be finite and in [0, 1]");
        }
    }
}
