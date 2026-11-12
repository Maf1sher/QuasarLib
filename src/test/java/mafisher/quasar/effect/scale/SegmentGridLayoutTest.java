package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentHeightProfile;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SegmentGridLayoutTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");

    @Test
    void offsetsMatchTheCoordinatorSpec() {
        SegmentedVisualEffectSpec spec = SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 200, 42L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(4.0F)
            .grid(3, 3)
            .spacing(10.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
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
    void reportsTheHalfDiagonalOfTheGrid() {
        assertEquals(0.0, new SegmentGridLayout(1, 1, 0.0).halfDiagonal());
        assertEquals(Math.sqrt(2.0) * 4.0,
            new SegmentGridLayout(2, 2, 8.0).halfDiagonal(), 1.0E-9);
        assertEquals(Math.sqrt(2.0) * 12.0,
            new SegmentGridLayout(4, 4, 8.0).halfDiagonal(), 1.0E-9);
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

    @Test
    void placesCellsInVerticalLayers() {
        SegmentGridLayout layout = new SegmentGridLayout(1, 1, 3, 0.0, 6.0);

        assertEquals(3, layout.segmentCount());
        assertEquals(0, layout.index(0, 0, 0));
        assertEquals(2, layout.index(0, 0, 2));
        assertEquals(0, layout.layer(0));
        assertEquals(2, layout.layer(2));
        assertEquals(new Vec3(0.0, -6.0, 0.0), layout.offset(0, 0, 0, 0.0F));
        assertEquals(new Vec3(0.0, 0.0, 0.0), layout.offset(0, 0, 1, 0.0F));
        assertEquals(new Vec3(0.0, 6.0, 0.0), layout.offset(0, 0, 2, 0.0F));
        assertEquals(6.0, layout.halfDiagonal(), 1.0E-9);
    }

    @Test
    void decomposesLayeredFlatIndexes() {
        SegmentGridLayout layout = new SegmentGridLayout(2, 2, 2, 8.0, 4.0);

        assertEquals(8, layout.segmentCount());
        assertEquals(7, layout.index(1, 1, 1));
        assertEquals(1, layout.column(7));
        assertEquals(1, layout.row(7));
        assertEquals(1, layout.layer(7));
        assertEquals(4, layout.index(0, 0, 1));
        assertEquals(0, layout.column(4));
        assertEquals(0, layout.row(4));
        assertEquals(1, layout.layer(4));
    }

    @Test
    void appliesAPerColumnHeightProfile() {
        SegmentGridLayout layout = new SegmentGridLayout(1, 1, 2, 0.0, 6.0);
        SegmentHeightProfile heights = SegmentHeightProfile.of(1, 1, new double[] {70.0});
        EffectTransform base = new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F);

        assertEquals(67.0, layout.transform(base, 0, 0, 0, heights).position().y());
        assertEquals(73.0, layout.transform(base, 0, 0, 1, heights).position().y());
        assertEquals(61.0, layout.transform(base, 0, 0, 0).position().y());
    }

    @Test
    void rejectsInvalidLayeredLayouts() {
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(1, 1, 0, 0.0, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(1, 1, 2, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(16, 8, 2, 4.0, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new SegmentGridLayout(2, 1, 1, 0.0, 0.0));
    }
}
