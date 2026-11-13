package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContentBlackHoleLightingTest {
    @Test
    void keepsNightEmissionAndReducesItDuringDay() {
        assertEquals(1.0F, ContentBlackHoleLighting.emissionScale(0.0F));
        assertEquals(0.55F, ContentBlackHoleLighting.emissionScale(1.0F));
    }

    @Test
    void clampsOutOfRangeDaylightAndRejectsNonFiniteValues() {
        assertEquals(ContentBlackHoleLighting.emissionScale(1.0F),
            ContentBlackHoleLighting.emissionScale(1.01F));
        assertEquals(ContentBlackHoleLighting.emissionScale(0.0F),
            ContentBlackHoleLighting.emissionScale(-1.0F));
        assertThrows(IllegalArgumentException.class,
            () -> ContentBlackHoleLighting.emissionScale(Float.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> ContentBlackHoleLighting.emissionScale(Float.POSITIVE_INFINITY));
    }
}
