package mafisher.quasar.client.effect;

import mafisher.quasar.effect.EffectVisualIds;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Client-only registry; visual effects do not need a gameplay registration. */
final class VisualEffectRendererRegistry {
    private final Map<ResourceLocation, VisualEffectRenderer> renderers = new HashMap<>();
    private Map<ResourceLocation, VisualEffectRenderer> frozenRenderers;
    private boolean builtInsRegistered;

    VisualEffectRendererRegistry() {
    }

    void registerBuiltIns() {
        if (this.builtInsRegistered) {
            return;
        }
        this.register(EffectVisualIds.DEBUG_RUNE_CIRCLE, new RuneCircleRenderer());
        this.register(EffectVisualIds.DEBUG_WAVE_DISTORTION, new WaveDistortionRenderer());
        this.register(EffectVisualIds.DEBUG_SCENE_BLUR, new SceneBlurRenderer());
        this.register(EffectVisualIds.DEBUG_BLOOM, new BloomRenderer());
        this.register(EffectVisualIds.DEBUG_BLACK_HOLE, new BlackHoleRenderer());
        this.builtInsRegistered = true;
    }

    void register(ResourceLocation id, VisualEffectRenderer renderer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(renderer, "renderer");
        if (this.frozenRenderers != null) {
            throw new IllegalStateException("Visual effect renderer registration is closed after client setup: " + id);
        }
        if (this.renderers.putIfAbsent(id, renderer) != null) {
            throw new IllegalStateException("A visual effect renderer is already registered for " + id);
        }
    }

    Optional<VisualEffectRenderer> find(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        Map<ResourceLocation, VisualEffectRenderer> current = this.frozenRenderers;
        if (current != null) {
            return Optional.ofNullable(current.get(id));
        }
        return Optional.ofNullable(this.renderers.get(id));
    }

    /** Makes the current registrations immutable and rejects any later registration. */
    void freeze() {
        if (this.frozenRenderers == null) {
            this.frozenRenderers = Map.copyOf(this.renderers);
        }
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
