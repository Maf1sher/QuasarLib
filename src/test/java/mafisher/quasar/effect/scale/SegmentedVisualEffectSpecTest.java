package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.VisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SegmentedVisualEffectSpecTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");

    @Test
    void placesSegmentsOnACenteredGrid() {
        SegmentedVisualEffectSpec spec = spec(3, 1, 10.0, 0.0F);

        assertEquals(new Vec3(-10.0, 64.0, 0.0), spec.segmentSpec(0, 0).position());
        assertEquals(new Vec3(0.0, 64.0, 0.0), spec.segmentSpec(1, 0).position());
        assertEquals(new Vec3(10.0, 64.0, 0.0), spec.segmentSpec(2, 0).position());
    }

    @Test
    void rotatesTheGridWithYaw() {
        SegmentedVisualEffectSpec spec = spec(1, 2, 10.0, 90.0F);

        Vec3 back = spec.segmentSpec(0, 0).position();
        Vec3 front = spec.segmentSpec(0, 1).position();

        assertTrue(Math.abs(back.x - 5.0) < 1.0e-9);
        assertTrue(Math.abs(back.z) < 1.0e-9);
        assertTrue(Math.abs(front.x + 5.0) < 1.0e-9);
        assertTrue(Math.abs(front.z) < 1.0e-9);
    }

    @Test
    void derivesDeterministicPerSegmentSeeds() {
        SegmentedVisualEffectSpec spec = spec(4, 4, 8.0, 0.0F);
        SegmentedVisualEffectSpec same = spec(4, 4, 8.0, 0.0F);
        SegmentedVisualEffectSpec other = new SegmentedVisualEffectSpec(
            EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F, 4.0F,
            4, 4, 8.0, 200, 99L, EffectAudience.dimensionWide()
        );

        assertEquals(spec.segmentSeed(2, 3), same.segmentSeed(2, 3));
        assertNotEquals(spec.segmentSeed(2, 3), spec.segmentSeed(3, 3));
        assertNotEquals(spec.segmentSeed(2, 3), spec.segmentSeed(2, 2));
        assertNotEquals(spec.segmentSeed(2, 3), other.segmentSeed(2, 3));
    }

    @Test
    void keepsSegmentSpecsInsideTheExistingCaps() {
        SegmentedVisualEffectSpec spec = spec(2, 2, 20.0, 35.0F);

        for (int row = 0; row < spec.rows(); row++) {
            for (int column = 0; column < spec.columns(); column++) {
                VisualEffectSpec segment = spec.segmentSpec(column, row);
                assertEquals(EFFECT_ID, segment.effectId());
                assertEquals(4.0F, segment.scale());
                assertEquals(200, segment.durationTicks());
                assertEquals(EffectAudience.dimensionWide(), segment.audience());
                assertEquals(spec.segmentSeed(column, row), segment.seed());
            }
        }
    }

    @Test
    void validatesLayoutAndCaps() {
        assertThrows(IllegalArgumentException.class, () -> spec(0, 1, 10.0, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> spec(1, 0, 10.0, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> spec(17, 8, 4.0, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> spec(2, 2, 0.0, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> spec(1, 1, -1.0, 0.0F));
        assertThrows(IllegalArgumentException.class,
            () -> new SegmentedVisualEffectSpec(
                EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F, 129.0F,
                1, 1, 0.0, 200, 42L, EffectAudience.dimensionWide()
            ));
        assertThrows(IllegalArgumentException.class,
            () -> new SegmentedVisualEffectSpec(
                EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F, 4.0F,
                1, 1, 0.0, 0, 42L, EffectAudience.dimensionWide()
            ));
        assertThrows(IndexOutOfBoundsException.class, () -> spec(2, 2, 4.0, 0.0F).segmentSpec(2, 0));
    }

    private static SegmentedVisualEffectSpec spec(int columns, int rows, double spacing, float yaw) {
        return new SegmentedVisualEffectSpec(
            EFFECT_ID,
            new Vec3(0.0, 64.0, 0.0),
            yaw,
            0.0F,
            0.0F,
            4.0F,
            columns,
            rows,
            spacing,
            200,
            42L,
            EffectAudience.dimensionWide()
        );
    }
}
