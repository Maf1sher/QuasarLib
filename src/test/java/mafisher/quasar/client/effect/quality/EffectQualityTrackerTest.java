package mafisher.quasar.client.effect.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.junit.jupiter.api.Test;

class EffectQualityTrackerTest {
    @Test
    void initialSelectionAppliesImmediately() {
        EffectQualityTracker.State state = new EffectQualityTracker.State();

        assertEquals(EffectQualityTier.LOW,
            EffectQualityTracker.update(state, EffectQualityTier.LOW, 0.0));
    }

    @Test
    void aNewTierNeedsToRemainStableForTheHoldWindow() {
        EffectQualityTracker.State state = new EffectQualityTracker.State();
        EffectQualityTracker.update(state, EffectQualityTier.HIGH, 0.0);

        assertEquals(EffectQualityTier.HIGH,
            EffectQualityTracker.update(state, EffectQualityTier.LOW, 0.0));
        assertEquals(EffectQualityTier.HIGH,
            EffectQualityTracker.update(state, EffectQualityTier.LOW, 9.0));
        assertEquals(EffectQualityTier.LOW,
            EffectQualityTracker.update(state, EffectQualityTier.LOW, 10.0));
    }

    @Test
    void aFlippedTargetRestartsTheHoldWindow() {
        EffectQualityTracker.State state = new EffectQualityTracker.State();
        EffectQualityTracker.update(state, EffectQualityTier.HIGH, 0.0);
        EffectQualityTracker.update(state, EffectQualityTier.LOW, 0.0);

        assertEquals(EffectQualityTier.HIGH,
            EffectQualityTracker.update(state, EffectQualityTier.MEDIUM, 5.0));
        assertEquals(EffectQualityTier.HIGH,
            EffectQualityTracker.update(state, EffectQualityTier.MEDIUM, 14.0));
        assertEquals(EffectQualityTier.MEDIUM,
            EffectQualityTracker.update(state, EffectQualityTier.MEDIUM, 15.0));
    }

    @Test
    void matchingTheCurrentTierClearsThePendingChange() {
        EffectQualityTracker.State state = new EffectQualityTracker.State();
        EffectQualityTracker.update(state, EffectQualityTier.HIGH, 0.0);
        EffectQualityTracker.update(state, EffectQualityTier.LOW, 0.0);

        assertEquals(EffectQualityTier.HIGH,
            EffectQualityTracker.update(state, EffectQualityTier.HIGH, 5.0));
        assertEquals(EffectQualityTier.HIGH,
            EffectQualityTracker.update(state, EffectQualityTier.LOW, 6.0));
        assertEquals(EffectQualityTier.HIGH,
            EffectQualityTracker.update(state, EffectQualityTier.LOW, 15.0));
        assertEquals(EffectQualityTier.LOW,
            EffectQualityTracker.update(state, EffectQualityTier.LOW, 16.0));
    }

    @Test
    void rejectsInvalidTime() {
        EffectQualityTracker.State state = new EffectQualityTracker.State();

        assertThrows(IllegalArgumentException.class,
            () -> EffectQualityTracker.update(state, EffectQualityTier.HIGH, Double.NaN));
    }
}
