package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.client.BlackHoleScreenSettings;
import org.junit.jupiter.api.Test;

class ContentBlackHoleLightingTest {
    @Test
    void keepsNightSettingsAndReducesEmissionDuringDay() {
        BlackHoleScreenSettings base = BlackHoleScreenSettings.CINEMATIC_DEFAULT;

        assertEquals(base, ContentBlackHoleLighting.screenSettings(base, 0.0F));
        assertEquals(1.0F, ContentBlackHoleLighting.emissionScale(0.0F));
        assertEquals(0.55F, ContentBlackHoleLighting.emissionScale(1.0F));
    }

    @Test
    void daylightReducesBloomButDoesNotChangeLensing() {
        BlackHoleScreenSettings base = BlackHoleScreenSettings.CINEMATIC_DEFAULT;
        BlackHoleScreenSettings daylight = ContentBlackHoleLighting.screenSettings(base, 1.0F);

        assertEquals(base.lensStrength(), daylight.lensStrength());
        assertEquals(base.lensRadiusScale(), daylight.lensRadiusScale());
        assertEquals(base.maxDistortionPixels(), daylight.maxDistortionPixels());
        assertEquals(0.50F, daylight.bloomSettings().threshold(), 0.000001F);
        assertEquals(base.bloomSettings().intensity() * 0.35F,
            daylight.bloomSettings().intensity(), 0.000001F);
        assertEquals(base.bloomSettings().radiusPixels() * 0.75F,
            daylight.bloomSettings().radiusPixels(), 0.000001F);
    }

    @Test
    void rejectsInvalidDaylightValues() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentBlackHoleLighting.emissionScale(Float.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> ContentBlackHoleLighting.screenSettings(BlackHoleScreenSettings.CINEMATIC_DEFAULT, 1.01F));
    }
}
