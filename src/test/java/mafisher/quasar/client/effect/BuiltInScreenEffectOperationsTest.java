package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.EffectQualityTier;
import org.junit.jupiter.api.Test;

class BuiltInScreenEffectOperationsTest {
    @Test
    void usesTheSeparableBloomBlurOnlyOnHighTiers() {
        assertFalse(BuiltInScreenEffectOperations.bloomUsesBlur(EffectQualityTier.LOW));
        assertFalse(BuiltInScreenEffectOperations.bloomUsesBlur(EffectQualityTier.MEDIUM));
        assertTrue(BuiltInScreenEffectOperations.bloomUsesBlur(EffectQualityTier.HIGH));
        assertTrue(BuiltInScreenEffectOperations.bloomUsesBlur(EffectQualityTier.ULTRA));
    }

    @Test
    void skipsSceneBlurOnlyOnTheLowestTier() {
        assertFalse(BuiltInScreenEffectOperations.blurEnabled(EffectQualityTier.LOW));
        assertTrue(BuiltInScreenEffectOperations.blurEnabled(EffectQualityTier.MEDIUM));
        assertTrue(BuiltInScreenEffectOperations.blurEnabled(EffectQualityTier.HIGH));
        assertTrue(BuiltInScreenEffectOperations.blurEnabled(EffectQualityTier.ULTRA));
    }

    @Test
    void halvesTheBlurRadiusBelowHigh() {
        assertEquals(0.5F, BuiltInScreenEffectOperations.blurRadiusScale(EffectQualityTier.MEDIUM));
        assertEquals(1.0F, BuiltInScreenEffectOperations.blurRadiusScale(EffectQualityTier.HIGH));
    }
}
