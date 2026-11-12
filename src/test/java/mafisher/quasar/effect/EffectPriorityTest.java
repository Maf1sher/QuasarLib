package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectPriority;
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

    @Test
    void parsesPersistedNamesWithContextAndAFallback() {
        assertEquals(EffectPriority.HIGH, EffectPriority.byName("HIGH", EffectPriority.NORMAL, "test"));
        assertEquals(EffectPriority.NORMAL, EffectPriority.byName("unknown", EffectPriority.NORMAL, "test"));
        assertEquals(EffectPriority.NORMAL, EffectPriority.byName("", EffectPriority.NORMAL, "test"));
        assertEquals(EffectPriority.NORMAL, EffectPriority.byName(null, EffectPriority.NORMAL, "test"));
    }
}
