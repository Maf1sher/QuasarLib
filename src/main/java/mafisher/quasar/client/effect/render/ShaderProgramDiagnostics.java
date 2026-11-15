package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.neoforge.client.ClientHooks;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles and links shader stages without touching the vanilla stage cache or
 * any live {@code ShaderInstance}. This is the diagnostic half of the preflight
 * pass; the real load is still done by {@code ShaderInstance}.
 */
final class ShaderProgramDiagnostics {
    private static final int MAX_LOG_LENGTH = 32_768;
    private static final int MAX_SUMMARY_LENGTH = 240;
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProgramDiagnostics.class);

    private ShaderProgramDiagnostics() {
    }

    static void validate(ShaderProgramSource source, ResourceProvider resources,
        VertexFormat format, List<ShaderFailure> failures) {
        PreprocessedStage vertex;
        PreprocessedStage fragment;
        try {
            vertex = preprocess(source.vertex().text(), source.vertex().location(),
                source.programId(), resources);
            fragment = preprocess(source.fragment().text(), source.fragment().location(),
                source.programId(), resources);
        } catch (RuntimeException exception) {
            failures.add(new ShaderFailure(source.programId(), "preprocess",
                source.json().location().toString(), source.json().pack(), List.of(),
                summary(exception.toString()), exception.toString()));
            return;
        }
        int vertexId = compile(vertex, GL20.GL_VERTEX_SHADER, source, "vertex",
            source.vertex().location(), source.vertex().pack(), failures);
        int fragmentId = compile(fragment, GL20.GL_FRAGMENT_SHADER, source, "fragment",
            source.fragment().location(), source.fragment().pack(), failures);
        try {
            if (vertexId != 0 && fragmentId != 0) {
                link(source, format, vertex, fragment, vertexId, fragmentId, failures);
            }
        } finally {
            if (vertexId != 0) {
                GlStateManager.glDeleteShader(vertexId);
            }
            if (fragmentId != 0) {
                GlStateManager.glDeleteShader(fragmentId);
            }
        }
    }

    static PreprocessedStage preprocess(String source, ResourceLocation stage,
        ResourceLocation programId, ResourceProvider resources) {
        ImportPreprocessor preprocessor = new ImportPreprocessor(programId, stage, resources);
        List<String> lines = preprocessor.process(source);
        return new PreprocessedStage(List.copyOf(lines), List.copyOf(preprocessor.imports()),
            Map.copyOf(preprocessor.sources()));
    }

    private static int compile(PreprocessedStage stage, int type, ShaderProgramSource source,
        String stageName, ResourceLocation location, String pack, List<ShaderFailure> failures) {
        int shader = GlStateManager.glCreateShader(type);
        if (shader == 0) {
            failures.add(new ShaderFailure(source.programId(), stageName, location.toString(), pack,
                imports(stage), "could not create a shader object", "could not create a shader object"));
            return 0;
        }
        try {
            GlStateManager.glShaderSource(shader, stage.lines());
            GlStateManager.glCompileShader(shader);
            if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0) {
                return shader;
            }
            String detail = formatLog(GlStateManager.glGetShaderInfoLog(shader, MAX_LOG_LENGTH),
                stage.sources());
            failures.add(new ShaderFailure(source.programId(), stageName, location.toString(), pack,
                imports(stage), summary(detail), detail));
        } catch (RuntimeException exception) {
            failures.add(new ShaderFailure(source.programId(), stageName, location.toString(), pack,
                imports(stage), summary(exception.toString()), exception.toString()));
        }
        GlStateManager.glDeleteShader(shader);
        return 0;
    }

    private static void link(ShaderProgramSource source, VertexFormat format,
        PreprocessedStage vertex, PreprocessedStage fragment, int vertexId, int fragmentId,
        List<ShaderFailure> failures) {
        int program = GlStateManager.glCreateProgram();
        if (program == 0) {
            failures.add(new ShaderFailure(source.programId(), "link",
                source.json().location().toString(), source.json().pack(), List.of(),
                "could not create a shader program", "could not create a shader program"));
            return;
        }
        try {
            List<String> attributes = format.getElementAttributeNames();
            for (int index = 0; index < attributes.size(); index++) {
                Uniform.glBindAttribLocation(program, index, attributes.get(index));
            }
            GlStateManager.glAttachShader(program, vertexId);
            GlStateManager.glAttachShader(program, fragmentId);
            GlStateManager.glLinkProgram(program);
            if (GlStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                String detail = formatLog(GlStateManager.glGetProgramInfoLog(program, MAX_LOG_LENGTH),
                    Map.of());
                List<String> imports = new ArrayList<>(imports(vertex));
                imports.addAll(imports(fragment));
                failures.add(new ShaderFailure(source.programId(), "link",
                    source.vertex().location() + " + " + source.fragment().location(),
                    source.json().pack(),
                    imports, summary(detail), detail));
            }
        } catch (RuntimeException exception) {
            failures.add(new ShaderFailure(source.programId(), "link",
                source.json().location().toString(), source.json().pack(), List.of(),
                summary(exception.toString()), exception.toString()));
        } finally {
            GlStateManager.glDeleteProgram(program);
        }
    }

    private static List<String> imports(PreprocessedStage stage) {
        return stage.imports().stream().map(ResourceLocation::toString).toList();
    }

    /** Maps {@code #line} source identifiers in a driver log back to resource paths. */
    static String formatLog(String log, Map<Integer, ResourceLocation> sources) {
        if (log == null || log.isBlank()) {
            return "";
        }
        return Pattern.compile("(?<!\\d)(\\d+)\\((\\d+)\\)").matcher(log.trim()).replaceAll(match -> {
            int sourceId;
            try {
                sourceId = Integer.parseInt(match.group(1));
            } catch (NumberFormatException exception) {
                return match.group();
            }
            ResourceLocation location = sources.get(sourceId);
            return location == null ? match.group() : location + ":" + match.group(2);
        });
    }

    static String summary(String detail) {
        if (detail == null || detail.isBlank()) {
            return "shader validation failed without a driver log";
        }
        for (String line : detail.split("\\R")) {
            if (!line.isBlank()) {
                String trimmed = line.trim();
                return trimmed.length() <= MAX_SUMMARY_LENGTH
                    ? trimmed
                    : trimmed.substring(0, MAX_SUMMARY_LENGTH) + "...";
            }
        }
        return "shader validation failed without a driver log";
    }

    record PreprocessedStage(List<String> lines, List<ResourceLocation> imports,
        Map<Integer, ResourceLocation> sources) {
        PreprocessedStage {
            lines = List.copyOf(lines);
            imports = List.copyOf(imports);
            sources = Map.copyOf(sources);
        }
    }

    private static final class ImportPreprocessor extends GlslPreprocessor {
        private final ResourceLocation programId;
        private final String basePath;
        private final ResourceProvider resources;
        private final List<ResourceLocation> imports = new ArrayList<>();
        private final Map<Integer, ResourceLocation> sources = new LinkedHashMap<>();
        private final Set<String> imported = new HashSet<>();
        private int sourceId;

        private ImportPreprocessor(ResourceLocation programId, ResourceLocation stage,
            ResourceProvider resources) {
            this.programId = programId;
            this.basePath = FileUtil.getFullResourcePath(stage.getPath());
            this.resources = resources;
            this.sources.put(0, stage);
        }

        @Override
        public String applyImport(boolean relative, String path) {
            ResourceLocation location;
            try {
                location = ClientHooks.getShaderImportLocation(this.basePath, relative, path);
            } catch (RuntimeException exception) {
                LOGGER.error("Invalid shader import '{}' in {}", path, this.programId, exception);
                return "#error invalid shader import " + path;
            }
            if (!this.imported.add(location.toString())) {
                return null;
            }
            this.imports.add(location);
            this.sources.put(++this.sourceId, location);
            try {
                return read(this.resources.getResource(location)
                    .orElseThrow(() -> new IOException("Missing shader import " + location))
                    .openAsReader());
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Could not open shader import {} for {}",
                    location, this.programId, exception);
                return "#error " + exception.getMessage();
            }
        }

        private List<ResourceLocation> imports() {
            return this.imports;
        }

        private Map<Integer, ResourceLocation> sources() {
            return this.sources;
        }
    }

    private static String read(BufferedReader reader) throws IOException {
        try (reader) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4_096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, count);
            }
            return builder.toString();
        }
    }
}
