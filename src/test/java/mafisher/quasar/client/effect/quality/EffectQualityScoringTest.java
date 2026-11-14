package mafisher.quasar.client.effect.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.client.render.EffectScreenMetrics;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.junit.jupiter.api.Test;

class EffectQualityScoringTest {
    @Test
    void keepsTheGlobalTierForLargeOrCentredEffects() {
        assertEquals(EffectQualityTier.HIGH, score(EffectQualityTier.HIGH, 0.5, 0.0));
        assertEquals(EffectQualityTier.HIGH, score(EffectQualityTier.HIGH, 0.08, 0.0));
    }

    @Test
    void dropsOneTierForSmallEffects() {
        assertEquals(EffectQualityTier.MEDIUM, score(EffectQualityTier.HIGH, 0.05, 0.0));
        assertEquals(EffectQualityTier.MEDIUM, score(EffectQualityTier.HIGH, 0.02, 0.0));
    }

    @Test
    void dropsTwoTiersForTinyOrPeripheralEffectsAndFloorsAtLow() {
        assertEquals(EffectQualityTier.LOW, score(EffectQualityTier.HIGH, 0.01, 0.0));
        assertEquals(EffectQualityTier.LOW, score(EffectQualityTier.LOW, 0.01, 0.0));
        assertEquals(EffectQualityTier.MEDIUM, score(EffectQualityTier.ULTRA, 0.01, 0.0));
    }

    @Test
    void centrePriorityLowerScoresTheSameCoverageAtTheEdge() {
        EffectScreenMetrics centre = EffectScreenMetrics.of(0.1, 0.0);
        EffectScreenMetrics edge = EffectScreenMetrics.of(0.1, 1.0);

        assertEquals(EffectQualityTier.HIGH,
            EffectQualityScoring.effectiveTier(EffectQualityTier.HIGH, centre, true));
        assertEquals(EffectQualityTier.MEDIUM,
            EffectQualityScoring.effectiveTier(EffectQualityTier.HIGH, edge, true));
    }

    @Test
    void fixedModeAndMissingProjectionKeepTheGlobalTier() {
        assertEquals(EffectQualityTier.HIGH,
            EffectQualityScoring.effectiveTier(EffectQualityTier.HIGH,
                EffectScreenMetrics.of(0.01, 0.0), false));
        assertEquals(EffectQualityTier.HIGH,
            EffectQualityScoring.effectiveTier(EffectQualityTier.HIGH,
                EffectScreenMetrics.UNKNOWN, true));
    }

    @Test
    void rejectsNullInputs() {
        EffectScreenMetrics metrics = EffectScreenMetrics.of(0.1, 0.0);
        assertThrows(NullPointerException.class,
            () -> EffectQualityScoring.effectiveTier(null, metrics, true));
        assertThrows(NullPointerException.class,
            () -> EffectQualityScoring.effectiveTier(EffectQualityTier.HIGH, null, true));
    }

    private static EffectQualityTier score(EffectQualityTier global, double coverage, double centreDistance) {
        return EffectQualityScoring.effectiveTier(global,
            EffectScreenMetrics.of(coverage, centreDistance), true);
    }
}
