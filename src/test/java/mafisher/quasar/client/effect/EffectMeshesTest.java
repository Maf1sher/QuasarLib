package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void cachesProceduralShapes() {
        assertSame(EffectMeshes.cylinder(), EffectMeshes.cylinder());
        assertSame(EffectMeshes.cone(), EffectMeshes.cone());
        assertSame(EffectMeshes.torus(), EffectMeshes.torus());
        assertSame(EffectMeshes.hemisphere(), EffectMeshes.hemisphere());
        assertSame(EffectMeshes.icosphere(), EffectMeshes.icosphere());
        assertSame(EffectMeshes.disk(), EffectMeshes.disk());
    }

    @Test
    void buildsCappedCylinder() {
        EffectMesh mesh = EffectMeshes.cylinder(3, 1);

        assertEquals(9 * 4, mesh.vertexCount());
        assertBounds(EffectMeshes.cylinder(), -1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F);
        assertOutwardNormals(mesh);
    }

    @Test
    void buildsCappedCone() {
        EffectMesh mesh = EffectMeshes.cone(3);

        assertEquals(6 * 4, mesh.vertexCount());
        assertBounds(EffectMeshes.cone(), -1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F);
        assertOutwardNormals(mesh);
    }

    @Test
    void buildsTorus() {
        EffectMesh mesh = EffectMeshes.torus(3, 3);

        assertEquals(9 * 4, mesh.vertexCount());
        assertBounds(EffectMeshes.torus(), -1.0F, -0.25F, -1.0F, 1.0F, 0.25F, 1.0F);
        assertUnitNormals(mesh);
        assertTorusNormals(mesh);
    }

    @Test
    void buildsFlatDisk() {
        EffectMesh mesh = EffectMeshes.disk(2, 5);

        assertEquals(10 * 4, mesh.vertexCount());
        assertBounds(EffectMeshes.disk(), -1.0F, 0.0F, -1.0F, 1.0F, 0.0F, 1.0F);
        for (int index = 0; index < mesh.vertexCount(); index++) {
            assertEquals(0.0F, mesh.vertex(index).y());
        }
    }

    @Test
    void buildsPoleFreeIcosphere() {
        EffectMesh mesh = EffectMeshes.icosphere(2);

        assertEquals(6 * 2 * 2 * 4, mesh.vertexCount());
        assertBounds(mesh, -1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F);
        assertNoDegenerateQuads(mesh);
        assertOutwardNormals(mesh);
    }

    @Test
    void buildsPoleFreeHemisphereWithFlatBase() {
        EffectMesh mesh = EffectMeshes.hemisphere(2);

        assertEquals((3 * 2 * 2 + 4 * 2) * 4, mesh.vertexCount());
        assertBounds(mesh, -1.0F, 0.0F, -1.0F, 1.0F, 1.0F, 1.0F);
        assertNoDegenerateDomeQuads(mesh);
    }

    @Test
    void rejectsInvalidProceduralParameters() {
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.cylinder(2, 1));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.cylinder(3, 0));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.cone(2));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.torus(2, 3));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.torus(3, 2));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.disk(0, 4));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.disk(4, 2));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.icosphere(0));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.hemisphere(1));
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.hemisphere(3));
    }

    @Test
    void rejectsOversizedProceduralMesh() {
        assertThrows(IllegalArgumentException.class, () -> EffectMeshes.cylinder(128, 128));
    }

    private static void assertBounds(EffectMesh mesh, float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ) {
        float[] minimum = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] maximum = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int index = 0; index < mesh.vertexCount(); index++) {
            EffectMesh.Vertex vertex = mesh.vertex(index);
            minimum[0] = Math.min(minimum[0], vertex.x());
            minimum[1] = Math.min(minimum[1], vertex.y());
            minimum[2] = Math.min(minimum[2], vertex.z());
            maximum[0] = Math.max(maximum[0], vertex.x());
            maximum[1] = Math.max(maximum[1], vertex.y());
            maximum[2] = Math.max(maximum[2], vertex.z());
        }
        assertEquals(minX, minimum[0], 0.0001F);
        assertEquals(minY, minimum[1], 0.0001F);
        assertEquals(minZ, minimum[2], 0.0001F);
        assertEquals(maxX, maximum[0], 0.0001F);
        assertEquals(maxY, maximum[1], 0.0001F);
        assertEquals(maxZ, maximum[2], 0.0001F);
    }

    private static void assertUnitNormals(EffectMesh mesh) {
        for (int index = 0; index < mesh.vertexCount(); index++) {
            EffectMesh.Vertex vertex = mesh.vertex(index);
            float length = vertex.normalX() * vertex.normalX() + vertex.normalY() * vertex.normalY()
                + vertex.normalZ() * vertex.normalZ();
            assertEquals(1.0F, length, 0.0001F, "vertex " + index);
        }
    }

    private static void assertTorusNormals(EffectMesh mesh) {
        for (int index = 0; index < mesh.vertexCount(); index++) {
            EffectMesh.Vertex vertex = mesh.vertex(index);
            float angle = (float)Math.atan2(vertex.z(), vertex.x());
            float centerX = 0.75F * (float)Math.cos(angle);
            float centerZ = 0.75F * (float)Math.sin(angle);
            float dot = (vertex.x() - centerX) * vertex.normalX() + vertex.y() * vertex.normalY()
                + (vertex.z() - centerZ) * vertex.normalZ();
            assertTrue(dot > 0.0F, "vertex " + index);
        }
    }

    private static void assertOutwardNormals(EffectMesh mesh) {
        for (int index = 0; index < mesh.vertexCount(); index++) {
            EffectMesh.Vertex vertex = mesh.vertex(index);
            float dot = vertex.x() * vertex.normalX() + vertex.y() * vertex.normalY()
                + vertex.z() * vertex.normalZ();
            assertTrue(dot > 0.0F, "vertex " + index);
        }
    }

    private static void assertNoDegenerateQuads(EffectMesh mesh) {
        assertNoDegenerateQuads(mesh, false);
    }

    private static void assertNoDegenerateDomeQuads(EffectMesh mesh) {
        assertNoDegenerateQuads(mesh, true);
    }

    private static void assertNoDegenerateQuads(EffectMesh mesh, boolean skipFlatBase) {
        for (int index = 0; index < mesh.vertexCount(); index += 4) {
            if (skipFlatBase && isFlatBase(mesh, index)) {
                continue;
            }
            for (int first = 0; first < 4; first++) {
                for (int second = first + 1; second < 4; second++) {
                    EffectMesh.Vertex a = mesh.vertex(index + first);
                    EffectMesh.Vertex b = mesh.vertex(index + second);
                    assertFalse(samePosition(a, b), "degenerate quad at vertex " + index);
                }
            }
        }
    }

    private static boolean isFlatBase(EffectMesh mesh, int index) {
        for (int offset = 0; offset < 4; offset++) {
            if (mesh.vertex(index + offset).y() != 0.0F) {
                return false;
            }
        }
        return true;
    }

    private static boolean samePosition(EffectMesh.Vertex a, EffectMesh.Vertex b) {
        return a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
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
