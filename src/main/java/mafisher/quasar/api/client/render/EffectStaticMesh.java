package mafisher.quasar.api.client.render;

import java.util.Objects;
import mafisher.quasar.client.effect.mesh.StaticMeshHandle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Reloadable static quad mesh loaded from a client resource pack.
 *
 * <p>The asset ID includes the file extension. Assets normally live under
 * {@code assets/<namespace>/quasar/meshes/<name>.obj} or {@code .../<name>.json}. OBJ
 * assets use the standard {@code v}, {@code vt}, {@code vn}, and {@code f} records;
 * material and texture records are ignored because the render pass owns the
 * material. OBJ UVs are converted from the bottom-left origin to Minecraft's
 * top-left origin, polygon faces are triangulated, and triangles are emitted as
 * degenerate quads because {@link EffectMesh} uses {@code BLOCK}/{@code QUADS}.</p>
 *
 * <p>JSON assets use {@code format_version: 1} and an array of quads; each quad is
 * an array of exactly four vertex objects with a required {@code position} and
 * optional {@code uv}, {@code normal}, and {@code alpha}. Unknown keys are
 * rejected. Missing or zero normals fall back to the face normal.</p>
 *
 * <p>The engine calls {@link VisualEffectRenderer#onResourceReload(ResourceManager)}
 * once before the first frame and after every client resource reload. Call
 * {@link #reload(ResourceManager)} from that callback. A failed reload keeps the
 * previous mesh and bounds, so active instances keep rendering the last valid
 * asset. The first failure leaves {@link #mesh()} null.</p>
 *
 * <p>Meshes are local-space, Y-up, and authored in blocks; apply scale and local
 * rotation in the {@link EffectStaticMeshPass} transform. Draw them through
 * {@link StaticMeshVisualEffectRenderer}; the pass uploads once into
 * {@link com.mojang.blaze3d.vertex.VertexBuffer} through the shared mesh cache.
 * Instances are client-thread-only and not thread-safe.</p>
 */
public final class EffectStaticMesh {
    /** Maximum number of emitted vertices for one static mesh. */
    public static final int MAX_VERTICES = 65_536;

    private final StaticMeshHandle handle;

    private EffectStaticMesh(StaticMeshHandle handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    /**
     * Creates an unloaded handle. The asset is read by {@link #reload(ResourceManager)}.
     *
     * @throws IllegalArgumentException when the path does not end in {@code .obj} or {@code .json}
     */
    public static EffectStaticMesh of(ResourceLocation location) {
        return new EffectStaticMesh(new StaticMeshHandle(location));
    }

    /** Returns the asset ID, including its file extension. */
    public ResourceLocation location() {
        return this.handle.location();
    }

    /** Returns whether at least one load has succeeded. */
    public boolean loaded() {
        return this.handle.loaded();
    }

    /**
     * Returns the last valid mesh, or {@code null} before the first successful load. This
     * method performs no I/O and never retries a failed load.
     */
    public @Nullable EffectMesh mesh() {
        return this.handle.mesh();
    }

    /**
     * Returns the local-space bounds of the last valid mesh, or {@code null} before the
     * first successful load. The bounds are not scaled or rotated.
     */
    public @Nullable AABB localBounds() {
        return this.handle.localBounds();
    }

    /**
     * Re-reads and validates the asset. On failure the previous mesh and bounds stay
     * active and a warning is logged.
     *
     * @return {@code true} when a new mesh was accepted; {@code false} when the last
     *     valid mesh was kept
     */
    public boolean reload(ResourceManager resources) {
        return this.handle.reload(resources);
    }
}
