package mafisher.quasar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import mafisher.quasar.effect.EffectQualityTier;
import org.junit.jupiter.api.Test;

class QuasarClientConfigTest {
    @Test
    void exposesQualityDefaults() {
        assertEquals(QuasarClientConfig.QualityMode.AUTO, QuasarClientConfig.QUALITY_MODE.getDefault());
        assertEquals(EffectQualityTier.HIGH, QuasarClientConfig.AUTO_MAX_TIER.getDefault());
        assertEquals(2.5D, QuasarClientConfig.EFFECT_BUDGET_MS.getDefault());
        assertFalse(QuasarClientConfig.SPEC.isEmpty());
    }

    @Test
    void fallsBackToDefaultsBeforeTheConfigLoads() {
        assertEquals(QuasarClientConfig.QualityMode.AUTO, QuasarClientConfig.qualityMode());
        assertEquals(EffectQualityTier.HIGH, QuasarClientConfig.autoMaxTier());
        assertEquals(2.5D, QuasarClientConfig.effectBudgetMillis());
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
