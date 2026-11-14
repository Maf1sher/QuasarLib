package mafisher.quasar.client.effect.mesh;

import mafisher.quasar.api.client.render.EffectStaticMesh;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Token parsing and range checks for the OBJ static-mesh reader. */
final class ObjTokens {
    private ObjTokens() {
    }

    static int resolveIndex(String value, int count, ResourceLocation location, int lineNumber,
        String kind) {
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw error(location, lineNumber, "invalid " + kind + " index '" + value + "'", exception);
        }
        if (parsed == 0L) {
            throw error(location, lineNumber, kind + " indices are one-based and cannot be zero");
        }
        long resolved = parsed > 0L ? parsed - 1L : count + parsed;
        if (resolved < 0L || resolved >= count) {
            throw error(location, lineNumber, kind + " index " + value + " is out of range");
        }
        return (int)resolved;
    }

    static Vector3f readVector3(String[] tokens, ResourceLocation location, int lineNumber,
        String kind) {
        return new Vector3f(
            parseFloat(tokens[1], location, lineNumber, kind),
            parseFloat(tokens[2], location, lineNumber, kind),
            parseFloat(tokens[3], location, lineNumber, kind)
        );
    }

    static float parseFloat(String value, ResourceLocation location, int lineNumber, String kind) {
        float parsed;
        try {
            parsed = Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            throw error(location, lineNumber, "invalid " + kind + " value '" + value + "'", exception);
        }
        if (!Float.isFinite(parsed)) {
            throw error(location, lineNumber, kind + " contains a non-finite value");
        }
        return parsed;
    }

    static void requireTokens(String[] tokens, int minimum, ResourceLocation location,
        int lineNumber, String kind) {
        if (tokens.length < minimum) {
            throw error(location, lineNumber, kind + " record is incomplete");
        }
    }

    static void checkSourceCapacity(int size, ResourceLocation location, int lineNumber, String kind) {
        if (size >= EffectStaticMesh.MAX_VERTICES) {
            throw error(location, lineNumber,
                "mesh exceeds " + EffectStaticMesh.MAX_VERTICES + " " + kind + " records");
        }
    }

    static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    static IllegalArgumentException error(ResourceLocation location, int lineNumber, String message) {
        return new IllegalArgumentException(
            "Invalid OBJ static mesh " + location + " at line " + lineNumber + ": " + message
        );
    }

    static IllegalArgumentException error(ResourceLocation location, int lineNumber, String message,
        Throwable cause) {
        return new IllegalArgumentException(
            "Invalid OBJ static mesh " + location + " at line " + lineNumber + ": " + message,
            cause
        );
    }
}
