package mafisher.quasar.api.client.render;

import java.util.Objects;
import net.minecraft.world.phys.AABB;

/**
 * Immutable ordered mesh levels for one renderer-visible shape. Level {@code 0} is full detail and each following
 * level is cheaper; {@link #mesh(int)} clamps requests to the available range, so a renderer can pass an engine LOD
 * level directly.
 *
 * <p>Every level shares one explicit local-space bounding box. The box is conservative and is used for culling
 * decisions; it must enclose the continuous surface of every level, not only the emitted vertices. This is
 * particularly important for low-poly spheres whose vertices may not reach an axis even though the surface does.</p>
 *
 * <p>Levels and bounds are immutable and safe to share. Selection and cross-fading stay with the renderer:
 * {@link EffectLod#previousLevel()} and {@link EffectLod#blend()} describe an optional transition.</p>
 */
public final class EffectMeshLod {
    /** Maximum number of mesh levels, matching {@link EffectLodProfile#MAX_THRESHOLDS} plus the unbounded level. */
    public static final int MAX_LEVELS = EffectLodProfile.MAX_THRESHOLDS + 1;

    private static final double BOUNDS_EPSILON = 1.0E-4;

    private final EffectMesh[] levels;
    private final AABB localBounds;

    private EffectMeshLod(AABB localBounds, EffectMesh[] levels) {
        this.localBounds = localBounds;
        this.levels = levels;
    }

    /**
     * Creates an ordered mesh LOD set with one shared local bound.
     *
     * @param localBounds conservative box that encloses every level's continuous surface
     * @param levels ordered meshes from full detail to cheapest; at least one and at most {@value #MAX_LEVELS}
     * @throws IllegalArgumentException when the level count or bounds are invalid
     * @throws NullPointerException when a level is null
     */
    public static EffectMeshLod of(AABB localBounds, EffectMesh... levels) {
        Objects.requireNonNull(localBounds, "localBounds");
        Objects.requireNonNull(levels, "levels");
        if (levels.length == 0 || levels.length > MAX_LEVELS) {
            throw new IllegalArgumentException("Mesh LOD requires between 1 and " + MAX_LEVELS + " levels");
        }
        validateBounds(localBounds);
        EffectMesh[] copy = levels.clone();
        for (int level = 0; level < copy.length; level++) {
            EffectMesh mesh = Objects.requireNonNull(copy[level], "levels[" + level + "]");
            validateInside(mesh, localBounds);
        }
        return new EffectMeshLod(localBounds, copy);
    }

    /** Number of available mesh levels. */
    public int levelCount() {
        return this.levels.length;
    }

    /**
     * Returns the mesh for a level, clamping negative requests to {@code 0} and oversized requests to the last level.
     */
    public EffectMesh mesh(int level) {
        return this.levels[Math.max(0, Math.min(level, this.levels.length - 1))];
    }

    /** Shared conservative local-space bound for every level. */
    public AABB localBounds() {
        return this.localBounds;
    }

    private static void validateBounds(AABB bounds) {
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.maxX)
            || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.maxY)) {
            throw new IllegalArgumentException("Mesh LOD bounds must be finite and ordered");
        }
        if (!Double.isFinite(bounds.minZ) || !Double.isFinite(bounds.maxZ)
            || bounds.minX > bounds.maxX || bounds.minY > bounds.maxY || bounds.minZ > bounds.maxZ) {
            throw new IllegalArgumentException("Mesh LOD bounds must be finite and ordered");
        }
    }

    private static void validateInside(EffectMesh mesh, AABB bounds) {
        for (int index = 0; index < mesh.vertexCount(); index++) {
            EffectMesh.Vertex vertex = mesh.vertex(index);
            if (outside(vertex.x(), bounds.minX, bounds.maxX)
                || outside(vertex.y(), bounds.minY, bounds.maxY)
                || outside(vertex.z(), bounds.minZ, bounds.maxZ)) {
                throw new IllegalArgumentException("Mesh LOD level escapes its shared bounds at vertex " + index);
            }
        }
    }

    private static boolean outside(float value, double minimum, double maximum) {
        return value < minimum - BOUNDS_EPSILON || value > maximum + BOUNDS_EPSILON;
    }
}
