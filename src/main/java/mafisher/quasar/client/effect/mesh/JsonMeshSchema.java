package mafisher.quasar.client.effect.mesh;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.joml.Vector3f;

/** Type and range checks shared by the JSON static-mesh reader. */
final class JsonMeshSchema {
    private JsonMeshSchema() {
    }

    static JsonElement require(JsonObject object, String key, ResourceLocation location) {
        return require(object, key, location, "root");
    }

    static JsonElement require(JsonObject object, String key, ResourceLocation location, String path) {
        JsonElement element = object.get(key);
        if (element == null) {
            throw error(location, path + " is missing '" + key + "'");
        }
        return element;
    }

    static JsonArray requireArray(JsonElement element, ResourceLocation location, String path) {
        if (element == null || !element.isJsonArray()) {
            throw error(location, path + " must be an array");
        }
        return element.getAsJsonArray();
    }

    static Vector3f readVector3(JsonElement element, ResourceLocation location, String path) {
        JsonArray array = requireArray(element, location, path);
        if (array.size() != 3) {
            throw error(location, path + " must contain exactly three numbers");
        }
        return new Vector3f(
            readFloat(array.get(0), location, path + "[0]"),
            readFloat(array.get(1), location, path + "[1]"),
            readFloat(array.get(2), location, path + "[2]")
        );
    }

    static Vector2f readVector2(JsonElement element, ResourceLocation location, String path) {
        JsonArray array = requireArray(element, location, path);
        if (array.size() != 2) {
            throw error(location, path + " must contain exactly two numbers");
        }
        return new Vector2f(
            readFloat(array.get(0), location, path + "[0]"),
            readFloat(array.get(1), location, path + "[1]")
        );
    }

    static float readFloat(JsonElement element, ResourceLocation location, String path) {
        if (element == null || !element.isJsonPrimitive()
            || !element.getAsJsonPrimitive().isNumber()) {
            throw error(location, path + " must be a number");
        }
        float value = element.getAsFloat();
        if (!Float.isFinite(value)) {
            throw error(location, path + " must be finite");
        }
        return value;
    }

    static int readInt(JsonElement element, ResourceLocation location, String path) {
        if (element == null || !element.isJsonPrimitive()
            || !element.getAsJsonPrimitive().isNumber()) {
            throw error(location, path + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value)
            || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw error(location, path + " must be an integer");
        }
        return (int)value;
    }

    static void rejectUnknown(JsonObject object, Set<String> allowed, ResourceLocation location,
        String path) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw error(location, path + " has unknown field '" + key + "'");
            }
        }
    }

    static IllegalArgumentException error(ResourceLocation location, String message) {
        return new IllegalArgumentException("Invalid JSON static mesh " + location + ": " + message);
    }
}
