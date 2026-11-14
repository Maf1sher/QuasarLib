package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the shader-side contract of the content black-hole composite. */
class ContentBlackHoleShaderTest {
    private static final String FRAGMENT = "quasar_content:shaders/core/black_hole.fsh";
    private static final List<String> EFFECT_UNIFORMS = List.of(
        "ViewMat",
        "WorldProjMat",
        "InvProjMat",
        "InvViewMat",
        "ViewportWidth",
        "ViewportHeight",
        "EffectOrigin",
        "EffectRight",
        "EffectUp",
        "EffectForward",
        "AgeTicks",
        "Progress",
        "Seed",
        "Daylight"
    );

    @Test
    void everyMandatoryEffectUniformIsReferenced() {
        String source = ShaderDeclarationScanner.stripComments(
            ShaderTestResources.preprocessedShader(FRAGMENT));

        for (String uniform : EFFECT_UNIFORMS) {
            long occurrences = Pattern.compile("\\b" + uniform + "\\b")
                .matcher(source)
                .results()
                .count();
            assertTrue(occurrences > 1,
                () -> uniform + " is declared but never used; the GLSL compiler will optimize it out");
        }
    }
}
