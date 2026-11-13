package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.junit.jupiter.api.Test;

class EffectQualityTierTest {
    @Test
    void ordersTiersFromLowToUltra() {
        assertEquals(0, EffectQualityTier.LOW.level());
        assertEquals(1, EffectQualityTier.MEDIUM.level());
        assertEquals(2, EffectQualityTier.HIGH.level());
        assertEquals(3, EffectQualityTier.ULTRA.level());
        assertTrue(EffectQualityTier.HIGH.atLeast(EffectQualityTier.MEDIUM));
        assertTrue(EffectQualityTier.HIGH.atLeast(EffectQualityTier.HIGH));
        assertFalse(EffectQualityTier.LOW.atLeast(EffectQualityTier.MEDIUM));
    }

    @Test
    void exposesStableDegradationParameters() {
        assertEquals(2.0F, EffectQualityTier.LOW.lodDistanceMultiplier());
        assertEquals(1.25F, EffectQualityTier.MEDIUM.lodDistanceMultiplier());
        assertEquals(1.0F, EffectQualityTier.HIGH.lodDistanceMultiplier());
        assertEquals(0.75F, EffectQualityTier.ULTRA.lodDistanceMultiplier());

        assertEquals(0.5F, EffectQualityTier.LOW.screenScale());
        assertEquals(0.75F, EffectQualityTier.MEDIUM.screenScale());
        assertEquals(1.0F, EffectQualityTier.HIGH.screenScale());
        assertEquals(1.0F, EffectQualityTier.ULTRA.screenScale());
    }
}
