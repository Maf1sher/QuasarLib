package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectPriorityTest {
    @Test
    void ordersPrioritiesByWeight() {
        assertTrue(EffectPriority.CRITICAL.weight() > EffectPriority.HIGH.weight());
        assertTrue(EffectPriority.HIGH.isAtLeast(EffectPriority.NORMAL));
        assertFalse(EffectPriority.BACKGROUND.isAtLeast(EffectPriority.LOW));
        assertEquals(0, EffectPriority.BACKGROUND.weight());
    }

    @Test
    void parsesPersistedNamesWithAFallback() {
        assertEquals(EffectPriority.HIGH, EffectPriority.byName("HIGH", EffectPriority.LOW));
        assertEquals(EffectPriority.LOW, EffectPriority.byName("unknown", EffectPriority.LOW));
        assertEquals(EffectPriority.LOW, EffectPriority.byName("", EffectPriority.LOW));
        assertEquals(EffectPriority.LOW, EffectPriority.byName(null, EffectPriority.LOW));
    }
}
