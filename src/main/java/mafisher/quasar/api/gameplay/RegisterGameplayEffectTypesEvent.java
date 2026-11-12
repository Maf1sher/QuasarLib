package mafisher.quasar.api.gameplay;

import java.util.Objects;
import java.util.function.Function;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.effect.gameplay.GameplayEffectTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Fired during common setup so consumers can register a factory that rebuilds a gameplay
 * controller from a persisted descriptor. Without a factory the session cannot resume after a
 * restart and its descriptor is quarantined for diagnostics.
 */
public final class RegisterGameplayEffectTypesEvent extends Event {
    public void register(
        ResourceLocation typeId,
        Function<EffectDescriptor, GameplayEffectController> factory
    ) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(factory, "factory");
        GameplayEffectTypeRegistry.register(typeId, factory);
    }
}
