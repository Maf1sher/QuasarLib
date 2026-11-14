package mafisher.quasar.client.effect.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectStaticMesh;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StaticMeshReaderTest {
    private static final float EPSILON = 1.0E-6F;
    private static final ResourceLocation OBJ = ResourceLocation.fromNamespaceAndPath(
        "quasar_test", "quasar/meshes/quad.obj"
    );
    private static final ResourceLocation JSON = ResourceLocation.fromNamespaceAndPath(
        "quasar_test", "quasar/meshes/quad.json"
    );

    @Test
    void readsQuadWithFlippedUvAndFaceNormal() {
        StaticMeshData data = readObj("""
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            vt 0 0
            vt 1 0
            vt 1 1
            vt 0 1
            f 1/1 2/2 3/3 4/4
            """);
        EffectMesh mesh = data.mesh();

        assertEquals(4, mesh.vertexCount());
        assertEquals(0.0F, mesh.vertex(0).u(), EPSILON);
        assertEquals(1.0F, mesh.vertex(0).v(), EPSILON);
        assertEquals(0.0F, mesh.vertex(0).normalX(), EPSILON);
        assertEquals(0.0F, mesh.vertex(0).normalY(), EPSILON);
        assertEquals(1.0F, mesh.vertex(0).normalZ(), EPSILON);
        assertEquals(0.0F, (float)data.localBounds().minX, EPSILON);
        assertEquals(1.0F, (float)data.localBounds().maxX, EPSILON);
    }

    @Test
    void readsTriangleAsDegenerateQuad() {
        EffectMesh mesh = readObj("""
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """).mesh();

        assertEquals(4, mesh.vertexCount());
        assertEquals(mesh.vertex(2).x(), mesh.vertex(3).x(), EPSILON);
        assertEquals(mesh.vertex(2).y(), mesh.vertex(3).y(), EPSILON);
        assertEquals(mesh.vertex(2).z(), mesh.vertex(3).z(), EPSILON);
        assertEquals(1.0F, mesh.vertex(0).normalZ(), EPSILON);
    }

    @Test
    void readsPolygonAsFan() {
        EffectMesh mesh = readObj("""
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            v -1 0.5 0
            f 1 2 3 4 5
            """).mesh();

        assertEquals(12, mesh.vertexCount());
    }

    @Test
    void readsNegativeAndMixedReferences() {
        EffectMesh mesh = readObj("""
            v 0 0 0
            v 1 0 0
            v 0 1 0
            vn 0 0 1
            f -3//1 -2//1 -1//1
            """).mesh();

        assertEquals(4, mesh.vertexCount());
        assertEquals(1.0F, mesh.vertex(0).normalZ(), EPSILON);
    }

    @Test
    void ignoresCommentsAndUnknownRecords() {
        EffectMesh mesh = readObj("""
            # comment
            o mushroom
            g body
            s off
            usemtl none
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """).mesh();

        assertEquals(4, mesh.vertexCount());
    }

    @Test
    void rejectsMalformedFaces() {
        assertThrows(IllegalArgumentException.class,
            () -> readObj("v 0 0 0\nv 1 0 0\nf 1 2\n"));
        assertThrows(IllegalArgumentException.class,
            () -> readObj("v 0 0 0\nf 1 2 3\n"));
        assertThrows(IllegalArgumentException.class,
            () -> readObj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 0 1 2\n"));
    }

    @Test
    void rejectsNonFiniteAndEmptyInput() {
        assertThrows(IllegalArgumentException.class,
            () -> readObj("v 0 0 NaN\nv 1 0 0\nv 0 1 0\nf 1 2 3\n"));
        assertThrows(IllegalArgumentException.class, () -> readObj("# empty\n"));
    }

    @Test
    void readsJsonQuadAndFaceNormal() {
        EffectMesh mesh = readJson("""
            {
              "format_version": 1,
              "quads": [[
                {"position": [0, 0, 0], "uv": [0, 1]},
                {"position": [1, 0, 0], "uv": [1, 1]},
                {"position": [1, 1, 0], "uv": [1, 0], "normal": [0, 0, 1]},
                {"position": [0, 1, 0], "uv": [0, 0], "alpha": 0.5}
              ]]
            }
            """).mesh();

        assertEquals(4, mesh.vertexCount());
        assertEquals(1.0F, mesh.vertex(0).v(), EPSILON);
        assertEquals(1.0F, mesh.vertex(1).normalZ(), EPSILON);
        assertEquals(0.5F, mesh.vertex(3).alphaMultiplier(), EPSILON);
    }

    @Test
    void rejectsInvalidJsonSchema() {
        assertThrows(IllegalArgumentException.class,
            () -> readJson("{\"format_version\": 2, \"quads\": []}"));
        assertThrows(IllegalArgumentException.class,
            () -> readJson("""
                {"format_version": 1, "quads": [[
                  {"position": [0, 0, 0]},
                  {"position": [1, 0, 0]},
                  {"position": [1, 1, 0]}
                ]]}
                """));
        assertThrows(IllegalArgumentException.class,
            () -> readJson("""
                {"format_version": 1, "unknown": true, "quads": [[
                  {"position": [0, 0, 0]},
                  {"position": [1, 0, 0]},
                  {"position": [1, 1, 0]},
                  {"position": [0, 1, 0]}
                ]]}
                """));
        assertThrows(IllegalArgumentException.class,
            () -> readJson("""
                {"format_version": 1, "quads": [[
                  {"position": [0, 0, 0], "positionx": [1, 0, 0]},
                  {"position": [1, 0, 0]},
                  {"position": [1, 1, 0]},
                  {"position": [0, 1, 0]}
                ]]}
                """));
        assertThrows(IllegalArgumentException.class,
            () -> readJson("{\"format_version\": 1, \"quads\": []}"));
    }

    @Test
    void enforcesVertexLimitBeforeBuilding() {
        assertThrows(IllegalArgumentException.class,
            () -> new StaticMeshBuilder(EffectStaticMesh.MAX_VERTICES / 4 + 1));
        assertThrows(IllegalArgumentException.class, () -> new StaticMeshBuilder(0));
    }

    @Test
    void normalizesSourceNormalsAndFallsBackForZero() {
        StaticMeshData normalized = readObj("""
            v 0 0 0
            v 1 0 0
            v 0 1 0
            vn 0 0 4
            f 1//1 2//1 3//1
            """);
        StaticMeshData zero = readObj("""
            v 0 0 0
            v 1 0 0
            v 0 1 0
            vn 0 0 0
            f 1//1 2//1 3//1
            """);

        assertEquals(1.0F, normalized.mesh().vertex(0).normalZ(), EPSILON);
        assertEquals(1.0F, zero.mesh().vertex(0).normalZ(), EPSILON);
        assertTrue(normalized.localBounds().maxY >= 1.0);
    }

    private static StaticMeshData readObj(String text) {
        try {
            return ObjStaticMeshReader.read(OBJ, new BufferedReader(new StringReader(text)));
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static StaticMeshData readJson(String text) {
        return JsonStaticMeshReader.read(JSON, new BufferedReader(new StringReader(text)));
    }
}
