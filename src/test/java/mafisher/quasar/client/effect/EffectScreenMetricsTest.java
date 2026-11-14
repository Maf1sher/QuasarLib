package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.render.EffectScreenMetrics;
import org.junit.jupiter.api.Test;

class EffectScreenMetricsTest {
    @Test
    void unknownHasNoProjectionAndZeroScore() {
        assertFalse(EffectScreenMetrics.UNKNOWN.projected());
        assertEquals(0.0, EffectScreenMetrics.UNKNOWN.coverage());
        assertEquals(0.0, EffectScreenMetrics.UNKNOWN.centreDistance());
        assertEquals(0.0, EffectScreenMetrics.UNKNOWN.score());
    }

    @Test
    void derivesScoreFromCoverageAndCentreDistance() {
        EffectScreenMetrics centre = EffectScreenMetrics.of(0.4, 0.0);
        EffectScreenMetrics edge = EffectScreenMetrics.of(0.4, 1.0);

        assertTrue(centre.projected());
        assertEquals(0.4, centre.score(), 0.000001);
        assertEquals(0.2, edge.score(), 0.000001);
    }

    @Test
    void rejectsValuesOutsideTheUnitRange() {
        assertThrows(IllegalArgumentException.class, () -> EffectScreenMetrics.of(-0.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenMetrics.of(1.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenMetrics.of(0.5, -0.1));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenMetrics.of(0.5, 1.1));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenMetrics.of(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectScreenMetrics(0.5, 0.5, 2.0, true));
    }
}
