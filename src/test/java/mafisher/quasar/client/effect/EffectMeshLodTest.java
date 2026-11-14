package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshLod;
import mafisher.quasar.api.client.render.EffectMeshes;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EffectMeshLodTest {
    private static final AABB UNIT_BOUNDS = new AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0);

    @Test
    void sharesBoundsAcrossLevelsAndClampsSelection() {
        EffectMesh full = EffectMeshes.icosphere(4);
        EffectMesh medium = EffectMeshes.icosphere(2);
        EffectMesh low = EffectMeshes.icosphere(1);
        EffectMeshLod lod = EffectMeshLod.of(UNIT_BOUNDS, full, medium, low);

        assertEquals(3, lod.levelCount());
        assertSame(full, lod.mesh(0));
        assertSame(medium, lod.mesh(1));
        assertSame(low, lod.mesh(2));
        assertSame(full, lod.mesh(-5));
        assertSame(low, lod.mesh(99));
        assertEquals(UNIT_BOUNDS.minX, lod.localBounds().minX);
        assertEquals(UNIT_BOUNDS.maxY, lod.localBounds().maxY);
    }

    @Test
    void copiesTheLevelArray() {
        EffectMesh full = EffectMeshes.icosphere(4);
        EffectMesh medium = EffectMeshes.icosphere(2);
        EffectMesh[] levels = {full, medium};
        EffectMeshLod lod = EffectMeshLod.of(UNIT_BOUNDS, levels);

        levels[0] = EffectMeshes.icosphere(1);

        assertSame(full, lod.mesh(0));
        assertSame(medium, lod.mesh(1));
    }

    @Test
    void rejectsEmptyOrOversizedLevelSets() {
        EffectMesh mesh = EffectMeshes.icosphere(1);
        EffectMesh[] tooMany = new EffectMesh[EffectMeshLod.MAX_LEVELS + 1];
        Arrays.fill(tooMany, mesh);

        assertThrows(IllegalArgumentException.class, () -> EffectMeshLod.of(UNIT_BOUNDS));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshLod.of(UNIT_BOUNDS, tooMany));
        assertThrows(NullPointerException.class, () -> EffectMeshLod.of(null, mesh));
        assertThrows(NullPointerException.class, () -> EffectMeshLod.of(UNIT_BOUNDS, (EffectMesh)null));
    }

    @Test
    void rejectsInvalidBounds() {
        EffectMesh mesh = EffectMeshes.icosphere(1);

        assertThrows(IllegalArgumentException.class, () -> EffectMeshLod.of(AABB.INFINITE, mesh));
        assertThrows(IllegalArgumentException.class,
            () -> EffectMeshLod.of(new AABB(-1.0, -1.0, -1.0, Double.NaN, 1.0, 1.0), mesh));
    }

    @Test
    void rejectsLevelsOutsideTheSharedBounds() {
        EffectMesh outside = EffectMesh.builder(4)
            .vertex(2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F)
            .vertex(2.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
            .vertex(3.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F)
            .vertex(3.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F)
            .build();

        assertThrows(IllegalArgumentException.class,
            () -> EffectMeshLod.of(new AABB(0.0, -1.0, -1.0, 1.0, 1.0, 1.0), outside));
    }
}
