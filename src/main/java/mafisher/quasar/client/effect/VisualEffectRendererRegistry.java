package mafisher.quasar.client.effect;

import mafisher.quasar.effect.EffectVisualIds;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Client-only registry; visual effects do not need a gameplay registration. */
public final class VisualEffectRendererRegistry {
    private final Map<ResourceLocation, VisualEffectRenderer> renderers = new HashMap<>();
    private boolean initialized;

    public VisualEffectRendererRegistry() {
    }

    public void registerBuiltIns() {
        if (this.initialized) {
            return;
        }
        this.register(EffectVisualIds.DEBUG_RUNE_CIRCLE, new RuneCircleRenderer());
        this.register(EffectVisualIds.DEBUG_WAVE_DISTORTION, new WaveDistortionRenderer());
        this.register(EffectVisualIds.DEBUG_SCENE_BLUR, new SceneBlurRenderer());
        this.register(EffectVisualIds.DEBUG_BLOOM, new BloomRenderer());
        this.register(EffectVisualIds.DEBUG_BLACK_HOLE, new BlackHoleRenderer());
        this.initialized = true;
    }

    public void register(ResourceLocation id, VisualEffectRenderer renderer) {
        if (this.renderers.putIfAbsent(id, renderer) != null) {
            throw new IllegalStateException("A visual effect renderer is already registered for " + id);
        }
    }

    public Optional<VisualEffectRenderer> find(ResourceLocation id) {
        return Optional.ofNullable(this.renderers.get(id));
    }

    void onResourceReload(ResourceManager resourceManager) {
        for (VisualEffectRenderer renderer : this.renderers.values()) {
            renderer.onResourceReload(resourceManager);
        }
    }
}
