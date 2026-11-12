package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.EffectMesh;
import mafisher.quasar.api.client.EffectMeshes;
import org.junit.jupiter.api.Test;

class BlackHoleGeometryTest {
    @Test
    void cachesAUnitSphereForTheCoreAndScreenMask() {
        EffectMesh sphere = BlackHoleGeometry.sphere();
        assertSame(EffectMeshes.unitSphere(), sphere);
        assertEquals(32 * 64 * 4, sphere.vertexCount());

        for (int index = 0; index < sphere.vertexCount(); index++) {
            EffectMesh.Vertex vertex = sphere.vertex(index);
            assertTrue(Float.isFinite(vertex.x()));
            assertTrue(Float.isFinite(vertex.y()));
            assertTrue(Float.isFinite(vertex.z()));
            assertTrue(Float.isFinite(vertex.u()));
            assertTrue(Float.isFinite(vertex.v()));
            assertEquals(1.0, Math.sqrt(vertex.x() * vertex.x() + vertex.y() * vertex.y()
                + vertex.z() * vertex.z()), 0.00001);
        }
    }

    @Test
    void cachesASmallerSphereForEachLevelOfDetail() {
        assertSame(EffectMeshes.unitSphere(), BlackHoleGeometry.sphere(0));
        assertEquals(16 * 32 * 4, BlackHoleGeometry.sphere(1).vertexCount());
        assertEquals(8 * 16 * 4, BlackHoleGeometry.sphere(2).vertexCount());
        assertSame(BlackHoleGeometry.sphere(2), BlackHoleGeometry.sphere(99));
        assertSame(BlackHoleGeometry.sphere(0), BlackHoleGeometry.sphere(-1));
    }

    @Test
    void cachesBothWarpedDiskLayersWithValidVertexAttributes() {
        EffectMesh primary = BlackHoleGeometry.primaryDisk();
        EffectMesh secondary = BlackHoleGeometry.secondaryDisk();
        assertSame(primary, BlackHoleGeometry.primaryDisk());
        assertEquals(6 * 64 * 2 * 4 + 64 * 2 * 4, primary.vertexCount());
        assertEquals(primary.vertexCount(), secondary.vertexCount());

        for (EffectMesh layer : new EffectMesh[] {primary, secondary}) {
            for (int index = 0; index < layer.vertexCount(); index++) {
                EffectMesh.Vertex vertex = layer.vertex(index);
                assertTrue(Float.isFinite(vertex.x()));
                assertTrue(Float.isFinite(vertex.y()));
                assertTrue(Float.isFinite(vertex.z()));
                assertTrue(Float.isFinite(vertex.u()) && vertex.u() >= 0.0F && vertex.u() <= 1.0F);
                assertTrue(Float.isFinite(vertex.v()) && vertex.v() >= 0.0F && vertex.v() <= 1.0F);
                assertTrue(Float.isFinite(vertex.alphaMultiplier())
                    && vertex.alphaMultiplier() > 0.0F && vertex.alphaMultiplier() <= 1.0F);
            }
        }
    }
}
