package mafisher.quasar.client.effect.mesh;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Static mesh source formats selected by the asset extension. */
enum StaticMeshFormat {
    OBJ(".obj"),
    JSON(".json");

    private final String extension;

    StaticMeshFormat(String extension) {
        this.extension = extension;
    }

    static StaticMeshFormat from(ResourceLocation location) {
        Objects.requireNonNull(location, "location");
        String path = location.getPath();
        for (StaticMeshFormat format : values()) {
            if (path.endsWith(format.extension)) {
                return format;
            }
        }
        throw new IllegalArgumentException(
            "Static effect mesh must end in .obj or .json: " + location
        );
    }
}
