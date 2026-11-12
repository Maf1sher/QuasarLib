package mafisher.quasar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectQualityTier;
import org.junit.jupiter.api.Test;

class QuasarClientConfigTest {
    @Test
    void publishesANonEmptyConfigSpec() {
        assertFalse(QuasarClientConfig.SPEC.isEmpty());
        assertTrue(QuasarClientConfig.EFFECT_BUDGET_MS.getDefault() > 0.0);
        assertNotNull(QuasarClientConfig.AUTO_MAX_TIER.getDefault());
    }

    @Test
    void fallsBackToDefaultsBeforeTheConfigLoads() {
        assertEquals(QuasarClientConfig.QUALITY_MODE.getDefault(),
            QuasarClientConfig.qualityMode());
        assertEquals(QuasarClientConfig.AUTO_MAX_TIER.getDefault(),
            QuasarClientConfig.autoMaxTier());
        assertEquals(QuasarClientConfig.EFFECT_BUDGET_MS.getDefault(),
            QuasarClientConfig.effectBudgetMillis(), 0.0);
    }

    @Test
    void mapsFixedModesToTiers() {
        assertNull(QuasarClientConfig.QualityMode.AUTO.fixedTier());
        assertEquals(EffectQualityTier.LOW, QuasarClientConfig.QualityMode.LOW.fixedTier());
        assertEquals(EffectQualityTier.MEDIUM, QuasarClientConfig.QualityMode.MEDIUM.fixedTier());
        assertEquals(EffectQualityTier.HIGH, QuasarClientConfig.QualityMode.HIGH.fixedTier());
        assertEquals(EffectQualityTier.ULTRA, QuasarClientConfig.QualityMode.ULTRA.fixedTier());
    }
}
