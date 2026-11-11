package mafisher.quasar.effect;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;

/**
 * Internal per-level state slot. Every slot is cleared by the effect lifecycle when a level
 * unloads or the server stops, so services never rely on garbage collection for cleanup.
 *
 * <p>This type is an implementation detail of the effect runtime and is not part of the
 * supported API.
 */
public final class LevelStateSlot<T> {
    private static final List<LevelStateSlot<?>> SLOTS = new CopyOnWriteArrayList<>();

    private final Map<ServerLevel, T> values = new WeakHashMap<>();
    private final Supplier<T> factory;

    private LevelStateSlot(Supplier<T> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /** Creates and registers a slot whose value is created lazily for each level. */
    public static <T> LevelStateSlot<T> create(Supplier<T> factory) {
        LevelStateSlot<T> slot = new LevelStateSlot<>(factory);
        SLOTS.add(slot);
        return slot;
    }

    /** Returns the level's state, creating it when needed. */
    public T get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(level);
        return this.values.computeIfAbsent(level, ignored -> this.factory.get());
    }

    /** Returns the level's state without creating it. */
    public T peek(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(level);
        return this.values.get(level);
    }

    /** Drops this slot's state for one level. */
    public void remove(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(level);
        this.values.remove(level);
    }

    /** Drops every registered slot for one level. */
    public static void clearLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(level);
        for (LevelStateSlot<?> slot : SLOTS) {
            slot.values.remove(level);
        }
    }

    static boolean isTracked(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        for (LevelStateSlot<?> slot : SLOTS) {
            if (slot.values.containsKey(level)) {
                return true;
            }
        }
        return false;
    }
}
