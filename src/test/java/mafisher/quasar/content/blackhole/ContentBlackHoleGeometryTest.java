package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentBlackHoleGeometryTest {
    @Test
    void stepBudgetKeepsATraversalFloorAndScalesWithQuality() {
        int fullQualityBase = ContentBlackHoleGeometry.stepCount(0, 0);
        for (int quality = 1; quality <= 3; quality++) {
            int next = ContentBlackHoleGeometry.stepCount(0, quality);
            assertTrue(next > fullQualityBase, "quality " + quality);
            fullQualityBase = next;
        }

        for (int quality = 0; quality <= 3; quality++) {
            int previous = Integer.MAX_VALUE;
            for (int lod = 0; lod <= 32; lod++) {
                int steps = ContentBlackHoleGeometry.stepCount(lod, quality);
                assertTrue(steps >= ContentBlackHoleGeometry.MIN_STEPS,
                    "quality " + quality + ", lod " + lod);
                assertTrue(steps <= previous, "quality " + quality + ", lod " + lod);
                previous = steps;
            }
        }

        assertEquals(
            ContentBlackHoleGeometry.MIN_STEPS,
            ContentBlackHoleGeometry.stepCount(64, 0)
        );
        assertEquals(
            Math.max(
                ContentBlackHoleGeometry.MIN_STEPS,
                ContentBlackHoleGeometry.stepCount(0, 0) - ContentBlackHoleGeometry.LOD_STEP_REDUCTION
            ),
            ContentBlackHoleGeometry.stepCount(1, 0)
        );
    }

    @Test
    void variantSeedPreservesTheLowPayloadAndSelectsThePalette() {
        long payload = 0x0123_4567_89AB_CDEFL;
        long goldSeed = ContentBlackHoleGeometry.withVariant(payload, ContentBlackHoleGeometry.VARIANT_GOLD);
        long neonSeed = ContentBlackHoleGeometry.withVariant(payload, ContentBlackHoleGeometry.VARIANT_NEON);

        assertEquals(ContentBlackHoleGeometry.VARIANT_GOLD, ContentBlackHoleGeometry.variant(goldSeed));
        assertEquals(ContentBlackHoleGeometry.VARIANT_NEON, ContentBlackHoleGeometry.variant(neonSeed));
        assertEquals(payload & 0x0FFF_FFFF_FFFF_FFFFL, goldSeed & 0x0FFF_FFFF_FFFF_FFFFL);
        assertEquals(payload & 0x0FFF_FFFF_FFFF_FFFFL, neonSeed & 0x0FFF_FFFF_FFFF_FFFFL);
        assertEquals(0.0F, ContentBlackHoleGeometry.chargeForSeed(goldSeed, 0.55F), 0.0001F);
        assertEquals(1.0F, ContentBlackHoleGeometry.chargeForSeed(neonSeed, 0.55F), 0.0001F);
        assertEquals(0.55F, ContentBlackHoleGeometry.chargeForSeed(payload, 0.55F), 0.0001F);
        assertThrows(IllegalArgumentException.class,
            () -> ContentBlackHoleGeometry.withVariant(payload, 3));
    }
}
