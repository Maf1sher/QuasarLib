package mafisher.quasar.api.client;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Fired once when client loading completes, before the renderer registry is frozen. Every renderer is
 * consumer-owned; subscribe during mod construction, for example with {@code @EventBusSubscriber}, so the
 * handler is present before loading finishes.
 *
 * <p>Handlers run on the client load-complete thread. They must only call
 * {@link #register(ResourceLocation, VisualEffectRenderer)} and must not touch
 * the client world, GL state, or loaded resources.</p>
 */
public final class RegisterVisualEffectRenderersEvent extends Event {
    /** Engine callback used to publish one renderer. */
    @FunctionalInterface
    public interface Registrar {
        /** Registers one renderer for a consumer-owned effect ID. */
        void register(ResourceLocation effectId, VisualEffectRenderer renderer);
    }

    private final Registrar registrar;

    /** Engine-internal constructor; consumers subscribe to the event instead. */
    public RegisterVisualEffectRenderersEvent(Registrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
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
        this.registrar.register(effectId, renderer);
    }
}
