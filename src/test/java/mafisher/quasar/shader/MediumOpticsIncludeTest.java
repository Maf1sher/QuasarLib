package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Keeps the shared medium optics include on one standard uniform contract. */
class MediumOpticsIncludeTest {
    private static final String INCLUDE =
        "quasar_lib:shaders/include/medium_optics.glsl";

    @Test
    void declaresTheStandardMediumUniformGroupAndNoSamplers() {
        ShaderDeclarationScanner.Declarations declarations = ShaderDeclarationScanner.scan(
            ShaderTestResources.preprocessedShader(INCLUDE));
        Set<String> uniforms = declarations.uniforms().stream()
            .map(ShaderDeclarationScanner.Declaration::name)
            .collect(Collectors.toSet());

        assertEquals(Set.of(
            "MediumSurfaceActive",
            "MediumSurfaceY",
            "MediumWaterIor",
            "MediumSuppressAboveWater",
            "MediumPathLength",
            "MediumCameraSubmerged",
            "MediumFogStart",
            "MediumFogEnd",
            "MediumFogColor",
            "MediumFogActive"
        ), uniforms);
        assertTrue(declarations.samplers().isEmpty(), "medium optics must not declare samplers");
    }

    @Test
    void exposesFiniteOpticsConstants() {
        Map<String, Double> constants = GlslConstants.load(INCLUDE);

        assertTrue(GlslConstants.require(constants, "QUASAR_MEDIUM_WATER_IOR") > 1.0);
        assertTrue(GlslConstants.require(constants, "QUASAR_MEDIUM_FRESNEL_FLOOR") > 0.0);
        assertTrue(GlslConstants.require(constants, "QUASAR_MEDIUM_DIRECTION_EPSILON") > 0.0);
        assertTrue(GlslConstants.require(constants, "QUASAR_MEDIUM_FOG_RANGE_FLOOR") > 0.0);
    }
}
