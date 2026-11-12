package mafisher.quasar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectQualityTier;
import mafisher.quasar.testing.GlobalStateTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class QuasarClientConfigTest {
    @AfterEach
    void unloadConfig() {
        TestClientConfig.unload();
    }

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
    void readsLoadedValues() {
        TestClientConfig.load(config -> {
            config.set("quality_mode", "LOW");
            config.set("auto_max_tier", "MEDIUM");
            config.set("effect_budget_ms", 5.0);
            config.set("cinematic_impulses", false);
        });

        assertEquals(QuasarClientConfig.QualityMode.LOW, QuasarClientConfig.qualityMode());
        assertEquals(EffectQualityTier.MEDIUM, QuasarClientConfig.autoMaxTier());
        assertEquals(5.0, QuasarClientConfig.effectBudgetMillis(), 0.0);
        assertFalse(QuasarClientConfig.cinematicImpulses());
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
