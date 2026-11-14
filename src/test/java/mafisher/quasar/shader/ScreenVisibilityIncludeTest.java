package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Keeps the shared screen-visibility include free of shader binding state. */
class ScreenVisibilityIncludeTest {
    private static final String INCLUDE =
        "quasar_lib:shaders/include/screen_visibility.glsl";

    @Test
    void declaresNoUniformsOrSamplers() {
        ShaderDeclarationScanner.Declarations declarations = ShaderDeclarationScanner.scan(
            ShaderTestResources.preprocessedShader(INCLUDE));

        assertTrue(declarations.uniforms().isEmpty(), "visibility include must not declare uniforms");
        assertTrue(declarations.samplers().isEmpty(), "visibility include must not declare samplers");
    }

    @Test
    void exposesTheSharedRangeFactor() {
        Map<String, Double> constants = GlslConstants.load(INCLUDE);

        assertTrue(GlslConstants.require(constants, "QUASAR_VISIBILITY_RANGE_FACTOR") > 0.0);
    }
}
