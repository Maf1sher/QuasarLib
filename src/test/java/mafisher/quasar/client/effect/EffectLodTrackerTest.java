package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.EffectLod;
import mafisher.quasar.api.client.EffectLodProfile;
import org.junit.jupiter.api.Test;

class EffectLodTrackerTest {
    private static final EffectLodProfile ONE_BAND = EffectLodProfile.of(1.0);

    @Test
    void initialSelectionUsesTheTargetLevelWithoutTransition() {
        EffectLod lod = update(new EffectLodTracker.State(), ONE_BAND, 1.5, 0.0);

        assertEquals(1, lod.level());
        assertEquals(1, lod.previousLevel());
        assertEquals(1.0, lod.blend());
        assertFalse(lod.isTransitioning());
    }

    @Test
    void hysteresisKeepsTheLevelInsideTheBand() {
        EffectLodTracker.State state = new EffectLodTracker.State();
        update(state, ONE_BAND, 0.5, 0.0);

        EffectLod justBelowUpperBand = update(state, ONE_BAND, 1.1, 1.0);
        assertEquals(0, justBelowUpperBand.level());
        assertFalse(justBelowUpperBand.isTransitioning());

        EffectLod upgraded = update(state, ONE_BAND, 1.2, 2.0);
        assertEquals(1, upgraded.level());
        assertEquals(0, upgraded.previousLevel());
        assertEquals(0.0, upgraded.blend());
        assertTrue(upgraded.isTransitioning());

        EffectLod halfway = update(state, ONE_BAND, 1.2, 4.5);
        assertEquals(1, halfway.level());
        assertEquals(0, halfway.previousLevel());
        assertEquals(0.5, halfway.blend());

        EffectLod completed = update(state, ONE_BAND, 1.2, 7.0);
        assertEquals(1, completed.level());
        assertEquals(1, completed.previousLevel());
        assertEquals(1.0, completed.blend());
        assertFalse(completed.isTransitioning());
    }

    @Test
    void downgradeNeedsToCrossTheLowerHysteresisBand() {
        EffectLodTracker.State state = new EffectLodTracker.State();
        update(state, ONE_BAND, 2.0, 0.0);

        EffectLod insideBand = update(state, ONE_BAND, 0.9, 1.0);
        assertEquals(1, insideBand.level());

        EffectLod downgraded = update(state, ONE_BAND, 0.8, 2.0);
        assertEquals(0, downgraded.level());
        assertEquals(1, downgraded.previousLevel());
        assertEquals(0.0, downgraded.blend());
    }

    @Test
    void zeroThresholdCanReturnToFullDetail() {
        EffectLodTracker.State state = new EffectLodTracker.State();
        EffectLodProfile profile = EffectLodProfile.of(0.0);
        update(state, profile, 0.0, 0.0);

        assertEquals(1, update(state, profile, 0.1, 1.0).level());
        assertEquals(0, update(state, profile, 0.0, 2.0).level());
    }

    @Test
    void clampsStateWhenTheProfileShrinks() {
        EffectLodTracker.State state = new EffectLodTracker.State();
        update(state, EffectLodProfile.of(1.0, 2.0, 3.0), 5.0, 0.0);

        EffectLod lod = update(state, EffectLodProfile.of(1.0), 5.0, 1.0);

        assertEquals(1, lod.level());
        assertEquals(1, lod.previousLevel());
        assertEquals(1.0, lod.blend());
    }

    @Test
    void rejectsInvalidSampleValues() {
        EffectLodTracker.State state = new EffectLodTracker.State();

        assertThrows(IllegalArgumentException.class,
            () -> update(state, ONE_BAND, Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> update(state, ONE_BAND, -1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> update(state, ONE_BAND, 0.0, Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> EffectLodTracker.update(state, ONE_BAND, 0.0, 0.0, Double.NaN));
    }

    private static EffectLod update(EffectLodTracker.State state, EffectLodProfile profile, double gap,
        double renderGameTime) {
        return EffectLodTracker.update(state, profile, 64.0, gap, renderGameTime);
    }
}
