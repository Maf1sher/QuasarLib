package mafisher.quasar.client.effect.mesh;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.api.client.render.EffectMesh;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Native JSON static mesh reader. Every quad is an array of four vertex objects; the
 * schema deliberately has no shared index list because {@code EffectMesh} stores
 * contiguous quads.
 */
final class JsonStaticMeshReader {
    private static final Set<String> ROOT_KEYS = Set.of("format_version", "quads");
    private static final Set<String> VERTEX_KEYS = Set.of("position", "uv", "normal", "alpha");
    private static final Vector2f NO_UV = new Vector2f(0.0F, 0.0F);

    private JsonStaticMeshReader() {
    }

    static StaticMeshData read(ResourceLocation location, BufferedReader reader) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(reader, "reader");
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException(
                "Invalid JSON static mesh " + location + ": " + exception.getMessage(), exception
            );
        }
        if (root == null || !root.isJsonObject()) {
            throw error(location, "root must be an object");
        }
        JsonObject object = root.getAsJsonObject();
        JsonMeshSchema.rejectUnknown(object, ROOT_KEYS, location, "root");
        int formatVersion = JsonMeshSchema.readInt(
            JsonMeshSchema.require(object, "format_version", location), location, "format_version"
        );
        if (formatVersion != 1) {
            throw error(location, "unsupported format_version " + formatVersion);
        }
        JsonArray quads = JsonMeshSchema.requireArray(
            JsonMeshSchema.require(object, "quads", location), location, "quads"
        );
        if (quads.isEmpty()) {
            throw error(location, "must contain at least one quad");
        }
        StaticMeshBuilder mesh = new StaticMeshBuilder(quads.size());
        for (int index = 0; index < quads.size(); index++) {
            parseQuad(mesh, quads.get(index), index, location);
        }
        return mesh.build();
    }

    private static void parseQuad(StaticMeshBuilder mesh, JsonElement element, int index,
        ResourceLocation location) {
        if (!element.isJsonArray()) {
            throw error(location, "quads[" + index + "] must be an array");
        }
        JsonArray quad = element.getAsJsonArray();
        if (quad.size() != 4) {
            throw error(location, "quads[" + index + "] must contain exactly four vertices");
        }
        List<Vertex> vertices = new ArrayList<>(4);
        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            vertices.add(parseVertex(quad.get(vertexIndex), location,
                "quads[" + index + "][" + vertexIndex + "]"));
        }
        Vector3f faceNormal = StaticMeshBuilder.faceNormal(
            vertices.stream().map(Vertex::position).toList()
        );
        for (Vertex vertex : vertices) {
            Vector3f normal = vertex.normal == null
                ? null
                : StaticMeshBuilder.normalized(vertex.normal);
            if (normal == null) {
                normal = faceNormal;
            }
            mesh.vertex(new EffectMesh.Vertex(
                vertex.position.x, vertex.position.y, vertex.position.z,
                vertex.uv.x, vertex.uv.y, normal.x, normal.y, normal.z, vertex.alpha
            ));
        }
    }

    private static Vertex parseVertex(JsonElement element, ResourceLocation location, String path) {
        if (!element.isJsonObject()) {
            throw error(location, path + " must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        JsonMeshSchema.rejectUnknown(object, VERTEX_KEYS, location, path);
        Vector3f position = JsonMeshSchema.readVector3(
            JsonMeshSchema.require(object, "position", location, path), location, path + ".position"
        );
        Vector2f uv = object.has("uv")
            ? JsonMeshSchema.readVector2(object.get("uv"), location, path + ".uv")
            : NO_UV;
        Vector3f normal = object.has("normal")
            ? JsonMeshSchema.readVector3(object.get("normal"), location, path + ".normal")
            : null;
        float alpha = object.has("alpha")
            ? JsonMeshSchema.readFloat(object.get("alpha"), location, path + ".alpha")
            : 1.0F;
        if (alpha < 0.0F || alpha > 1.0F) {
            throw error(location, path + ".alpha must be between 0 and 1");
        }
        return new Vertex(position, uv, normal, alpha);
    }

    private static IllegalArgumentException error(ResourceLocation location, String message) {
        return JsonMeshSchema.error(location, message);
    }

    private record Vertex(Vector3f position, Vector2f uv, @Nullable Vector3f normal, float alpha) {
    }
}
