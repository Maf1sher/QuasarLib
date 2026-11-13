package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshes;
import org.junit.jupiter.api.Test;

class EffectMeshesTest {
    @Test
    void sharesTheHorizontalQuadUsedByWorldEffectsAndMasks() {
        EffectMesh mesh = EffectMeshes.horizontalQuad();
        assertSame(mesh, EffectMeshes.horizontalQuad());
        assertEquals(4, mesh.vertexCount());
        assertVertex(mesh.vertex(0), -1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        assertVertex(mesh.vertex(1), -1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F);
        assertVertex(mesh.vertex(2), 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
        assertVertex(mesh.vertex(3), 1.0F, 0.0F, -1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
    }

    @Test
    void rejectsIncompleteQuads() {
        assertThrows(IllegalStateException.class, () -> EffectMesh.builder(4).build());
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> EffectMesh.builder(3));
    }

    @Test
    void rejectsInvalidVertexAttributes() {
        assertThrows(IllegalArgumentException.class,
            () -> EffectMesh.builder(4).vertex(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.1F));
        assertThrows(IllegalArgumentException.class,
            () -> EffectMesh.builder(4).vertex(Float.NaN, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
    }

    @Test
    void sharesTheVerticalPlaneUsedByTheEffectProbe() {
        EffectMesh mesh = EffectMeshes.verticalPlane();
        assertSame(mesh, EffectMeshes.verticalPlane());
        assertEquals(4, mesh.vertexCount());
        assertVertex(mesh.vertex(0), -0.5F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        assertVertex(mesh.vertex(1), 0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        assertVertex(mesh.vertex(2), 0.5F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        assertVertex(mesh.vertex(3), -0.5F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    }

    @Test
    void buildsParameterizedSpheresForLevelOfDetail() {
        assertEquals(8 * 16 * 4, EffectMeshes.unitSphere(8, 16).vertexCount());
        assertEquals(16 * 32 * 4, EffectMeshes.unitSphere(16, 32).vertexCount());
        assertEquals(32 * 64 * 4, EffectMeshes.unitSphere().vertexCount());
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.unitSphere(0, 16));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.unitSphere(16, 0));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.unitSphere(-1, 16));
    }

    private static void assertVertex(EffectMesh.Vertex vertex, float x, float y, float z, float u, float v,
        float normalX, float normalY, float normalZ) {
        assertEquals(x, vertex.x());
        assertEquals(y, vertex.y());
        assertEquals(z, vertex.z());
        assertEquals(u, vertex.u());
        assertEquals(v, vertex.v());
        assertEquals(normalX, vertex.normalX());
        assertEquals(normalY, vertex.normalY());
        assertEquals(normalZ, vertex.normalZ());
        assertTrue(Float.isFinite(vertex.alphaMultiplier()));
        assertEquals(1.0F, vertex.alphaMultiplier());
    }
}
