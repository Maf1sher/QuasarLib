package mafisher.quasar.client.effect.mesh;

import java.util.List;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectStaticMesh;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/** Shared validation and quad emission for OBJ and JSON static meshes. */
final class StaticMeshBuilder {
    private static final float MIN_NORMAL_LENGTH_SQUARED = 1.0E-20F;
    private static final float MIN_NEWELL_LENGTH_SQUARED = 1.0E-12F;
    private static final Vector3f FALLBACK_NORMAL = new Vector3f(0.0F, 1.0F, 0.0F);

    private final EffectMesh.Builder mesh;
    private int vertices;
    private float minX = Float.POSITIVE_INFINITY;
    private float minY = Float.POSITIVE_INFINITY;
    private float minZ = Float.POSITIVE_INFINITY;
    private float maxX = Float.NEGATIVE_INFINITY;
    private float maxY = Float.NEGATIVE_INFINITY;
    private float maxZ = Float.NEGATIVE_INFINITY;

    StaticMeshBuilder(int quadCount) {
        if (quadCount <= 0 || (long)quadCount * 4L > EffectStaticMesh.MAX_VERTICES) {
            throw new IllegalArgumentException(
                "Static mesh quad count must be between 1 and "
                    + EffectStaticMesh.MAX_VERTICES / 4
            );
        }
        this.mesh = EffectMesh.builder(quadCount * 4);
    }

    void vertex(EffectMesh.Vertex vertex) {
        Objects.requireNonNull(vertex, "vertex");
        if (this.vertices >= EffectStaticMesh.MAX_VERTICES) {
            throw new IllegalArgumentException(
                "Static mesh exceeds " + EffectStaticMesh.MAX_VERTICES + " vertices"
            );
        }
        this.mesh.vertex(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v(),
            vertex.normalX(), vertex.normalY(), vertex.normalZ(), vertex.alphaMultiplier());
        this.minX = Math.min(this.minX, vertex.x());
        this.minY = Math.min(this.minY, vertex.y());
        this.minZ = Math.min(this.minZ, vertex.z());
        this.maxX = Math.max(this.maxX, vertex.x());
        this.maxY = Math.max(this.maxY, vertex.y());
        this.maxZ = Math.max(this.maxZ, vertex.z());
        this.vertices++;
    }

    StaticMeshData build() {
        if (this.vertices == 0) {
            throw new IllegalArgumentException("Static mesh contains no vertices");
        }
        return new StaticMeshData(this.mesh.build(),
            new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ));
    }

    /**
     * Returns a unit normal for one polygon. A degenerate polygon falls back to +Y so
     * one bad face does not reject an otherwise usable asset.
     */
    static Vector3f faceNormal(List<Vector3f> points) {
        Objects.requireNonNull(points, "points");
        if (points.size() < 3) {
            throw new IllegalArgumentException("A static mesh face needs at least three vertices");
        }
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (int index = 0; index < points.size(); index++) {
            Vector3f current = points.get(index);
            Vector3f next = points.get((index + 1) % points.size());
            x += (current.y - next.y) * (current.z + next.z);
            y += (current.z - next.z) * (current.x + next.x);
            z += (current.x - next.x) * (current.y + next.y);
        }
        double lengthSquared = x * x + y * y + z * z;
        if (!(lengthSquared > MIN_NEWELL_LENGTH_SQUARED)) {
            return new Vector3f(FALLBACK_NORMAL);
        }
        double inverseLength = 1.0 / Math.sqrt(lengthSquared);
        return new Vector3f(
            (float)(x * inverseLength),
            (float)(y * inverseLength),
            (float)(z * inverseLength)
        );
    }

    /** Returns a normalized copy, or {@code null} for a zero or invalid vector. */
    static Vector3f normalized(Vector3f normal) {
        float lengthSquared = normal.lengthSquared();
        if (!(lengthSquared > MIN_NORMAL_LENGTH_SQUARED) || !Float.isFinite(lengthSquared)) {
            return null;
        }
        return new Vector3f(normal).div((float)Math.sqrt(lengthSquared));
    }
}
