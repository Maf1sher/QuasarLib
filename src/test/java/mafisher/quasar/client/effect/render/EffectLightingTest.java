package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectLightingTest {
    @Test
    void fullSkyWithoutDarkeningIsFullDaylight() {
        assertEquals(1.0F, EffectLighting.daylight(1.0F, 0.0F, 0.0F), 1.0E-6F);
    }

    @Test
    void noSkyOrBlockLightIsNight() {
        assertEquals(0.0F, EffectLighting.daylight(0.0F, 0.0F, 0.0F), 1.0E-6F);
        assertEquals(0.0F, EffectLighting.daylight(1.0F, 1.0F, 0.0F), 1.0E-6F);
    }

    @Test
    void blockLightLiftsTheFloorWithoutBecomingFullDaylight() {
        float blockOnly = EffectLighting.daylight(0.0F, 0.0F, 1.0F);

        assertTrue(blockOnly > 0.0F);
        assertTrue(blockOnly < 1.0F);
    }

    @Test
    void clampsInputsAndRejectsNonFiniteValues() {
        assertEquals(EffectLighting.daylight(1.0F, 0.0F, 0.0F),
            EffectLighting.daylight(2.0F, -1.0F, 0.0F), 1.0E-6F);
        assertThrows(IllegalArgumentException.class,
            () -> EffectLighting.daylight(Float.NaN, 0.0F, 0.0F));
    }
}
