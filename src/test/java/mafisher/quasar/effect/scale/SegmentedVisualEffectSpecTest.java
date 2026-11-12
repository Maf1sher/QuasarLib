package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.scale.SegmentHeightSource;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
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
        SegmentedVisualEffectSpec other = SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 200, 99L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(4.0F)
            .grid(4, 4)
            .spacing(8.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();

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
            () -> SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 200, 42L, EffectAudience.dimensionWide())
                .rotation(0.0F, 0.0F, 0.0F)
                .segmentScale(129.0F)
                .grid(1, 1)
                .spacing(0.0)
                .layers(1, 0.0)
                .audio(EffectAudioSpec.NONE)
                .priority(EffectPriority.NORMAL)
                .terrain(SegmentTerrainConform.NONE)
                .build());
        assertThrows(IllegalArgumentException.class,
            () -> SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 0, 42L, EffectAudience.dimensionWide())
                .rotation(0.0F, 0.0F, 0.0F)
                .segmentScale(4.0F)
                .grid(1, 1)
                .spacing(0.0)
                .layers(1, 0.0)
                .audio(EffectAudioSpec.NONE)
                .priority(EffectPriority.NORMAL)
                .terrain(SegmentTerrainConform.NONE)
                .build());
        assertThrows(IndexOutOfBoundsException.class, () -> spec(2, 2, 4.0, 0.0F).segmentSpec(2, 0));
    }

    @Test
    void supportsVerticalLayers() {
        SegmentedVisualEffectSpec spec = layeredSpec(1, 1, 3, 0.0, 6.0);

        assertEquals(3, spec.segmentCount());
        assertEquals(58.0, spec.segmentSpec(0, 0, 0).position().y, 1.0E-9);
        assertEquals(64.0, spec.segmentSpec(0, 0, 1).position().y, 1.0E-9);
        assertEquals(70.0, spec.segmentSpec(0, 0, 2).position().y, 1.0E-9);
        assertNotEquals(spec.segmentSeed(0, 0, 0), spec.segmentSeed(0, 0, 1));
        assertNotEquals(spec.segmentSeed(0, 0, 1), spec.segmentSeed(0, 0, 2));
    }

    @Test
    void validatesLayersAndTerrainParameters() {
        assertThrows(IllegalArgumentException.class, () -> layeredSpec(1, 1, 0, 0.0, 6.0));
        assertThrows(IllegalArgumentException.class, () -> layeredSpec(1, 1, 2, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> layeredSpec(16, 8, 2, 4.0, 4.0));
        assertThrows(IllegalArgumentException.class,
            () -> new SegmentTerrainConform(SegmentHeightSource.WORLD_SURFACE, Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> new SegmentTerrainConform(SegmentHeightSource.WORLD_SURFACE, 4097.0));
    }

    private static SegmentedVisualEffectSpec layeredSpec(
        int columns,
        int rows,
        int layers,
        double spacing,
        double layerSpacing
    ) {
        return SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 200, 42L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(4.0F)
            .grid(columns, rows)
            .spacing(spacing)
            .layers(layers, layerSpacing)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
    }

    private static SegmentedVisualEffectSpec spec(int columns, int rows, double spacing, float yaw) {
        return SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 200, 42L, EffectAudience.dimensionWide())
            .rotation(yaw, 0.0F, 0.0F)
            .segmentScale(4.0F)
            .grid(columns, rows)
            .spacing(spacing)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
    }
}
