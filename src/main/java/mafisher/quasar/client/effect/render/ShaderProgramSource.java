package mafisher.quasar.client.effect.render;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One shader program loaded from the resource manager. Stage sources are kept
 * as text so the diagnostic pass can reuse the preprocessor exactly once.
 */
record ShaderProgramSource(
    ResourceLocation programId,
    Asset json,
    Asset vertex,
    Asset fragment
) {
    ShaderProgramSource {
        Objects.requireNonNull(programId, "programId");
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(vertex, "vertex");
        Objects.requireNonNull(fragment, "fragment");
    }

    record Asset(String text, ResourceLocation location, String pack) {
        Asset {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(location, "location");
            pack = pack == null ? "" : pack;
        }
    }
}
