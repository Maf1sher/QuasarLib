package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BlackHoleLightingTest {
    @Test
    void keepsNightSettingsAndReducesEmissionDuringDay() {
        BlackHoleScreenSettings base = BlackHoleScreenSettings.CINEMATIC_DEFAULT;

        assertEquals(base, BlackHoleLighting.screenSettings(base, 0.0F));
        assertEquals(1.0F, BlackHoleLighting.emissionScale(0.0F));
        assertEquals(0.55F, BlackHoleLighting.emissionScale(1.0F));
    }

    @Test
    void daylightReducesBloomButDoesNotChangeLensing() {
        BlackHoleScreenSettings base = BlackHoleScreenSettings.CINEMATIC_DEFAULT;
        BlackHoleScreenSettings daylight = BlackHoleLighting.screenSettings(base, 1.0F);

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
            () -> BlackHoleLighting.emissionScale(Float.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> BlackHoleLighting.screenSettings(BlackHoleScreenSettings.CINEMATIC_DEFAULT, 1.01F));
    }
}
