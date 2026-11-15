package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Keeps every shared shader module on one documented declaration contract. */
class ShaderModuleContractTest {
    private static final String INCLUDE_PREFIX = "quasar_lib:shaders/include/";
    private static final Pattern GUARD = Pattern.compile(
        "(?m)^#ifndef\\s+([A-Z0-9_]+)\\s*$");
    private static final Map<String, Set<String>> UNIFORMS = expectedUniforms();

    @Test
    void everyModuleMatchesItsUniformContract() {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : UNIFORMS.entrySet()) {
            ShaderDeclarationScanner.Declarations declarations =
                ShaderDeclarationScanner.scan(
                    ShaderTestResources.preprocessedShader(entry.getKey()));
            Set<String> actual = ShaderDeclarationScanner.Declarations.names(
                declarations.uniforms());
            if (!actual.equals(entry.getValue())) {
                failures.add(entry.getKey() + " declares " + actual
                    + " instead of " + entry.getValue());
            }
            if (!declarations.samplers().isEmpty()) {
                failures.add(entry.getKey() + " declares samplers: "
                    + ShaderDeclarationScanner.Declarations.names(declarations.samplers()));
            }
        }
        assertTrue(failures.isEmpty(), () -> "Shader module uniform contract failed:\n"
            + String.join("\n", failures));
    }

    @Test
    void everyIncludeHasAContract() {
        Set<String> discovered = ShaderTestResources.includeSources().stream()
            .map(path -> ShaderTestResources.resourceFor(path).id())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertTrue(discovered.equals(UNIFORMS.keySet()),
            () -> "Every shader include must have a module contract; discovered "
                + discovered + ", documented " + UNIFORMS.keySet());
    }

    @Test
    void modulesDoNotImportEachOther() {
        List<String> failures = new ArrayList<>();
        for (String module : UNIFORMS.keySet()) {
            if (!ShaderTestResources.imports(
                ShaderTestResources.readShaderSource(module)).isEmpty()) {
                failures.add(module + " must not nest #moj_import");
            }
        }
        assertTrue(failures.isEmpty(), () -> "Shader modules must stay self-contained:\n"
            + String.join("\n", failures));
    }

    @Test
    void everyModuleDeclaresAUniqueGuard() {
        Map<String, String> guardByModule = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (String module : UNIFORMS.keySet()) {
            Matcher matcher = GUARD.matcher(ShaderTestResources.readShaderSource(module));
            if (!matcher.find()) {
                failures.add(module + " has no include guard");
                continue;
            }
            String guard = matcher.group(1);
            if (!guard.startsWith("QUASAR_") || !guard.endsWith("_GLSL")) {
                failures.add(module + " guard '" + guard + "' is not a QUASAR_*_GLSL guard");
            }
            String previous = guardByModule.putIfAbsent(guard, module);
            if (previous != null) {
                failures.add(module + " reuses guard '" + guard + "' from " + previous);
            }
        }
        assertTrue(failures.isEmpty(), () -> "Shader module guards are invalid:\n"
            + String.join("\n", failures));
    }

    @Test
    void reconstructionAndSoftParticleConstantsAreFiniteAndPositive() {
        Map<String, Double> depth = GlslConstants.load(
            INCLUDE_PREFIX + "depth_reconstruction.glsl");
        Map<String, Double> soft = GlslConstants.load(
            INCLUDE_PREFIX + "soft_particles.glsl");

        assertTrue(GlslConstants.require(depth, "QUASAR_DEPTH_W_EPSILON") > 0.0);
        double minimum = GlslConstants.require(depth, "QUASAR_DEPTH_MIN_DISTANCE");
        double maximum = GlslConstants.require(depth, "QUASAR_DEPTH_MAX_DISTANCE");
        assertTrue(minimum > 0.0 && maximum > minimum);
        assertTrue(GlslConstants.require(soft, "QUASAR_SOFT_PARTICLE_MIN_SOFTNESS") > 0.0);
        assertTrue(GlslConstants.require(soft, "QUASAR_SOFT_PARTICLE_MIN_NEAR_RANGE") > 0.0);
    }

    private static Map<String, Set<String>> expectedUniforms() {
        Map<String, Set<String>> modules = new LinkedHashMap<>();
        modules.put(INCLUDE_PREFIX + "effect_space.glsl", Set.of(
            "EffectOrigin",
            "EffectRight",
            "EffectUp",
            "EffectForward"
        ));
        modules.put(INCLUDE_PREFIX + "screen_visibility.glsl", Set.of());
        modules.put(INCLUDE_PREFIX + "medium_optics.glsl", Set.of(
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
        ));
        modules.put(INCLUDE_PREFIX + "noise.glsl", Set.of());
        modules.put(INCLUDE_PREFIX + "instance_payload.glsl", Set.of());
        modules.put(INCLUDE_PREFIX + "depth_reconstruction.glsl", Set.of(
            "InvProjMat",
            "InvViewMat"
        ));
        modules.put(INCLUDE_PREFIX + "soft_particles.glsl", Set.of());
        modules.put(INCLUDE_PREFIX + "particle_field.glsl", Set.of());
        modules.put("quasar_content:shaders/include/black_hole_optics.glsl", Set.of());
        return modules;
    }
}
