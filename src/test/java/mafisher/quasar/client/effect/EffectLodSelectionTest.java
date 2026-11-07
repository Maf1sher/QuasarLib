package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectLodSelectionTest {
    private static final Vec3 ORIGIN = Vec3.ZERO;

    @Test
    void cameraInsideTheBoundingSphereUsesFullDetail() {
        EffectLod lod = EffectLodSelection.select(EffectLodProfile.of(1.0), ORIGIN, new Vec3(5.0, 0.0, 0.0),
            10.0);

        assertEquals(0, lod.level());
        assertEquals(5.0, lod.cameraDistance());
        assertEquals(0.0, lod.gapInRadii());
    }

    @Test
    void selectsBySurfaceGapInRadii() {
        EffectLod lod = EffectLodSelection.select(EffectLodProfile.of(2.0, 8.0), ORIGIN,
            new Vec3(60.0, 0.0, 0.0), 10.0);

        assertEquals(1, lod.level());
        assertEquals(60.0, lod.cameraDistance());
        assertEquals(5.0, lod.gapInRadii());
    }

    @Test
    void infiniteRadiusAlwaysStaysAtFullDetail() {
        EffectLod lod = EffectLodSelection.select(EffectLodProfile.of(1.0), ORIGIN,
            new Vec3(500.0, 0.0, 0.0), Double.POSITIVE_INFINITY);

        assertEquals(0, lod.level());
        assertEquals(0.0, lod.gapInRadii());
    }

    @Test
    void nonPositiveRadiusAlwaysStaysAtFullDetail() {
        assertEquals(0, EffectLodSelection.select(EffectLodProfile.of(1.0), ORIGIN,
            new Vec3(500.0, 0.0, 0.0), 0.0).level());
        assertEquals(0, EffectLodSelection.select(EffectLodProfile.of(1.0), ORIGIN,
            new Vec3(500.0, 0.0, 0.0), -5.0).level());
        assertEquals(0, EffectLodSelection.select(EffectLodProfile.of(1.0), ORIGIN,
            new Vec3(500.0, 0.0, 0.0), Double.NaN).level());
    }

    @Test
    void tinyRadiusClampsToTheLastLevel() {
        EffectLod lod = EffectLodSelection.select(EffectLodProfile.of(1.0), ORIGIN,
            new Vec3(1.0, 0.0, 0.0), Double.MIN_VALUE);

        assertEquals(1, lod.level());
        assertTrue(Double.isFinite(lod.gapInRadii()));
    }

    @Test
    void usesTheInterpolatedPositionAndCurrentDistance() {
        EffectLod first = EffectLodSelection.select(EffectLodProfile.of(1.0), ORIGIN,
            new Vec3(3.0, 4.0, 0.0), 10.0);
        EffectLod second = EffectLodSelection.select(EffectLodProfile.of(1.0), ORIGIN,
            new Vec3(30.0, 40.0, 0.0), 1.0);

        assertEquals(0, first.level());
        assertEquals(1, second.level());
        assertEquals(50.0, second.cameraDistance());
        assertEquals(49.0, second.gapInRadii());
    }
}
