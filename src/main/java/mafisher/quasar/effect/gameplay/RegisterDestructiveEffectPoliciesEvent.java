package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Fired once during common setup so consumers can register an admission policy for destructive
 * gameplay effects. Every registered policy must allow an effect; duplicate IDs and late
 * registration throw.
 *
 * <p>Subscribe on the game bus, for example with
 * {@code @EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)}.</p>
 */
public final class RegisterDestructiveEffectPoliciesEvent extends Event {
    public void register(ResourceLocation id, DestructiveEffectPolicy policy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policy, "policy");
        DestructiveEffectPolicies.register(id, policy);
    }
}
