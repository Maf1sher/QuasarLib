package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScreenEffectInputsTest {
    @Test
    void unionsOptionalCaptures() {
        assertEquals(ScreenEffectInputs.NONE, ScreenEffectInputs.NONE.union(ScreenEffectInputs.NONE));
        assertEquals(new ScreenEffectInputs(true, false),
            new ScreenEffectInputs(true, false).union(ScreenEffectInputs.NONE));
        assertEquals(ScreenEffectInputs.ALL,
            new ScreenEffectInputs(true, false).union(new ScreenEffectInputs(false, true)));
    }

    @Test
    void keepsCanonicalConstants() {
        assertSame(ScreenEffectInputs.ALL, ScreenEffectInputs.NONE.union(ScreenEffectInputs.ALL));
        assertSame(ScreenEffectInputs.ALL, ScreenEffectInputs.ALL.union(ScreenEffectInputs.NONE));
        assertSame(ScreenEffectInputs.NONE, ScreenEffectInputs.NONE.union(ScreenEffectInputs.NONE));
    }

    @Test
    void rejectsANullUnion() {
        assertThrows(NullPointerException.class,
            () -> ScreenEffectInputs.NONE.union(null));
    }
}
