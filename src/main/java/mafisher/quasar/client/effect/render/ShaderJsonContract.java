package mafisher.quasar.client.effect.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.util.GsonHelper;

/**
 * Validates the shader JSON contract enforced by {@code ShaderInstance}. This
 * keeps a preflight pass from green-lighting a program that the real loader will
 * reject later.
 */
final class ShaderJsonContract {
    private ShaderJsonContract() {
    }

    static Contract validate(JsonObject object) throws Failure {
        String vertex = requiredString(object, "vertex");
        String fragment = requiredString(object, "fragment");
        validateSamplers(object);
        validateUniforms(object);
        return new Contract(vertex, fragment);
    }

    private static String requiredString(JsonObject object, String name) throws Failure {
        try {
            return GsonHelper.getAsString(object, name);
        } catch (RuntimeException exception) {
            throw new Failure("missing or invalid '" + name + "'", exception);
        }
    }

    private static void validateSamplers(JsonObject object) throws Failure {
        JsonElement element = object.get("samplers");
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (!element.isJsonArray()) {
            throw new Failure("samplers must be an array", null);
        }
        JsonArray samplers = element.getAsJsonArray();
        for (int index = 0; index < samplers.size(); index++) {
            try {
                JsonObject sampler = GsonHelper.convertToJsonObject(samplers.get(index), "sampler");
                GsonHelper.getAsString(sampler, "name");
            } catch (RuntimeException exception) {
                throw new Failure("samplers[" + index + "]: " + exception.getMessage(), exception);
            }
        }
    }

    private static void validateUniforms(JsonObject object) throws Failure {
        JsonElement element = object.get("uniforms");
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (!element.isJsonArray()) {
            throw new Failure("uniforms must be an array", null);
        }
        JsonArray uniforms = element.getAsJsonArray();
        for (int index = 0; index < uniforms.size(); index++) {
            validateUniform(uniforms.get(index), index);
        }
    }

    private static void validateUniform(JsonElement element, int index) throws Failure {
        try {
            JsonObject uniform = GsonHelper.convertToJsonObject(element, "uniform");
            GsonHelper.getAsString(uniform, "name");
            String type = GsonHelper.getAsString(uniform, "type");
            if (Uniform.getTypeFromString(type) < 0) {
                throw new IllegalArgumentException("unknown uniform type '" + type + "'");
            }
            int count = GsonHelper.getAsInt(uniform, "count");
            if (count < 1) {
                throw new IllegalArgumentException("uniform count must be positive");
            }
            JsonArray values = GsonHelper.getAsJsonArray(uniform, "values");
            if (values.size() != count && values.size() > 1) {
                throw new IllegalArgumentException(
                    "expected " + count + " values, found " + values.size()
                );
            }
            for (JsonElement value : values) {
                GsonHelper.convertToFloat(value, "value");
            }
        } catch (RuntimeException exception) {
            throw new Failure("uniforms[" + index + "]: " + exception.getMessage(), exception);
        }
    }

    record Contract(String vertex, String fragment) {
    }

    static final class Failure extends Exception {
        private Failure(String message, Throwable cause) {
            super(message == null || message.isBlank()
                ? (cause == null ? "invalid shader JSON" : cause.toString())
                : message, cause);
        }
    }
}
