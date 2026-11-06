package mafisher.quasar.client.effect;

import java.util.Objects;
import net.neoforged.bus.api.Event;

/**
 * Fired once during client setup, after QuasarLib registers its built-in screen
 * operations and before the operation registry is frozen.
 *
 * <p>Subscribe during mod construction, for example with
 * {@code @EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)}.
 * Handlers run on the first client-use thread. They must only call
 * {@link #register(ScreenEffectOperation, ScreenEffectOperationRenderer)} and
 * must not touch the client world, GL state, or loaded resources.</p>
 */
public final class RegisterScreenEffectOperationsEvent extends Event {
    private final ScreenEffectOperationRegistry registry;

    RegisterScreenEffectOperationsEvent(ScreenEffectOperationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Registers one renderer for a consumer-owned operation ID.
     *
     * @param operation the operation handle returned by
     *     {@link ScreenEffectOperation#create(net.minecraft.resources.ResourceLocation)}
     * @param renderer the client-only operation renderer
     * @throws IllegalStateException if the ID is already registered or registration is already closed
     */
    public void register(ScreenEffectOperation operation, ScreenEffectOperationRenderer renderer) {
        this.registry.register(operation, renderer);
    }
}
