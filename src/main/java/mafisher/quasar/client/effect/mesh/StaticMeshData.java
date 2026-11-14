package mafisher.quasar.client.effect.mesh;

import java.util.Objects;
import mafisher.quasar.api.client.render.EffectMesh;
import net.minecraft.world.phys.AABB;

/** One immutable parsed static mesh and its conservative local-space bounds. */
record StaticMeshData(EffectMesh mesh, AABB localBounds) {
    StaticMeshData {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(localBounds, "localBounds");
    }
}
