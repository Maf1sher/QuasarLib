package mafisher.quasar.client.effect.mesh;

import java.util.Objects;

/** One resolved OBJ face. Indices are zero-based; UV and normal indices use {@code -1} when absent. */
record ObjFace(int[] positions, int[] uvs, int[] normals) {
    ObjFace {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(uvs, "uvs");
        Objects.requireNonNull(normals, "normals");
        if (positions.length < 3 || positions.length != uvs.length
            || positions.length != normals.length) {
            throw new IllegalArgumentException("OBJ face has inconsistent vertex records");
        }
    }

    int emittedQuads() {
        return this.positions.length == 3 || this.positions.length == 4
            ? 1
            : this.positions.length - 2;
    }
}
