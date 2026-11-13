package mafisher.quasar.client.effect.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import mafisher.quasar.api.client.render.VisualEffectRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.ApiStatus;

/** Client-only registry; visual effects do not need a gameplay registration. */
@ApiStatus.Internal
public final class VisualEffectRendererRegistry {
    private final Map<ResourceLocation, VisualEffectRenderer> renderers = new HashMap<>();
    private Map<ResourceLocation, VisualEffectRenderer> frozenRenderers;

    public VisualEffectRendererRegistry() {
    }

    public void register(ResourceLocation id, VisualEffectRenderer renderer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(renderer, "renderer");
        if (this.frozenRenderers != null) {
            throw new IllegalStateException("Visual effect renderer registration is closed after client setup: " + id);
        }
        if (this.renderers.putIfAbsent(id, renderer) != null) {
            throw new IllegalStateException("A visual effect renderer is already registered for " + id);
        }
    }

    public Optional<VisualEffectRenderer> find(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        Map<ResourceLocation, VisualEffectRenderer> current = this.frozenRenderers;
        if (current != null) {
            return Optional.ofNullable(current.get(id));
        }
        return Optional.ofNullable(this.renderers.get(id));
    }

    /** Makes the current registrations immutable and rejects any later registration. */
    public void freeze() {
        if (this.frozenRenderers == null) {
            this.frozenRenderers = Map.copyOf(this.renderers);
        }
    }

    /** Number of registered renderers, used by startup diagnostics. */
    public int size() {
        Map<ResourceLocation, VisualEffectRenderer> current = this.frozenRenderers;
        return current != null ? current.size() : this.renderers.size();
    }

    void onResourceReload(ResourceManager resourceManager) {
        Map<ResourceLocation, VisualEffectRenderer> current = this.frozenRenderers;
        Iterable<VisualEffectRenderer> registeredRenderers = current != null
            ? current.values()
            : this.renderers.values();
        for (VisualEffectRenderer renderer : registeredRenderers) {
            renderer.onResourceReload(resourceManager);
        }
    }
}
