package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SegmentIdsTest {
    @Test
    void derivesStableSegmentIds() {
        UUID coordinatorId = UUID.fromString("00000000-0000-0000-0000-000000000010");

        assertEquals(SegmentIds.segmentId(coordinatorId, 5),
            SegmentIds.segmentId(coordinatorId, 5));
        assertNotEquals(SegmentIds.segmentId(coordinatorId, 5),
            SegmentIds.segmentId(coordinatorId, 6));
        assertEquals(SegmentIds.segmentId(coordinatorId, 5),
            SegmentedVisualEffectService.segmentId(coordinatorId, 5));
    }

    @Test
    void rejectsNegativeSegmentIndexes() {
        assertThrows(IllegalArgumentException.class,
            () -> SegmentIds.segmentId(UUID.randomUUID(), -1));
    }

    @Test
    void keepsExistingFlatSeedsAndSeparatesLayers() {
        assertEquals(SegmentIds.segmentSeed(42L, 2, 3), SegmentIds.segmentSeed(42L, 2, 3, 0));
        assertNotEquals(SegmentIds.segmentSeed(42L, 2, 3, 0), SegmentIds.segmentSeed(42L, 2, 3, 1));
        assertNotEquals(SegmentIds.segmentSeed(42L, 2, 3, 1), SegmentIds.segmentSeed(42L, 2, 3, 2));
    }

    @Test
    void rejectsNegativeCells() {
        assertThrows(IllegalArgumentException.class,
            () -> SegmentIds.segmentSeed(42L, -1, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> SegmentIds.segmentSeed(42L, 0, 0, -1));
    }

    @Test
    void matchesTheSpecSeedDerivation() {
        SegmentedVisualEffectSpec spec = SegmentedVisualEffectSpec.builder(ResourceLocation.fromNamespaceAndPath("quasar_lib", "test"), new Vec3(0.0, 64.0, 0.0), 200, 42L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(4.0F)
            .grid(4, 4)
            .spacing(8.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();

        for (int row = 0; row < spec.rows(); row++) {
            for (int column = 0; column < spec.columns(); column++) {
                assertEquals(SegmentIds.segmentSeed(42L, column, row),
                    spec.segmentSeed(column, row));
            }
        }
    }
}
