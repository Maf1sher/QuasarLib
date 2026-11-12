package mafisher.quasar.effect.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectThreading;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Composition root for per-level effect runtimes. This is the only static holder of mutable
 * effect state; each runtime owns its services and state.
 */
@ApiStatus.Internal
public final class EffectRuntimeRegistry {
    private static final Map<ServerLevel, LevelEffectRuntime> RUNTIMES = new WeakHashMap<>();

    private EffectRuntimeRegistry() {
    }

    /** Returns the runtime for one level, creating it when needed. */
    public static LevelEffectRuntime get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(level);
        return RUNTIMES.computeIfAbsent(level, LevelEffectRuntime::new);
    }

    /** Returns the runtime for one level without creating it. */
    public static LevelEffectRuntime peek(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(level);
        return RUNTIMES.get(level);
    }

    /** Binds a runtime that the caller constructed with explicit seams. */
    public static void install(ServerLevel level, LevelEffectRuntime runtime) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(runtime, "runtime");
        EffectThreading.requireServerThread(level);
        if (runtime.level() != level) {
            throw new IllegalArgumentException("Effect runtime belongs to another level");
        }
        if (RUNTIMES.containsKey(level)) {
            throw new IllegalStateException("An effect runtime is already installed for this level");
        }
        RUNTIMES.put(level, runtime);
    }

    /** Closes the runtime and detaches it from the level. */
    public static void close(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(level);
        LevelEffectRuntime runtime = RUNTIMES.get(level);
        if (runtime == null) {
            return;
        }
        try {
            runtime.close();
        } finally {
            RUNTIMES.remove(level);
        }
    }

    /** Drops transient runtime state without service shutdown. */
    public static void reset(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(level);
        LevelEffectRuntime runtime = RUNTIMES.get(level);
        if (runtime == null) {
            return;
        }
        try {
            runtime.discard();
        } finally {
            RUNTIMES.remove(level);
        }
    }
}
