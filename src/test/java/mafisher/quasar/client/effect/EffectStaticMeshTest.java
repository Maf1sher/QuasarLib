package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectStaticMesh;
import mafisher.quasar.testing.TestResourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

class EffectStaticMeshTest {
    private static final ResourceLocation OBJ = ResourceLocation.fromNamespaceAndPath(
        "quasar_test", "quasar/meshes/quad.obj"
    );
    private static final ResourceLocation JSON = ResourceLocation.fromNamespaceAndPath(
        "quasar_test", "quasar/meshes/quad.json"
    );
    private static final String OBJ_TEXT = """
        v 0 0 0
        v 1 0 0
        v 1 1 0
        v 0 1 0
        f 1 2 3 4
        """;
    private static final String JSON_TEXT = """
        {
          "format_version": 1,
          "quads": [[
            {"position": [0, 0, 0]},
            {"position": [2, 0, 0]},
            {"position": [2, 2, 0]},
            {"position": [0, 2, 0]}
          ]]
        }
        """;

    @Test
    void loadsObjAndJsonAssets() {
        EffectStaticMesh obj = EffectStaticMesh.of(OBJ);
        assertTrue(obj.reload(TestResourceManager.fromText(OBJ, OBJ_TEXT)));
        assertTrue(obj.loaded());
        assertEquals(OBJ, obj.location());
        assertNotNull(obj.mesh());
        assertNotNull(obj.localBounds());

        EffectStaticMesh json = EffectStaticMesh.of(JSON);
        assertTrue(json.reload(TestResourceManager.fromText(JSON, JSON_TEXT)));
        assertEquals(4, json.mesh().vertexCount());
        assertEquals(2.0F, (float)json.localBounds().maxX, 1.0E-6F);
    }

    @Test
    void keepsTheLastValidMeshAfterAFailedReload() {
        EffectStaticMesh mesh = EffectStaticMesh.of(OBJ);
        ResourceManager valid = TestResourceManager.fromText(OBJ, OBJ_TEXT);
        assertTrue(mesh.reload(valid));
        EffectMesh first = mesh.mesh();

        assertFalse(mesh.reload(TestResourceManager.missing()));
        assertSame(first, mesh.mesh());

        assertFalse(mesh.reload(TestResourceManager.fromText(OBJ,
            "v 0 0 0\nv 1 0 0\nf 1 2\n")));
        assertSame(first, mesh.mesh());
    }

    @Test
    void swapsTheMeshAfterASuccessfulReload() {
        EffectStaticMesh mesh = EffectStaticMesh.of(JSON);
        assertTrue(mesh.reload(TestResourceManager.fromText(JSON, JSON_TEXT)));
        EffectMesh first = mesh.mesh();

        String replacement = JSON_TEXT.replace("[2, 2, 0]", "[3, 2, 0]");
        assertTrue(mesh.reload(TestResourceManager.fromText(JSON, replacement)));

        assertNotNull(mesh.mesh());
        assertEquals(3.0F, (float)mesh.localBounds().maxX, 1.0E-6F);
    }

    @Test
    void failedFirstLoadDoesNotThrowOrRetryFromMesh() {
        ResourceManager manager = TestResourceManager.missing();
        EffectStaticMesh mesh = EffectStaticMesh.of(OBJ);

        assertFalse(mesh.reload(manager));
        assertFalse(mesh.loaded());
        assertNull(mesh.mesh());
        assertNull(mesh.localBounds());
        assertNull(mesh.mesh());

        verify(manager, times(1)).getResource(OBJ);
    }

    @Test
    void rejectsUnknownExtensions() {
        ResourceLocation invalid = ResourceLocation.fromNamespaceAndPath(
            "quasar_test", "quasar/meshes/quad.txt"
        );

        assertThrows(IllegalArgumentException.class, () -> EffectStaticMesh.of(invalid));
    }
}
