package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Keeps the shader asset graph reachable and free of orphaned sources. */
class ShaderAssetGraphTest {
    /** Public modules that no shipped program imports yet; 4.10 adopts soft particles. */
    private static final Set<String> LIBRARY_ONLY_INCLUDES = Set.of(
        "quasar_lib:shaders/include/soft_particles.glsl"
    );

    @Test
    void everyProgramReferencesExistingStages() {
        List<String> failures = new ArrayList<>();
        for (ShaderTestResources.ShaderProgramAsset program : ShaderTestResources.programs()) {
            requireStage(program, program.vertexSourceId(), "vertex", failures);
            requireStage(program, program.fragmentSourceId(), "fragment", failures);
        }
        assertTrue(failures.isEmpty(), () -> "Shader programs reference missing stages:\n\n"
            + String.join("\n\n", failures));
    }

    @Test
    void everyCoreSourceIsReachable() {
        Set<String> referenced = new LinkedHashSet<>();
        for (ShaderTestResources.ShaderProgramAsset program : ShaderTestResources.programs()) {
            referenced.add(program.vertexSourceId());
            referenced.add(program.fragmentSourceId());
        }
        Set<String> sources = ShaderTestResources.coreShaderSources().stream()
            .map(path -> ShaderTestResources.resourceFor(path).id())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        sources.removeAll(referenced);

        assertTrue(sources.isEmpty(), () -> "Unreferenced shader sources: " + sources);
    }

    @Test
    void everyIncludeIsImportedOrDeclaredLibraryOnly() {
        Set<String> includeIds = ShaderTestResources.includeSources().stream()
            .map(path -> ShaderTestResources.resourceFor(path).id())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> visited = new LinkedHashSet<>();
        Deque<ShaderTestResources.ShaderResource> queue = new ArrayDeque<>();

        for (Path path : ShaderTestResources.coreShaderSources()) {
            queue.add(ShaderTestResources.resourceFor(path));
        }
        while (!queue.isEmpty()) {
            ShaderTestResources.ShaderResource resource = queue.removeFirst();
            String source = ShaderTestResources.readShaderSource(resource.id());
            for (ShaderTestResources.ImportSpec imported : ShaderTestResources.imports(source)) {
                ShaderTestResources.ShaderResource resolved = ShaderTestResources.resolveImport(
                    resource.namespace(), resource.path(), imported.path(), imported.relative());
                if (includeIds.contains(resolved.id()) && visited.add(resolved.id())) {
                    queue.add(resolved);
                }
            }
        }

        includeIds.removeAll(visited);
        includeIds.removeAll(LIBRARY_ONLY_INCLUDES);
        assertTrue(includeIds.isEmpty(), () -> "Unreferenced shader includes: " + includeIds);
    }

    @Test
    void libraryOnlyIncludesExist() {
        Set<String> discovered = ShaderTestResources.includeSources().stream()
            .map(path -> ShaderTestResources.resourceFor(path).id())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertTrue(discovered.containsAll(LIBRARY_ONLY_INCLUDES),
            () -> "Library-only includes are missing: " + LIBRARY_ONLY_INCLUDES);
    }

    @Test
    void instancedVertexShaderUsesTheInstancePayloadModule() {
        ShaderDeclarationScanner.Declarations declarations = ShaderDeclarationScanner.scan(
            ShaderTestResources.preprocessedShader(
                "quasar_lib:shaders/core/instanced_visual.vsh"));
        Set<String> samplers = ShaderDeclarationScanner.Declarations.names(declarations.samplers());
        assertTrue(samplers.contains("InstanceBuffer"),
            () -> "InstanceBuffer must be declared as a samplerBuffer: " + samplers);
        Set<String> imports = importsOf(ShaderTestResources.ShaderResource.parse(
            "quasar_lib:shaders/core/instanced_visual.vsh"));
        assertTrue(imports.contains("quasar_lib:shaders/include/instance_payload.glsl"),
            () -> "instanced_visual.vsh must import the instance payload module: " + imports);
    }

    @Test
    void noDuplicateShaderSources() {
        Map<String, Path> normalized = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        List<Path> sources = new ArrayList<>(ShaderTestResources.coreShaderSources());
        sources.addAll(ShaderTestResources.includeSources());
        for (Path path : sources) {
            ShaderTestResources.ShaderResource resource = ShaderTestResources.resourceFor(path);
            String source = normalize(ShaderTestResources.readShaderSource(resource.id()));
            Path previous = normalized.putIfAbsent(source, path);
            if (previous != null) {
                duplicates.add(previous + " duplicates " + path);
            }
        }
        assertTrue(duplicates.isEmpty(), () -> "Duplicate shader sources:\n"
            + String.join("\n", duplicates));
    }

    @Test
    void sharedNoiseIsDefinedOnlyInTheInclude() {
        assertDefinitionsOnlyIn("noise.glsl", "float", Set.of(
            "quasarHash21", "quasarValueNoise", "quasarHash13",
            "quasarValueNoise3", "quasarFbm3"));
    }

    @Test
    void blackHoleUsesTheOpticsInclude() {
        Set<String> imports = importsOf(ShaderTestResources.ShaderResource.parse(
            "quasar_content:shaders/core/black_hole.fsh"));
        assertTrue(imports.contains("quasar_content:shaders/include/black_hole_optics.glsl"),
            () -> "black_hole.fsh must import the shared optics constants: " + imports);
    }

    @Test
    void blackHoleUsesTheEffectSpaceInclude() {
        Set<String> imports = importsOf(ShaderTestResources.ShaderResource.parse(
            "quasar_content:shaders/core/black_hole.fsh"));
        assertTrue(imports.contains("quasar_lib:shaders/include/effect_space.glsl"),
            () -> "black_hole.fsh must import the shared effect-space frame: " + imports);
    }

    @Test
    void blackHoleUsesTheMediumOpticsInclude() {
        Set<String> imports = importsOf(ShaderTestResources.ShaderResource.parse(
            "quasar_content:shaders/core/black_hole.fsh"));
        assertTrue(imports.contains("quasar_lib:shaders/include/medium_optics.glsl"),
            () -> "black_hole.fsh must import the shared medium optics: " + imports);
    }

    @Test
    void blackHoleUsesTheDepthReconstructionInclude() {
        Set<String> imports = importsOf(ShaderTestResources.ShaderResource.parse(
            "quasar_content:shaders/core/black_hole.fsh"));
        assertTrue(imports.contains("quasar_lib:shaders/include/depth_reconstruction.glsl"),
            () -> "black_hole.fsh must import the shared depth reconstruction: " + imports);
    }

    @Test
    void blackHoleHasNoLocalCameraRayOrDepthHelpers() {
        String source = ShaderTestResources.readShaderSource(
            "quasar_content:shaders/core/black_hole.fsh");
        Pattern localHelper = Pattern.compile(
            "\\b(?:float|vec3)\\s+(?:viewDistance|rayDirection)\\s*\\(");

        assertFalse(localHelper.matcher(source).find(),
            "black_hole.fsh must use the shared depth reconstruction module");
    }

    @Test
    void sharedDepthReconstructionIsDefinedOnlyInTheInclude() {
        assertDefinitionsOnlyIn("depth_reconstruction.glsl", "float|vec3", Set.of(
            "quasarWorldRay", "quasarViewDistance"));
    }

    @Test
    void sharedSoftParticlesAreDefinedOnlyInTheInclude() {
        assertDefinitionsOnlyIn("soft_particles.glsl", "float", Set.of(
            "quasarSoftParticleFade", "quasarParticleNearFade"));
    }

    @Test
    void sharedMediumOpticsAreDefinedOnlyInTheInclude() {
        assertDefinitionsOnlyIn("medium_optics.glsl",
            "float|vec3|bool|QuasarMediumRay", Set.of(
                "quasarMediumFresnel",
                "quasarMediumFromCameraFresnel",
                "quasarMediumSurfaceTransmittance",
                "quasarMediumRay",
                "quasarMediumRefract",
                "quasarMediumFogDistance",
                "quasarMediumFogFactor",
                "quasarMediumFog",
                "quasarMediumFogTransmission",
                "quasarMediumWarpAllowed"
            ));
    }

    @Test
    void sharedNoiseIsImportedByItsUsers() {
        Set<String> users = Set.of(
            "quasar_lib:shaders/core/effect_visual.fsh",
            "quasar_content:shaders/core/black_hole_mesh.fsh",
            "quasar_content:shaders/core/black_hole.fsh"
        );
        List<String> missing = new ArrayList<>();
        for (String user : users) {
            Set<String> imports = importsOf(ShaderTestResources.ShaderResource.parse(user));
            if (!imports.contains("quasar_lib:shaders/include/noise.glsl")) {
                missing.add(user);
            }
        }
        assertTrue(missing.isEmpty(), () -> "Shader sources do not import noise.glsl: " + missing);
    }

    private static void assertDefinitionsOnlyIn(String includeFile, String returnTypes,
        Set<String> names) {
        String alternatives = names.stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
        Pattern definition = Pattern.compile(
            "\\b(?:" + returnTypes + ")\\s+(" + alternatives + ")\\s*\\(");
        List<String> duplicated = new ArrayList<>();
        List<Path> sources = new ArrayList<>(ShaderTestResources.coreShaderSources());
        sources.addAll(ShaderTestResources.includeSources());
        for (Path path : sources) {
            String source = ShaderTestResources.readShaderSource(
                ShaderTestResources.resourceFor(path).id());
            Matcher matcher = definition.matcher(source);
            while (matcher.find()) {
                if (!path.getFileName().toString().equals(includeFile)) {
                    duplicated.add(matcher.group(1) + " is defined in " + path);
                }
            }
        }
        assertTrue(duplicated.isEmpty(), () -> "Shared helpers must live only in "
            + includeFile + ":\n" + String.join("\n", duplicated));
    }

    private static Set<String> importsOf(ShaderTestResources.ShaderResource resource) {
        Set<String> imports = new LinkedHashSet<>();
        Deque<ShaderTestResources.ShaderResource> queue = new ArrayDeque<>();
        queue.add(resource);
        while (!queue.isEmpty()) {
            ShaderTestResources.ShaderResource current = queue.removeFirst();
            String source = ShaderTestResources.readShaderSource(current.id());
            for (ShaderTestResources.ImportSpec imported : ShaderTestResources.imports(source)) {
                ShaderTestResources.ShaderResource resolved = ShaderTestResources.resolveImport(
                    current.namespace(), current.path(), imported.path(), imported.relative());
                if (imports.add(resolved.id())) {
                    queue.add(resolved);
                }
            }
        }
        return imports;
    }

    private static String normalize(String source) {
        return ShaderDeclarationScanner.stripComments(source).replaceAll("\\s+", " ").trim();
    }

    private static void requireStage(ShaderTestResources.ShaderProgramAsset program,
        String resourceId, String kind, List<String> failures) {
        try {
            ShaderTestResources.readShaderSource(resourceId);
        } catch (RuntimeException e) {
            failures.add(program.programId() + " " + kind + " -> " + resourceId + ": "
                + e.getMessage());
        }
    }

}
