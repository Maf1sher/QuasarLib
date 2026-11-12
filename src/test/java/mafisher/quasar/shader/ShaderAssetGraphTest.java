package mafisher.quasar.shader;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Keeps the shader asset graph reachable and free of orphaned sources. */
class ShaderAssetGraphTest {
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
    void everyIncludeIsImported() {
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
        assertTrue(includeIds.isEmpty(), () -> "Unreferenced shader includes: " + includeIds);
    }

    @Test
    void instancedVertexShaderDeclaresArrayUniforms() {
        ShaderDeclarationScanner.Declarations declarations = ShaderDeclarationScanner.scan(
            ShaderTestResources.preprocessedShader(
                "quasar_lib:shaders/core/instanced_visual.vsh"));
        Map<String, ShaderDeclarationScanner.Declaration> uniforms = declarations.uniforms().stream()
            .collect(Collectors.toMap(ShaderDeclarationScanner.Declaration::name,
                declaration -> declaration, (first, second) -> first));
        for (String name : List.of("Instances", "InstanceColors", "InstanceData")) {
            ShaderDeclarationScanner.Declaration declaration = uniforms.get(name);
            assertTrue(declaration != null && declaration.array(),
                () -> name + " must be declared as an array uniform");
        }
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
        List<String> duplicated = new ArrayList<>();
        Set<String> names = Set.of("quasarHash21", "quasarValueNoise", "quasarHash13",
            "quasarValueNoise3", "quasarFbm3");
        List<Path> sources = new ArrayList<>(ShaderTestResources.coreShaderSources());
        sources.addAll(ShaderTestResources.includeSources());
        for (Path path : sources) {
            String source = ShaderTestResources.readShaderSource(
                ShaderTestResources.resourceFor(path).id());
            for (String name : names) {
                if (!path.getFileName().toString().equals("noise.glsl")
                    && Pattern.compile("\\bfloat\\s+" + name + "\\s*\\(").matcher(source).find()) {
                    duplicated.add(name + " is defined in " + path);
                }
            }
        }
        assertTrue(duplicated.isEmpty(), () -> "Noise helpers must live only in noise.glsl:\n"
            + String.join("\n", duplicated));
    }

    @Test
    void blackHoleUsesTheOpticsInclude() {
        Set<String> imports = importsOf(ShaderTestResources.ShaderResource.parse(
            "quasar_content:shaders/core/black_hole.fsh"));
        assertTrue(imports.contains("quasar_content:shaders/include/black_hole_optics.glsl"),
            () -> "black_hole.fsh must import the shared optics constants: " + imports);
    }

    @Test
    void sharedNoiseIsImportedByItsUsers() {
        Set<String> users = Set.of(
            "quasar_lib:shaders/core/effect_visual.fsh",
            "quasar_lib:shaders/core/black_hole_visual.fsh",
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
