package mafisher.quasar.shader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Classpath access to the shipped shader programs, core stages, and includes. */
final class ShaderTestResources {
    private static final String ASSETS = "assets";
    private static final String SHADERS = "shaders";

    private ShaderTestResources() {
    }

    static Path assetsRoot() {
        String configured = System.getProperty("quasar.shader.sourceRoot");
        if (configured != null && !configured.isBlank()) {
            Path configuredAssets = Path.of(configured).resolve(ASSETS);
            if (Files.isDirectory(configuredAssets)) {
                return configuredAssets;
            }
        }
        Path projectAssets = Path.of("src/main/resources").resolve(ASSETS);
        if (Files.isDirectory(projectAssets)) {
            return projectAssets;
        }
        URL url = ShaderTestResources.class.getClassLoader().getResource(ASSETS);
        if (url == null) {
            throw new IllegalStateException("Missing test resource root: " + ASSETS);
        }
        if (!"file".equals(url.getProtocol())) {
            throw new IllegalStateException("Shader tests require an exploded resource directory, got " + url);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid assets URI: " + url, e);
        }
    }

    static List<ShaderProgramAsset> programs() {
        Path root = assetsRoot();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .filter(path -> normalized(path).contains("/" + SHADERS + "/core/"))
                .sorted(Comparator.comparing(Path::toString))
                .map(ShaderTestResources::parseProgram)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not discover shader programs", e);
        }
    }

    static List<Path> coreShaderSources() {
        return sourcesUnder("core", List.of(".vsh", ".fsh"));
    }

    static List<Path> includeSources() {
        return sourcesUnder("include", List.of(".glsl"));
    }

    static String readShaderSource(String resourceId) {
        ShaderResource resource = ShaderResource.parse(resourceId);
        return readText(resource.namespace(), resource.path());
    }

    static String preprocessedShader(String resourceId) {
        return new ShaderTestPreprocessor().processSource(readShaderSource(resourceId));
    }

    static String stageSourceId(String reference, String extension) {
        ShaderResource resource = ShaderResource.parse(reference);
        String path = resource.path();
        if (!path.startsWith(SHADERS + "/")) {
            path = SHADERS + "/core/" + path;
        }
        if (!path.endsWith("." + extension)) {
            path = path + "." + extension;
        }
        return new ShaderResource(resource.namespace(), path).id();
    }

    static String readText(String namespace, String path) {
        Path local = assetsRoot().resolve(namespace).resolve(path);
        if (Files.isRegularFile(local)) {
            try {
                return Files.readString(local);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read shader test resource: " + local, e);
            }
        }
        String fullPath = ASSETS + "/" + namespace + "/" + path;
        try (InputStream stream = ShaderTestResources.class.getClassLoader()
            .getResourceAsStream(fullPath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing shader test resource: " + fullPath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read shader test resource: " + fullPath, e);
        }
    }

    static ShaderResource resourceFor(Path path) {
        Path relative = assetsRoot().relativize(path);
        String namespace = relative.getName(0).toString();
        String resourcePath = relative.subpath(1, relative.getNameCount()).toString()
            .replace('\\', '/');
        return new ShaderResource(namespace, resourcePath);
    }

    static ShaderResource resolveImport(String importerNamespace, String importerPath,
        String importPath, boolean relative) {
        if (relative) {
            Path parent = Path.of(importerPath).getParent();
            Path resolved = (parent == null ? Path.of(importPath) : parent.resolve(importPath))
                .normalize();
            return new ShaderResource(importerNamespace, resolved.toString().replace('\\', '/'));
        }
        ShaderResource imported = importPath.indexOf(':') >= 0
            ? ShaderResource.parse(importPath)
            : new ShaderResource("minecraft", importPath);
        return new ShaderResource(imported.namespace(), SHADERS + "/include/" + imported.path());
    }

    static List<ImportSpec> imports(String source) {
        List<ImportSpec> imports = new ArrayList<>();
        int index = 0;
        while ((index = source.indexOf("#moj_import", index)) >= 0) {
            int start = source.indexOf('<', index);
            int quote = source.indexOf('"', index);
            boolean angle = start >= 0 && (quote < 0 || start < quote);
            int open = angle ? start : quote;
            if (open < 0) {
                index += "#moj_import".length();
                continue;
            }
            char close = angle ? '>' : '"';
            int end = source.indexOf(close, open + 1);
            if (end < 0) {
                index = open + 1;
                continue;
            }
            imports.add(new ImportSpec(source.substring(open + 1, end), !angle));
            index = end + 1;
        }
        return imports;
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static List<Path> sourcesUnder(String directory, List<String> extensions) {
        Path root = assetsRoot();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> normalized(path).contains("/" + SHADERS + "/" + directory + "/"))
                .filter(path -> extensions.stream()
                    .anyMatch(extension -> path.getFileName().toString().endsWith(extension)))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not discover shader sources", e);
        }
    }

    private static ShaderProgramAsset parseProgram(Path path) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            ShaderResource resource = resourceFor(path);
            String name = path.getFileName().toString()
                .substring(0, path.getFileName().toString().length() - ".json".length());
            return new ShaderProgramAsset(
                resource.namespace(),
                name,
                resource.namespace() + ":" + name,
                requiredString(json, "vertex"),
                requiredString(json, "fragment"),
                objectNames(json, "samplers", "name"),
                objectNames(json, "uniforms", "name"),
                stringValues(json, "attributes"),
                path
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Could not parse shader program " + path, e);
        }
    }

    private static String requiredString(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Shader JSON is missing string '" + name + "'");
        }
        return element.getAsString();
    }

    private static List<String> objectNames(JsonObject json, String arrayName, String key) {
        JsonElement element = json.get(arrayName);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        List<String> names = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            names.add(requiredString(item.getAsJsonObject(), key));
        }
        return List.copyOf(names);
    }

    private static List<String> stringValues(JsonObject json, String arrayName) {
        JsonElement element = json.get(arrayName);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            values.add(item.getAsString());
        }
        return List.copyOf(values);
    }

    record ImportSpec(String path, boolean relative) {
    }

    record ShaderResource(String namespace, String path) {
        static ShaderResource parse(String resourceId) {
            int separator = resourceId.indexOf(':');
            if (separator <= 0 || separator == resourceId.length() - 1) {
                throw new IllegalArgumentException("Invalid shader resource id: " + resourceId);
            }
            return new ShaderResource(resourceId.substring(0, separator),
                resourceId.substring(separator + 1));
        }

        String id() {
            return this.namespace + ":" + this.path;
        }
    }

    record ShaderProgramAsset(String namespace, String name, String programId,
        String vertex, String fragment, List<String> samplers, List<String> uniforms,
        List<String> attributes, Path jsonPath) {
        ShaderProgramAsset {
            samplers = List.copyOf(samplers);
            uniforms = List.copyOf(uniforms);
            attributes = List.copyOf(attributes);
        }

        String vertexSourceId() {
            return ShaderTestResources.stageSourceId(this.vertex, "vsh");
        }

        String fragmentSourceId() {
            return ShaderTestResources.stageSourceId(this.fragment, "fsh");
        }
    }
}
