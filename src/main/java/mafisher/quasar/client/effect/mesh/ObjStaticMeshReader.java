package mafisher.quasar.client.effect.mesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectStaticMesh;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Minimal Wavefront OBJ reader for static effect meshes. It keeps geometry only:
 * object/group/material records are ignored, triangles become degenerate quads,
 * and UV V coordinates are flipped to Minecraft's top-left texture origin.
 */
final class ObjStaticMeshReader {
    private static final int MAX_QUADS = EffectStaticMesh.MAX_VERTICES / 4;

    private ObjStaticMeshReader() {
    }

    static StaticMeshData read(ResourceLocation location, BufferedReader reader) throws IOException {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(reader, "reader");
        List<Vector3f> positions = new ArrayList<>();
        List<Vector2f> uvs = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<ObjFace> faces = new ArrayList<>();
        int emittedQuads = 0;
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String content = ObjTokens.stripComment(line).trim();
            if (content.isEmpty()) {
                continue;
            }
            String[] tokens = content.split("\\s+");
            switch (tokens[0]) {
                case "v" -> {
                    ObjTokens.requireTokens(tokens, 4, location, lineNumber, "vertex");
                    ObjTokens.checkSourceCapacity(positions.size(), location, lineNumber, "vertex");
                    positions.add(ObjTokens.readVector3(tokens, location, lineNumber, "vertex"));
                }
                case "vt" -> {
                    ObjTokens.requireTokens(tokens, 3, location, lineNumber, "texture coordinate");
                    ObjTokens.checkSourceCapacity(uvs.size(), location, lineNumber, "texture coordinate");
                    float u = ObjTokens.parseFloat(tokens[1], location, lineNumber, "texture coordinate");
                    float v = ObjTokens.parseFloat(tokens[2], location, lineNumber, "texture coordinate");
                    uvs.add(new Vector2f(u, 1.0F - v));
                }
                case "vn" -> {
                    ObjTokens.requireTokens(tokens, 4, location, lineNumber, "normal");
                    ObjTokens.checkSourceCapacity(normals.size(), location, lineNumber, "normal");
                    normals.add(ObjTokens.readVector3(tokens, location, lineNumber, "normal"));
                }
                case "f" -> {
                    ObjTokens.requireTokens(tokens, 4, location, lineNumber, "face");
                    ObjFace face = parseFace(tokens, positions.size(), uvs.size(), normals.size(),
                        location, lineNumber);
                    long nextEmitted = (long)emittedQuads + face.emittedQuads();
                    if (nextEmitted > MAX_QUADS) {
                        throw ObjTokens.error(location, lineNumber,
                            "mesh exceeds " + EffectStaticMesh.MAX_VERTICES + " vertices");
                    }
                    emittedQuads = (int)nextEmitted;
                    faces.add(face);
                }
                default -> {
                    // Other OBJ records do not affect geometry.
                }
            }
        }
        return build(location, faces, positions, uvs, normals, emittedQuads);
    }

    private static StaticMeshData build(ResourceLocation location, List<ObjFace> faces,
        List<Vector3f> positions, List<Vector2f> uvs, List<Vector3f> normals, int emittedQuads) {
        if (faces.isEmpty()) {
            throw new IllegalArgumentException("OBJ static mesh " + location + " contains no faces");
        }
        StaticMeshBuilder mesh = new StaticMeshBuilder(emittedQuads);
        ObjFaceEmitter emitter = new ObjFaceEmitter(mesh, positions, uvs, normals);
        for (ObjFace face : faces) {
            emitter.addFace(face);
        }
        return mesh.build();
    }

    private static ObjFace parseFace(String[] tokens, int positionCount, int uvCount, int normalCount,
        ResourceLocation location, int lineNumber) {
        int vertexCount = tokens.length - 1;
        int[] positions = new int[vertexCount];
        int[] uvs = new int[vertexCount];
        int[] normals = new int[vertexCount];
        for (int index = 0; index < vertexCount; index++) {
            String reference = tokens[index + 1];
            String[] parts = reference.split("/", -1);
            if (parts.length == 0 || parts.length > 3 || parts[0].isEmpty()) {
                throw ObjTokens.error(location, lineNumber, "invalid face reference '" + reference + "'");
            }
            positions[index] = ObjTokens.resolveIndex(
                parts[0], positionCount, location, lineNumber, "position"
            );
            uvs[index] = parts.length >= 2 && !parts[1].isEmpty()
                ? ObjTokens.resolveIndex(parts[1], uvCount, location, lineNumber, "texture coordinate")
                : -1;
            normals[index] = parts.length == 3 && !parts[2].isEmpty()
                ? ObjTokens.resolveIndex(parts[2], normalCount, location, lineNumber, "normal")
                : -1;
        }
        return new ObjFace(positions, uvs, normals);
    }
}
