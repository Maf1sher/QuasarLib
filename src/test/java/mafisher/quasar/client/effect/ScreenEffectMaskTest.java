package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScreenEffectMaskTest {
    @Test
    void acceptsFiniteUnitIntervalInputs() {
        assertDoesNotThrow(() -> new ScreenEffectMask(0.0F, 0.5F, 1.0F, 0.45F));
    }

    @Test
    void rejectsOutOfRangeAndNonFiniteInputs() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectMask(-0.01F, 0.5F, 1.0F, 0.45F));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectMask(0.0F, 0.5F, 1.0F, 1.01F));
        assertThrows(IllegalArgumentException.class,
            () -> new ScreenEffectMask(0.0F, Float.NaN, 1.0F, 0.45F));
    }
}
