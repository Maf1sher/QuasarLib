package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EffectSeedsTest {
    @Test
    void repeatsForTheSameSeedAndTick() {
        assertEquals(EffectSeeds.forTick(1234L, 0), EffectSeeds.forTick(1234L, 0));
        assertEquals(EffectSeeds.forTick(1234L, 17), EffectSeeds.forTick(1234L, 17));
    }

    @Test
    void separatesSeedsAcrossTicksAndSessions() {
        assertNotEquals(EffectSeeds.forTick(1234L, 0), EffectSeeds.forTick(1234L, 1));
        assertNotEquals(EffectSeeds.forTick(1234L, 0), EffectSeeds.forTick(1235L, 0));
    }

    @Test
    void rejectsNegativeAges() {
        assertThrows(IllegalArgumentException.class, () -> EffectSeeds.forTick(0L, -1));
    }
}
