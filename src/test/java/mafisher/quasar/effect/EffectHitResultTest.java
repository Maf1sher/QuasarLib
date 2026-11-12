package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.effect.EffectHitResult;
import org.junit.jupiter.api.Test;

class EffectHitResultTest {
    @Test
    void storesConsistentCounts() {
        EffectHitResult result = new EffectHitResult(3, 2);

        assertEquals(3, result.examined());
        assertEquals(2, result.damaged());
    }

    @Test
    void rejectsNegativeOrInconsistentCounts() {
        assertThrows(IllegalArgumentException.class, () -> new EffectHitResult(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectHitResult(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new EffectHitResult(1, 2));
    }
}
