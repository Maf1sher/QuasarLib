package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshes;
import org.junit.jupiter.api.Test;

class ContentBlackHoleMeshGeometryTest {
    @Test
    void cachesAPoleFreeSphereForTheCoreAndScreenMask() {
        EffectMesh sphere = ContentBlackHoleMeshGeometry.sphere();
        assertSame(EffectMeshes.icosphere(), sphere);
        assertEquals(6 * 16 * 16 * 4, sphere.vertexCount());

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
        assertSame(EffectMeshes.icosphere(), ContentBlackHoleMeshGeometry.sphere(0));
        assertEquals(6 * 8 * 8 * 4, ContentBlackHoleMeshGeometry.sphere(1).vertexCount());
        assertEquals(6 * 4 * 4 * 4, ContentBlackHoleMeshGeometry.sphere(2).vertexCount());
        assertSame(ContentBlackHoleMeshGeometry.sphere(2), ContentBlackHoleMeshGeometry.sphere(99));
        assertSame(ContentBlackHoleMeshGeometry.sphere(0), ContentBlackHoleMeshGeometry.sphere(-1));
        assertEquals(3, ContentBlackHoleMeshGeometry.spheres().levelCount());
        assertEquals(-1.0, ContentBlackHoleMeshGeometry.spheres().localBounds().minY, 0.000001);
        assertEquals(1.0, ContentBlackHoleMeshGeometry.spheres().localBounds().maxY, 0.000001);
    }

    @Test
    void cachesBothWarpedDiskLayersWithValidVertexAttributes() {
        EffectMesh primary = ContentBlackHoleMeshGeometry.primaryDisk();
        EffectMesh secondary = ContentBlackHoleMeshGeometry.secondaryDisk();
        assertSame(primary, ContentBlackHoleMeshGeometry.primaryDisk());
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
