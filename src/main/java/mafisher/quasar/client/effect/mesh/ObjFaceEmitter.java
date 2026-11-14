package mafisher.quasar.client.effect.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectMesh;
import org.joml.Vector2f;
import org.joml.Vector3f;

/** Emits resolved OBJ faces as complete quads into one {@link StaticMeshBuilder}. */
final class ObjFaceEmitter {
    private static final Vector2f NO_UV = new Vector2f(0.0F, 0.0F);

    private final StaticMeshBuilder mesh;
    private final List<Vector3f> positions;
    private final List<Vector2f> uvs;
    private final List<Vector3f> normals;

    ObjFaceEmitter(StaticMeshBuilder mesh, List<Vector3f> positions, List<Vector2f> uvs,
        List<Vector3f> normals) {
        this.mesh = Objects.requireNonNull(mesh, "mesh");
        this.positions = Objects.requireNonNull(positions, "positions");
        this.uvs = Objects.requireNonNull(uvs, "uvs");
        this.normals = Objects.requireNonNull(normals, "normals");
    }

    void addFace(ObjFace face) {
        List<Vector3f> facePositions = new ArrayList<>(face.positions().length);
        for (int position : face.positions()) {
            facePositions.add(this.positions.get(position));
        }
        Vector3f faceNormal = StaticMeshBuilder.faceNormal(facePositions);
        int count = face.positions().length;
        if (count == 4) {
            addQuad(face, 0, 1, 2, 3, faceNormal);
        } else if (count == 3) {
            addQuad(face, 0, 1, 2, 2, faceNormal);
        } else {
            for (int index = 1; index < count - 1; index++) {
                addQuad(face, 0, index, index + 1, index + 1, faceNormal);
            }
        }
    }

    private void addQuad(ObjFace face, int first, int second, int third, int fourth,
        Vector3f faceNormal) {
        addVertex(face, first, faceNormal);
        addVertex(face, second, faceNormal);
        addVertex(face, third, faceNormal);
        addVertex(face, fourth, faceNormal);
    }

    private void addVertex(ObjFace face, int index, Vector3f faceNormal) {
        Vector3f position = this.positions.get(face.positions()[index]);
        Vector2f uv = face.uvs()[index] >= 0 ? this.uvs.get(face.uvs()[index]) : NO_UV;
        Vector3f normal = face.normals()[index] >= 0
            ? StaticMeshBuilder.normalized(this.normals.get(face.normals()[index]))
            : null;
        if (normal == null) {
            normal = faceNormal;
        }
        this.mesh.vertex(new EffectMesh.Vertex(
            position.x, position.y, position.z, uv.x, uv.y,
            normal.x, normal.y, normal.z, 1.0F
        ));
    }
}
