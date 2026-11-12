package mafisher.quasar.api.client;

import java.util.Objects;
import net.minecraft.client.renderer.RenderType;

/** One ordered render operation declared by a visual effect renderer. */
public record EffectRenderPass(String id, RenderType renderType) {
    public EffectRenderPass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(renderType, "renderType");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Render pass ID must not be blank");
        }
    }
}
