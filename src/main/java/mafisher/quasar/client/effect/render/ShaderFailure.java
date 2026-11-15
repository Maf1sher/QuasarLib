package mafisher.quasar.client.effect.render;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * One failed shader preflight check. The compact fields are safe for chat; the
 * detail keeps the full driver output for the log.
 */
@ApiStatus.Internal
public record ShaderFailure(
    ResourceLocation programId,
    String stage,
    String resource,
    String pack,
    List<String> imports,
    String summary,
    String detail
) {
    public ShaderFailure {
        Objects.requireNonNull(programId, "programId");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(resource, "resource");
        pack = pack == null ? "" : pack;
        imports = List.copyOf(imports);
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(detail, "detail");
    }
}
