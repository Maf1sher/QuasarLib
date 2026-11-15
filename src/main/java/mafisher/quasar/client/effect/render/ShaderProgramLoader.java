package mafisher.quasar.client.effect.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * Reads the JSON and both stage files for one shader program. The JSON contract
 * is validated here; GLSL compilation is left to {@link ShaderProgramDiagnostics}.
 */
final class ShaderProgramLoader {
    private static final String SHADER_CORE_PATH = "shaders/core/";

    private ShaderProgramLoader() {
    }

    static ShaderProgramSource load(ResourceProvider resources, ResourceLocation programId)
        throws LoadException {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(programId, "programId");
        ResourceLocation jsonLocation = ResourceLocation.fromNamespaceAndPath(
            programId.getNamespace(), SHADER_CORE_PATH + programId.getPath() + ".json"
        );
        LoadedResource json = read(resources, jsonLocation, "json");
        JsonObject object = parseJson(json.text(), jsonLocation, json.pack());
        ShaderJsonContract.Contract contract;
        try {
            contract = ShaderJsonContract.validate(object);
        } catch (ShaderJsonContract.Failure failure) {
            throw new LoadException("json", jsonLocation, json.pack(), failure.getMessage(), failure);
        }

        ResourceLocation vertexLocation = stageLocation(contract.vertex(), ".vsh", jsonLocation,
            json.pack());
        ResourceLocation fragmentLocation = stageLocation(contract.fragment(), ".fsh", jsonLocation,
            json.pack());
        LoadedResource vertex = read(resources, vertexLocation, "vertex");
        LoadedResource fragment = read(resources, fragmentLocation, "fragment");
        return new ShaderProgramSource(programId,
            new ShaderProgramSource.Asset(json.text(), jsonLocation, json.pack()),
            new ShaderProgramSource.Asset(vertex.text(), vertexLocation, vertex.pack()),
            new ShaderProgramSource.Asset(fragment.text(), fragmentLocation, fragment.pack()));
    }

    private static JsonObject parseJson(String text, ResourceLocation location, String pack)
        throws LoadException {
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("shader JSON is not an object");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new LoadException("json", location, pack, "could not parse shader JSON", exception);
        }
    }

    private static ResourceLocation stageLocation(String name, String extension,
        ResourceLocation jsonLocation, String pack) throws LoadException {
        try {
            ResourceLocation stage = ResourceLocation.parse(name);
            return ResourceLocation.fromNamespaceAndPath(
                stage.getNamespace(), SHADER_CORE_PATH + stage.getPath() + extension
            );
        } catch (RuntimeException exception) {
            throw new LoadException("json", jsonLocation, pack, "invalid shader stage '" + name + "'",
                exception);
        }
    }

    private static LoadedResource read(ResourceProvider resources, ResourceLocation location,
        String stage) throws LoadException {
        try {
            Resource resource = resources.getResource(location)
                .orElseThrow(() -> new IOException("Missing shader asset " + location));
            String pack = resource.sourcePackId();
            try (BufferedReader reader = resource.openAsReader()) {
                return new LoadedResource(readAll(reader), pack == null ? "" : pack);
            }
        } catch (IOException | RuntimeException exception) {
            throw new LoadException(stage, location, "", "could not read shader asset", exception);
        }
    }

    private static String readAll(BufferedReader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4_096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            builder.append(buffer, 0, read);
        }
        return builder.toString();
    }

    private record LoadedResource(String text, String pack) {
    }

    /** Failure while reading or validating one program before GLSL compilation. */
    static final class LoadException extends Exception {
        private final String stage;
        private final ResourceLocation resource;
        private final String pack;
        private final String detail;

        private LoadException(String stage, ResourceLocation resource, String pack, String message,
            Throwable cause) {
            super(message, cause);
            this.stage = stage;
            this.resource = resource;
            this.pack = pack == null ? "" : pack;
            this.detail = cause == null ? message : message + ": " + cause;
        }

        String stage() {
            return this.stage;
        }

        ResourceLocation resource() {
            return this.resource;
        }

        String pack() {
            return this.pack;
        }

        String detail() {
            return this.detail;
        }
    }
}
