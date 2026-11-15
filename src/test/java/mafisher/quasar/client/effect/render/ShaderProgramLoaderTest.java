package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import mafisher.quasar.testing.TestResourceManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ShaderProgramLoaderTest {
    private static final ResourceLocation PROGRAM = id("test_program");
    private static final ResourceLocation JSON = id("shaders/core/test_program.json");
    private static final ResourceLocation VERTEX = id("shaders/core/test_vertex.vsh");
    private static final ResourceLocation FRAGMENT = id("shaders/core/test_fragment.fsh");

    private static final String VALID_JSON = """
        {
          "vertex": "quasar_test:test_vertex",
          "fragment": "quasar_test:test_fragment",
          "samplers": [{"name": "Sampler0"}],
          "uniforms": [{"name": "Value", "type": "float", "count": 1, "values": [0.0]}]
        }
        """;

    @Test
    void loadsStagesAndContract() throws Exception {
        ShaderProgramSource source = ShaderProgramLoader.load(
            TestResourceManager.fromText(program(VALID_JSON)), PROGRAM
        );

        assertEquals(PROGRAM, source.programId());
        assertEquals(JSON, source.json().location());
        assertEquals(VERTEX, source.vertex().location());
        assertEquals(FRAGMENT, source.fragment().location());
        assertTrue(source.vertex().text().contains("void main"));
        assertTrue(source.fragment().text().contains("void main"));
    }

    @Test
    void rejectsMissingStageField() {
        String json = """
            {"fragment": "quasar_test:test_fragment"}
            """;
        ShaderProgramLoader.LoadException exception = assertThrows(
            ShaderProgramLoader.LoadException.class,
            () -> ShaderProgramLoader.load(TestResourceManager.fromText(program(json)), PROGRAM)
        );

        assertEquals("json", exception.stage());
        assertEquals(JSON, exception.resource());
    }

    @Test
    void rejectsMissingStageSource() {
        ShaderProgramLoader.LoadException exception = assertThrows(
            ShaderProgramLoader.LoadException.class,
            () -> ShaderProgramLoader.load(TestResourceManager.fromText(Map.of(
                JSON, VALID_JSON
            )), PROGRAM)
        );

        assertEquals("vertex", exception.stage());
        assertEquals(VERTEX, exception.resource());
    }

    @Test
    void rejectsUnknownUniformType() {
        String json = """
            {
              "vertex": "quasar_test:test_vertex",
              "fragment": "quasar_test:test_fragment",
              "uniforms": [{"name": "Value", "type": "bogus", "count": 1, "values": [0.0]}]
            }
            """;
        ShaderProgramLoader.LoadException exception = assertThrows(
            ShaderProgramLoader.LoadException.class,
            () -> ShaderProgramLoader.load(TestResourceManager.fromText(program(json)), PROGRAM)
        );

        assertEquals("json", exception.stage());
        assertTrue(exception.getMessage().contains("uniforms[0]"));
    }

    @Test
    void rejectsUniformValueCountMismatch() {
        String json = """
            {
              "vertex": "quasar_test:test_vertex",
              "fragment": "quasar_test:test_fragment",
              "uniforms": [{"name": "Value", "type": "float", "count": 2, "values": [0.0, 1.0, 2.0]}]
            }
            """;
        ShaderProgramLoader.LoadException exception = assertThrows(
            ShaderProgramLoader.LoadException.class,
            () -> ShaderProgramLoader.load(TestResourceManager.fromText(program(json)), PROGRAM)
        );

        assertEquals("json", exception.stage());
        assertTrue(exception.getMessage().contains("uniforms[0]"));
    }

    @Test
    void rejectsMalformedSampler() {
        String json = """
            {
              "vertex": "quasar_test:test_vertex",
              "fragment": "quasar_test:test_fragment",
              "samplers": [{"file": "texture.png"}]
            }
            """;
        ShaderProgramLoader.LoadException exception = assertThrows(
            ShaderProgramLoader.LoadException.class,
            () -> ShaderProgramLoader.load(TestResourceManager.fromText(program(json)), PROGRAM)
        );

        assertEquals("json", exception.stage());
        assertTrue(exception.getMessage().contains("samplers[0]"));
    }

    private static Map<ResourceLocation, String> program(String json) {
        Map<ResourceLocation, String> assets = new HashMap<>();
        assets.put(JSON, json);
        assets.put(VERTEX, "#version 150\nvoid main() {}\n");
        assets.put(FRAGMENT, "#version 150\nvoid main() {}\n");
        return assets;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("quasar_test", path);
    }
}
