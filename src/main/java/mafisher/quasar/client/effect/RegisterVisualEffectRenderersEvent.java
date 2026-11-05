package mafisher.quasar.client.effect;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Fired once during client setup, after QuasarLib registers its built-in renderers and before the renderer registry is
 * frozen. Subscribe during mod construction, for example with {@code @EventBusSubscriber}, so the handler is present
 * before client setup runs.
 *
 * <p>Handlers run on the client setup thread. They must only call {@link #register(ResourceLocation, VisualEffectRenderer)}
 * and must not touch the client world, GL state, or loaded resources.</p>
 */
public final class RegisterVisualEffectRenderersEvent extends Event {
    private final VisualEffectRendererRegistry registry;

    RegisterVisualEffectRenderersEvent(VisualEffectRendererRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Registers one renderer for a consumer-owned effect ID. The renderer instance is shared by every active effect
     * with that ID; it must not keep per-instance mutable state.
     *
     * @param effectId the effect ID sent by the server
     * @param renderer the client-only renderer instance
     * @throws IllegalStateException if the ID is already registered or registration is already closed
     */
    public void register(ResourceLocation effectId, VisualEffectRenderer renderer) {
        this.registry.register(effectId, renderer);
    }
}
