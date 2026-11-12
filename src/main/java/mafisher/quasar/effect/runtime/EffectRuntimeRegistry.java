package mafisher.quasar.effect.runtime;

import java.util.Objects;
import mafisher.quasar.effect.LevelStateSlot;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Composition root for per-level effect runtimes. Each runtime owns visual, segmented, audio,
 * cinematic, gameplay, terrain, rollback, and restore state.
 */
@ApiStatus.Internal
public final class EffectRuntimeRegistry {
    private static final LevelStateSlot<LevelEffectRuntime> RUNTIMES =
        LevelStateSlot.create(LevelEffectRuntime::new);

    private EffectRuntimeRegistry() {
    }

    /** Returns the runtime for one level, creating it when needed. */
    public static LevelEffectRuntime get(ServerLevel level) {
        return RUNTIMES.get(level);
    }

    /** Returns the runtime for one level without creating it. */
    public static LevelEffectRuntime peek(ServerLevel level) {
        return RUNTIMES.peek(level);
    }

    /** Binds a runtime that the caller constructed with explicit seams. */
    public static void install(ServerLevel level, LevelEffectRuntime runtime) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.level() != level) {
            throw new IllegalArgumentException("Effect runtime belongs to another level");
        }
        if (RUNTIMES.peek(level) != null) {
            throw new IllegalStateException("An effect runtime is already installed for this level");
        }
        RUNTIMES.set(level, runtime);
    }

    /** Closes the runtime and detaches it from the level. */
    public static void close(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = RUNTIMES.peek(level);
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
        LevelEffectRuntime runtime = RUNTIMES.peek(level);
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
