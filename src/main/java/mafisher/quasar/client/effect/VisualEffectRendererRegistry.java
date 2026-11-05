package mafisher.quasar.client.effect;

import mafisher.quasar.effect.EffectVisualIds;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Client-only registry; visual effects do not need a gameplay registration. */
public final class VisualEffectRendererRegistry {
    private static final Map<ResourceLocation, VisualEffectRenderer> RENDERERS = new HashMap<>();
    private static boolean initialized;

    private VisualEffectRendererRegistry() {
    }

    public static void registerBuiltIns() {
        if (initialized) {
            return;
        }
        initialized = true;
        register(EffectVisualIds.DEBUG_RUNE_CIRCLE, new RuneCircleRenderer());
    }

    public static void register(ResourceLocation id, VisualEffectRenderer renderer) {
        if (RENDERERS.putIfAbsent(id, renderer) != null) {
            throw new IllegalStateException("A visual effect renderer is already registered for " + id);
        }
    }

    public static Optional<VisualEffectRenderer> find(ResourceLocation id) {
        return Optional.ofNullable(RENDERERS.get(id));
    }
}
