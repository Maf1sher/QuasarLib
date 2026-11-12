package mafisher.quasar.api.gameplay;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.GameplayEffectRuntime;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Server-thread facade for active gameplay effect sessions. Session state lives in the per-level
 * {@link GameplayEffectRuntime}; sessions are resumable through their descriptors.
 */
public final class GameplayEffectService {
    private GameplayEffectService() {
    }

    /**
     * Starts a session and calls {@link GameplayEffectController#onStart} before registering it.
     * A failed start closes the scope and leaves no active session behind.
     */
    public static GameplayEffectHandle start(
        ServerLevel level,
        GameplayEffectSpec spec,
        GameplayEffectController controller
    ) {
        return start(level, spec, controller, EffectPersistenceOptions.DEFAULT);
    }

    /**
     * Starts a session and calls {@link GameplayEffectController#onStart} before registering it.
     * A failed start closes the scope and leaves no active session behind.
     */
    public static GameplayEffectHandle start(
        ServerLevel level,
        GameplayEffectSpec spec,
        GameplayEffectController controller,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(options, "options");
        return EffectRuntimeRegistry.get(level).gameplay().start(spec, controller, options);
    }

    /**
     * Restores one gameplay session with its original identity, start time, and consumer state.
     * Restoring a session that is already active or pending keeps the existing session.
     */
    public static Optional<GameplayEffectHandle> restore(
        ServerLevel level,
        GameplayEffectSpec spec,
        UUID sessionId,
        long startGameTime,
        EffectDescriptor descriptor,
        GameplayEffectController controller
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(controller, "controller");
        return EffectRuntimeRegistry.get(level).gameplay().restore(
            spec, sessionId, startGameTime, descriptor, controller
        );
    }

    /**
     * Scope of one active session, used to reattach persisted child visuals after restore.
     *
     * @return the live scope, or {@code null} when the session is unknown or the level has no runtime
     */
    @Nullable
    public static EffectScope scope(ServerLevel level, GameplayEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? null : runtime.gameplay().scope(handle);
    }

    /**
     * Stops one session or cancels one queued start; unknown handles are no-ops and repeated stops
     * are safe.
     */
    public static void stop(ServerLevel level, GameplayEffectHandle handle, GameplayEffectEndReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(reason, "reason");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.gameplay().stop(handle, reason);
        }
    }

    /** Stops every session in one level and returns how many were active. */
    public static int stopAll(ServerLevel level, GameplayEffectEndReason reason) {
        return stopAll(level, reason, EffectRemovalPolicy.DELETE);
    }

    /**
     * Stops every session in one level. Level unload and shutdown pass
     * {@link EffectRemovalPolicy#KEEP_FOR_RESTORE} so descriptors stay available for restore; an
     * explicit stop passes {@link EffectRemovalPolicy#DELETE}.
     */
    public static int stopAll(
        ServerLevel level,
        GameplayEffectEndReason reason,
        EffectRemovalPolicy policy
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(policy, "policy");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.gameplay().stopAll(reason, policy);
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.gameplay().activeCount();
    }

    /** Number of gameplay starts waiting for capacity in one level. */
    public static int pendingCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.gameplay().pendingCount();
    }

    /** Whether one gameplay session is currently active. */
    public static boolean isActive(ServerLevel level, GameplayEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime != null && runtime.gameplay().isActive(handle);
    }

    /** Whether one gameplay start is queued and will run when capacity becomes available. */
    public static boolean isPending(ServerLevel level, GameplayEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime != null && runtime.gameplay().isPending(handle);
    }

    /** Advances every active session in one level by one server tick. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.gameplay().tick();
        }
    }

    /**
     * Reserves a small share of the block budget for pending rollbacks so live sessions cannot
     * starve them forever when they consume the whole level tick allowance.
     */
    public static int rollbackReserve(int blockChangeBudget, int activeRollbacks) {
        return GameplayEffectRuntime.rollbackReserve(blockChangeBudget, activeRollbacks);
    }
}
