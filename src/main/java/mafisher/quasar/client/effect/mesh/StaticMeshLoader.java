package mafisher.quasar.client.effect.mesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reads one static mesh asset from the client resource manager. */
final class StaticMeshLoader {
    private StaticMeshLoader() {
    }

    static StaticMeshData load(ResourceManager resources, ResourceLocation location, StaticMeshFormat format)
        throws IOException {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(format, "format");
        Resource resource = resources.getResource(location)
            .orElseThrow(() -> new IOException("Missing static effect mesh " + location));
        try (BufferedReader reader = resource.openAsReader()) {
            return switch (format) {
                case OBJ -> ObjStaticMeshReader.read(location, reader);
                case JSON -> JsonStaticMeshReader.read(location, reader);
            };
        }
    }
}
