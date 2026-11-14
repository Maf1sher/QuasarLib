package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Keeps the shared emissive composite shader on one standard contract. */
class EmissiveCompositeShaderTest {
    private static final String PROGRAM = "quasar_lib:emissive_composite";
    private static final String FRAGMENT = "quasar_lib:shaders/core/emissive_composite.fsh";

    @Test
    void declaresTheStandardCompositeSamplers() {
        ShaderDeclarationScanner.Declarations declarations = ShaderDeclarationScanner.scan(
            ShaderTestResources.preprocessedShader(FRAGMENT));
        Set<String> samplers = declarations.samplers().stream()
            .map(ShaderDeclarationScanner.Declaration::name)
            .collect(Collectors.toSet());

        assertEquals(Set.of(
            "SceneSampler",
            "EffectSampler",
            "EmissionSampler",
            "BloomSampler",
            "MaskSampler"
        ), samplers);
    }

    @Test
    void declaresTheCompositeUniformsAndMediumGroup() {
        ShaderDeclarationScanner.Declarations declarations = ShaderDeclarationScanner.scan(
            ShaderTestResources.preprocessedShader(FRAGMENT));
        Set<String> uniforms = declarations.uniforms().stream()
            .map(ShaderDeclarationScanner.Declaration::name)
            .collect(Collectors.toSet());

        assertTrue(uniforms.containsAll(Set.of(
            "Pass",
            "Threshold",
            "Intensity",
            "RadiusPixels",
            "Horizontal",
            "Solidity",
            "EmissionFog",
            "MaskStrength",
            "ViewportWidth",
            "ViewportHeight",
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
        )), () -> "Missing emissive composite uniforms: " + uniforms);
    }

    @Test
    void jsonAndGlslShareTheSameContract() {
        ShaderTestResources.ShaderProgramAsset program = ShaderTestResources.programs().stream()
            .filter(candidate -> candidate.programId().equals(PROGRAM))
            .findFirst()
            .orElseThrow();

        assertEquals(Set.of(
            "SceneSampler",
            "EffectSampler",
            "EmissionSampler",
            "BloomSampler",
            "MaskSampler"
        ), Set.copyOf(program.samplers()));
        assertEquals(Set.of("Position"), Set.copyOf(program.attributes()));
    }
}
