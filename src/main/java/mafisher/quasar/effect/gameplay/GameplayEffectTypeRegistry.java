package mafisher.quasar.effect.gameplay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.persistence.EffectDescriptor;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Registry of consumer-owned gameplay effect types that can rebuild a controller after restart. */
@ApiStatus.Internal
public final class GameplayEffectTypeRegistry {
    private static final Map<ResourceLocation, Function<EffectDescriptor, GameplayEffectController>> FACTORIES =
        new LinkedHashMap<>();

    private GameplayEffectTypeRegistry() {
    }

    public static void register(
        ResourceLocation typeId,
        Function<EffectDescriptor, GameplayEffectController> factory
    ) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(factory, "factory");
        if (FACTORIES.putIfAbsent(typeId, factory) != null) {
            throw new IllegalStateException("Gameplay effect type is already registered: " + typeId);
        }
    }

    public static Optional<GameplayEffectController> create(EffectDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        Function<EffectDescriptor, GameplayEffectController> factory = FACTORIES.get(descriptor.typeId());
        return factory == null ? Optional.empty() : Optional.ofNullable(factory.apply(descriptor));
    }

    public static boolean contains(ResourceLocation typeId) {
        return FACTORIES.containsKey(typeId);
    }

    public static int size() {
        return FACTORIES.size();
    }
}
