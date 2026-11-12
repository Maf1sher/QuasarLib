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
    private static boolean frozen;

    private GameplayEffectTypeRegistry() {
    }

    public static void register(
        ResourceLocation typeId,
        Function<EffectDescriptor, GameplayEffectController> factory
    ) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(factory, "factory");
        if (frozen) {
            throw new IllegalStateException("Gameplay effect type registration is already closed");
        }
        if (FACTORIES.putIfAbsent(typeId, factory) != null) {
            throw new IllegalStateException("Gameplay effect type is already registered: " + typeId);
        }
    }

    /** Closes consumer registration after the common-setup event. */
    public static void freeze() {
        frozen = true;
    }

    static void clearForTesting() {
        FACTORIES.clear();
        frozen = false;
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
