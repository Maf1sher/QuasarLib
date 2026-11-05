package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EffectAudienceTest {
    @Test
    void validatesNearbyRadiusAndExposesDimensionWidePolicy() {
        assertEquals(128.0, ((EffectAudience.Nearby)EffectAudience.nearby(128.0)).radius());
        assertThrows(IllegalArgumentException.class, () -> EffectAudience.nearby(-1.0));
        assertThrows(IllegalArgumentException.class, () -> EffectAudience.nearby(512.1));
        assertThrows(IllegalArgumentException.class, () -> EffectAudience.nearby(Double.NaN));
    }
}
