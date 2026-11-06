package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SegmentGridLayoutTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");

    @Test
    void offsetsMatchTheCoordinatorSpec() {
        SegmentedVisualEffectSpec spec = new SegmentedVisualEffectSpec(
            EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F, 4.0F,
            3, 3, 10.0, 200, 42L, EffectAudience.dimensionWide()
        );
        SegmentGridLayout layout = spec.layout();

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                assertEquals(spec.segmentOffset(column, row, 0.0F), layout.offset(column, row, 0.0F),
                    "offset for " + column + "," + row);
            }
        }
    }

    @Test
    void offsetsRotateWithYaw() {
        SegmentGridLayout layout = new SegmentGridLayout(2, 2, 8.0);

        Vec3 leftFront = layout.offset(0, 0, 90.0F);

        assertEquals(4.0, leftFront.x, 1.0E-6);
        assertEquals(-4.0, leftFront.z, 1.0E-6);
        assertEquals(0.0, leftFront.y, 1.0E-6);
    }

    @Test
    void transformInheritsRotationAndMovesFromTheBase() {
        SegmentGridLayout layout = new SegmentGridLayout(3, 1, 10.0);
        EffectTransform base = new EffectTransform(new EffectPoint(100.0, 64.0, -20.0), 0.0F, 15.0F, 5.0F);

        EffectTransform left = layout.transform(base, 0, 0);
        EffectTransform middle = layout.transform(base, 1, 0);
        EffectTransform right = layout.transform(base, 2, 0);

        assertEquals(new EffectPoint(90.0, 64.0, -20.0), left.position());
        assertEquals(new EffectPoint(100.0, 64.0, -20.0), middle.position());
        assertEquals(new EffectPoint(110.0, 64.0, -20.0), right.position());
        assertEquals(0.0F, left.yaw());
        assertEquals(15.0F, left.pitch());
        assertEquals(5.0F, left.roll());
    }

    @Test
    void rejectsInvalidLayouts() {
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(0, 1, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(1, 0, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(2, 2, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(16, 16, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(1, 1, Double.NaN));
    }

    @Test
    void oneCellGridsAllowZeroSpacing() {
        SegmentGridLayout layout = new SegmentGridLayout(1, 1, 0.0);

        assertEquals(1, layout.segmentCount());
        assertEquals(Vec3.ZERO, layout.offset(0, 0, 0.0F));
    }
}
