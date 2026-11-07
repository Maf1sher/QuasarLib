package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectLodTest {
    @Test
    void fullIsASteadyStateSelection() {
        assertEquals(0, EffectLod.FULL.level());
        assertEquals(0, EffectLod.FULL.previousLevel());
        assertEquals(1.0, EffectLod.FULL.blend());
        assertEquals(0.0, EffectLod.FULL.cameraDistance());
        assertEquals(0.0, EffectLod.FULL.gapInRadii());
        assertFalse(EffectLod.FULL.isTransitioning());
    }

    @Test
    void compatibilityConstructorProducesASteadyState() {
        EffectLod lod = new EffectLod(2, 64.0, 8.0);

        assertEquals(2, lod.level());
        assertEquals(2, lod.previousLevel());
        assertEquals(1.0, lod.blend());
        assertEquals(64.0, lod.cameraDistance());
        assertEquals(8.0, lod.gapInRadii());
        assertFalse(lod.isTransitioning());
    }

    @Test
    void reportsAnActiveTransition() {
        EffectLod lod = new EffectLod(2, 1, 0.5, 64.0, 8.0);

        assertTrue(lod.isTransitioning());
        assertEquals(1, lod.previousLevel());
        assertEquals(0.5, lod.blend());
    }

    @Test
    void rejectsInvalidSelections() {
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(-1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, -1, 1.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, 0, -0.1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, 0, 1.1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, 0, Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, -1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, 0.0, -1.0));
    }
}
