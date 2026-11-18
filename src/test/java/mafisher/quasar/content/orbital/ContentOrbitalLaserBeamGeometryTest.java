package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserBeamGeometryTest {
    private static final ContentOrbitalLaserBeamSettings SETTINGS =
        ContentOrbitalLaserBeamSettings.DEFAULT;

    @Test
    void marchStepsFallWithLodAndRiseWithQuality() {
        for (EffectQualityTier quality : EffectQualityTier.values()) {
            int previous = Integer.MAX_VALUE;
            for (int lod = 0; lod <= 6; lod++) {
                int steps = ContentOrbitalLaserBeamGeometry.marchSteps(lod, quality);
                assertTrue(steps >= 8 && steps <= 56);
                assertTrue(steps <= previous, quality + " lod " + lod);
                previous = steps;
            }
        }
        for (int lod = 0; lod <= 3; lod++) {
            int previous = 0;
            for (EffectQualityTier quality : EffectQualityTier.values()) {
                int steps = ContentOrbitalLaserBeamGeometry.marchSteps(lod, quality);
                assertTrue(steps >= previous, "lod " + lod + " quality " + quality);
                previous = steps;
            }
        }
    }

    @Test
    void tipHeightMovesFromTopToGround() {
        assertEquals(0.0F, ContentOrbitalLaserBeamGeometry.tipHeight(0.0F, 256.0F), 0.0F);
        assertEquals(256.0F, ContentOrbitalLaserBeamGeometry.tipHeight(1.0F, 256.0F), 0.0F);
        assertEquals(128.0F, ContentOrbitalLaserBeamGeometry.tipHeight(0.5F, 256.0F), 0.0F);
        assertEquals(0.0F, ContentOrbitalLaserBeamGeometry.tipHeight(Float.NaN, 256.0F), 0.0F);
        assertEquals(0.0F, ContentOrbitalLaserBeamGeometry.tipHeight(1.0F, 0.0F), 0.0F);
    }

    @Test
    void effectiveRadiusAppliesTheBudgetScale() {
        assertEquals(5.0F,
            ContentOrbitalLaserBeamGeometry.effectiveRadius(10.0F, 0.5F), 0.0F);
        assertEquals(10.0F,
            ContentOrbitalLaserBeamGeometry.effectiveRadius(10.0F, 1.0F), 0.0F);
        assertEquals(0.0F,
            ContentOrbitalLaserBeamGeometry.effectiveRadius(10.0F, 0.0F), 0.0F);
        assertEquals(0.0F,
            ContentOrbitalLaserBeamGeometry.effectiveRadius(Float.NaN, 1.0F), 0.0F);
        assertEquals(0.0F,
            ContentOrbitalLaserBeamGeometry.effectiveRadius(10.0F, Float.NaN), 0.0F);
        assertEquals(10.0F,
            ContentOrbitalLaserBeamGeometry.effectiveRadius(10.0F, 2.0F), 0.0F);
    }

    @Test
    void footprintAndRadiusCoverEveryPhase() {
        assertEquals(ContentOrbitalLaserSettings.BEAM_TOP_HEIGHT, SETTINGS.topHeight(), 0.0F);
        assertEquals(3.5F, SETTINGS.maximumFootprintFactor(), 0.0F);
        assertEquals(35.0, ContentOrbitalLaserBeamGeometry.maximumFootprint(SETTINGS, 10.0F),
            0.0F);
        assertEquals(SETTINGS.topHeight() + 35.0 + 0.01,
            ContentOrbitalLaserBeamGeometry.spatialRadius(SETTINGS, 10.0F), 1.0E-9);
        assertEquals(0.0,
            ContentOrbitalLaserBeamGeometry.maximumFootprint(SETTINGS, 0.0F), 0.0F);
    }

    @Test
    void settingsRejectNonFiniteGeometry() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ContentOrbitalLaserBeamSettings(
                0.0F, 0.3F, 1.4F, 3.5F, 2.0F
            ));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ContentOrbitalLaserBeamSettings(
                256.0F, 0.3F, Float.NaN, 3.5F, 2.0F
            ));
    }
}
