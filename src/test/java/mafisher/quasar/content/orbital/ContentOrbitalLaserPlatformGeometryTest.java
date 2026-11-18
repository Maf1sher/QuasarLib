package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserPlatformGeometryTest {
    @Test
    void traceStepsFallWithLodAndRiseWithQuality() {
        for (EffectQualityTier quality : EffectQualityTier.values()) {
            int previous = Integer.MAX_VALUE;
            for (int lod = 0; lod <= 6; lod++) {
                int steps = ContentOrbitalLaserPlatformGeometry.traceSteps(lod, quality);
                assertTrue(steps >= ContentOrbitalLaserPlatformGeometry.MIN_TRACE_STEPS,
                    quality + " lod " + lod);
                assertTrue(steps <= ContentOrbitalLaserPlatformGeometry.MAX_TRACE_STEPS,
                    quality + " lod " + lod);
                assertTrue(steps <= previous, quality + " lod " + lod);
                previous = steps;
            }
        }
        for (int lod = 0; lod <= 3; lod++) {
            int previous = 0;
            for (EffectQualityTier quality : EffectQualityTier.values()) {
                int steps = ContentOrbitalLaserPlatformGeometry.traceSteps(lod, quality);
                assertTrue(steps >= previous, "lod " + lod + " quality " + quality);
                previous = steps;
            }
        }
    }

    @Test
    void detailFallsWithLodButNeverDropsTheSilhouette() {
        for (EffectQualityTier quality : EffectQualityTier.values()) {
            float previous = Float.MAX_VALUE;
            for (int lod = 0; lod <= 6; lod++) {
                float detail = ContentOrbitalLaserPlatformGeometry.detail(lod, quality);
                assertTrue(detail >= 0.25F && detail <= 1.0F, quality + " lod " + lod);
                assertTrue(detail <= previous + 1.0E-6F, quality + " lod " + lod);
                previous = detail;
            }
        }
        for (int lod = 0; lod <= 3; lod++) {
            float previous = 0.0F;
            for (EffectQualityTier quality : EffectQualityTier.values()) {
                float detail = ContentOrbitalLaserPlatformGeometry.detail(lod, quality);
                assertTrue(detail >= previous, "lod " + lod + " quality " + quality);
                previous = detail;
            }
        }
        assertTrue(ContentOrbitalLaserPlatformGeometry.detail(0, EffectQualityTier.LOW)
            < ContentOrbitalLaserPlatformGeometry.detail(0, EffectQualityTier.MEDIUM));
        assertEquals(1.0F,
            ContentOrbitalLaserPlatformGeometry.detail(0, EffectQualityTier.ULTRA), 0.0F);
    }

    @Test
    void lowQualityAndHighLodStillKeepATraceBudget() {
        assertEquals(ContentOrbitalLaserPlatformGeometry.MIN_TRACE_STEPS,
            ContentOrbitalLaserPlatformGeometry.traceSteps(6, EffectQualityTier.LOW));
        assertTrue(ContentOrbitalLaserPlatformGeometry.traceSteps(6, EffectQualityTier.LOW)
            > ContentOrbitalLaserBeamGeometry.marchSteps(6, EffectQualityTier.LOW));
    }
}
