package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import mafisher.quasar.testing.TestResourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

class ShaderProgramDiagnosticsTest {
    private static final ResourceLocation PROGRAM = id("test");
    private static final ResourceLocation STAGE = id("shaders/core/test.fsh");
    private static final ResourceLocation MODULE = id("shaders/include/module.glsl");

    @Test
    void resolvesImportsAndSourceIds() {
        ResourceManager resources = TestResourceManager.fromText(Map.of(
            MODULE, "const float X = 1.0;\n"
        ));
        String source = """
            #version 150
            #moj_import <quasar_test:module.glsl>
            void main() {}
            """;

        ShaderProgramDiagnostics.PreprocessedStage stage =
            ShaderProgramDiagnostics.preprocess(source, STAGE, PROGRAM, resources);

        assertEquals(List.of(MODULE), stage.imports());
        assertEquals(STAGE, stage.sources().get(0));
        assertEquals(MODULE, stage.sources().get(1));
        assertTrue(String.join("\n", stage.lines()).contains("const float X"));
    }

    @Test
    void dropsDuplicateImportsAndKeepsTheComment() {
        ResourceManager resources = TestResourceManager.fromText(Map.of(
            MODULE, "const float X = 1.0;\n"
        ));
        String source = """
            #version 150
            #moj_import <quasar_test:module.glsl>
            #moj_import <quasar_test:module.glsl>
            void main() {}
            """;

        ShaderProgramDiagnostics.PreprocessedStage stage =
            ShaderProgramDiagnostics.preprocess(source, STAGE, PROGRAM, resources);
        String preprocessed = String.join("\n", stage.lines());

        assertEquals(List.of(MODULE), stage.imports());
        assertTrue(preprocessed.contains("/*#moj_import <quasar_test:module.glsl>*/"));
    }

    @Test
    void reportsMissingImportsAsCompileErrors() {
        String source = """
            #version 150
            #moj_import <quasar_test:missing.glsl>
            void main() {}
            """;

        ShaderProgramDiagnostics.PreprocessedStage stage = ShaderProgramDiagnostics.preprocess(
            source, STAGE, PROGRAM, TestResourceManager.missing()
        );

        assertEquals(List.of(id("shaders/include/missing.glsl")), stage.imports());
        assertTrue(String.join("\n", stage.lines()).contains("#error"));
    }

    @Test
    void mapsSourceIdsBackToResourcePaths() {
        Map<Integer, ResourceLocation> sources = Map.of(
            0, STAGE,
            1, MODULE
        );

        String formatted = ShaderProgramDiagnostics.formatLog(
            "0(12): error: bad\n1(7): warning: included", sources
        );

        assertEquals("quasar_test:shaders/core/test.fsh:12: error: bad\n"
            + "quasar_test:shaders/include/module.glsl:7: warning: included", formatted);
    }

    @Test
    void summaryUsesTheFirstNonBlankLine() {
        assertEquals("error: bad", ShaderProgramDiagnostics.summary("\n error: bad\nmore"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("quasar_test", path);
    }
}
