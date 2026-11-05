package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void sharesTheVerticalPlaneUsedByTheEffectProbe() {
        EffectMesh mesh = EffectMeshes.verticalPlane();
        assertSame(mesh, EffectMeshes.verticalPlane());
        assertEquals(4, mesh.vertexCount());
        assertVertex(mesh.vertex(0), -0.5F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        assertVertex(mesh.vertex(1), 0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        assertVertex(mesh.vertex(2), 0.5F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        assertVertex(mesh.vertex(3), -0.5F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
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
