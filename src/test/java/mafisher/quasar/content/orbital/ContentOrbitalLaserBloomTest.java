package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.screen.BloomSettings;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserBloomTest {
    @Test
    void dayBloomIsSofterThanNightBloom() {
        BloomSettings night = profile(0.0F, EffectQualityTier.HIGH, 0);
        BloomSettings day = profile(1.0F, EffectQualityTier.HIGH, 0);

        assertTrue(day.threshold() <= night.threshold());
        assertTrue(day.intensity() <= night.intensity());
        assertTrue(day.radiusPixels() <= night.radiusPixels());
    }

    @Test
    void lowQualityAndHighLodReduceBloom() {
        BloomSettings full = profile(0.0F, EffectQualityTier.HIGH, 0);
        BloomSettings low = profile(0.0F, EffectQualityTier.LOW, 0);
        BloomSettings distant = profile(0.0F, EffectQualityTier.HIGH, 3);

        assertTrue(low.intensity() <= full.intensity());
        assertTrue(low.radiusPixels() <= full.radiusPixels());
        assertTrue(distant.intensity() <= full.intensity());
        assertTrue(distant.radiusPixels() <= full.radiusPixels());
    }

    @Test
    void profileStaysInsideTheBloomContract() {
        for (float daylight : new float[] {0.0F, 0.5F, 1.0F, Float.NaN}) {
            for (EffectQualityTier quality : EffectQualityTier.values()) {
                for (int lod = 0; lod <= 6; lod++) {
                    BloomSettings profile = profile(daylight, quality, lod);
                    assertTrue(profile.threshold() >= 0.0F
                        && profile.threshold() <= BloomSettings.MAX_THRESHOLD);
                    assertTrue(profile.intensity() >= 0.0F
                        && profile.intensity() <= BloomSettings.MAX_INTENSITY);
                    assertTrue(profile.radiusPixels() >= 0.0F
                        && profile.radiusPixels() <= BloomSettings.MAX_RADIUS_PIXELS);
                }
            }
        }
        assertEquals(profile(0.0F, EffectQualityTier.LOW, 0),
            profile(Float.NaN, EffectQualityTier.LOW, 0));
    }

    private static BloomSettings profile(float daylight, EffectQualityTier quality, int lod) {
        return ContentOrbitalLaserBloom.profile(daylight, quality, lod);
    }
}
