package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mafisher.quasar.effect.EffectQualityTier;
import org.junit.jupiter.api.Test;

class EffectQualityGovernorTest {
    private static final long BUDGET = 100L;

    @Test
    void degradesAfterTwoWindowsAboveBudget() {
        EffectQualityGovernor governor = new EffectQualityGovernor(EffectQualityTier.HIGH, BUDGET, 4, 2);

        feed(governor, 4, 200L);

        assertEquals(EffectQualityTier.MEDIUM, governor.tier());
    }

    @Test
    void recoversOnlyAfterFourWindowsBelowTheRecoveryThreshold() {
        EffectQualityGovernor governor = new EffectQualityGovernor(EffectQualityTier.HIGH, BUDGET, 4, 2);
        feed(governor, 4, 200L);
        assertEquals(EffectQualityTier.MEDIUM, governor.tier());

        // Two cooldown windows plus four below-budget windows.
        feed(governor, 10, 10L);
        assertEquals(EffectQualityTier.MEDIUM, governor.tier());
        feed(governor, 2, 10L);

        assertEquals(EffectQualityTier.HIGH, governor.tier());
    }

    @Test
    void neverExceedsConfiguredMaximumOrFallsBelowLow() {
        EffectQualityGovernor governor = new EffectQualityGovernor(EffectQualityTier.HIGH, BUDGET, 4, 2);
        feed(governor, 200, 200L);

        assertEquals(EffectQualityTier.LOW, governor.tier());

        governor.reset(EffectQualityTier.HIGH);
        feed(governor, 200, 1L);

        assertEquals(EffectQualityTier.HIGH, governor.tier());
    }

    @Test
    void resetClampsToMaximumAndClearsSamples() {
        EffectQualityGovernor governor = new EffectQualityGovernor(EffectQualityTier.MEDIUM, BUDGET, 4, 2);

        governor.reset(EffectQualityTier.ULTRA);
        assertEquals(EffectQualityTier.MEDIUM, governor.tier());
        feed(governor, 4, 200L);
        assertEquals(EffectQualityTier.LOW, governor.tier());

        governor.reset(EffectQualityTier.MEDIUM);
        assertEquals(EffectQualityTier.MEDIUM, governor.tier());
    }

    @Test
    void reportsNonNullP95AfterEvaluation() {
        EffectQualityGovernor governor = new EffectQualityGovernor(EffectQualityTier.HIGH, BUDGET, 10, 5);

        feed(governor, 5, 50L);

        assertEquals(50L, governor.lastP95Nanos());
    }

    private static void feed(EffectQualityGovernor governor, int samples, long nanos) {
        for (int index = 0; index < samples; index++) {
            governor.sample(nanos);
        }
    }
}
