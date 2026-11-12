package mafisher.quasar.api.cinematic;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Server-thread facade for cinematic timelines. Sessions, child visuals, and audio belong to the
 * per-level {@link LevelEffectRuntime}.
 */
public final class CinematicEffectService {
    private CinematicEffectService() {
    }

    /**
     * Starts a relative timeline at a world anchor. JSON-authored timelines use this overload;
     * code-authored timelines with absolute positions use {@link #start(ServerLevel, CinematicTimeline)}.
     */
    public static CinematicEffectStartResult start(
        ServerLevel level,
        CinematicTimeline timeline,
        Vec3 anchor
    ) {
        return start(level, timeline, anchor, EffectPersistenceOptions.DEFAULT);
    }

    public static CinematicEffectStartResult start(
        ServerLevel level,
        CinematicTimeline timeline,
        Vec3 anchor,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(options, "options");
        return EffectRuntimeRegistry.get(level).cinematics().start(timeline, anchor, options);
    }

    /**
     * Starts a timeline, executes its tick-zero cues immediately, and applies tick-zero transform
     * tracks. When the level budget is full, the lowest-priority cinematic is closed first; a
     * lower-priority newcomer returns an inactive handle instead of throwing.
     */
    public static CinematicEffectStartResult start(ServerLevel level, CinematicTimeline timeline) {
        return start(level, timeline, Vec3.ZERO, EffectPersistenceOptions.DEFAULT);
    }

    public static CinematicEffectStartResult start(
        ServerLevel level,
        CinematicTimeline timeline,
        EffectPersistenceOptions options
    ) {
        return start(level, timeline, Vec3.ZERO, options);
    }

    /** Restores a cinematic session and fast-forwards it to the current age without replaying past cues. */
    public static CinematicEffectStartResult restore(
        ServerLevel level,
        CinematicTimeline timeline,
        UUID sessionId,
        long startGameTime,
        EffectDescriptor descriptor
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(descriptor, "descriptor");
        return EffectRuntimeRegistry.get(level).cinematics().restore(
            timeline, sessionId, startGameTime, descriptor
        );
    }

    /** Advances one level's cinematic sessions. A failed session is logged, closed, and removed. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.cinematics().tick();
        }
    }

    /** Stops one session; unknown handles are no-ops and repeated stops are safe. */
    public static void stop(ServerLevel level, CinematicEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.cinematics().stop(handle);
        }
    }

    /** Stops every cinematic session in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        return stopAll(level, EffectRemovalPolicy.DELETE);
    }

    /**
     * Stops every cinematic session in one level. Level unload and shutdown pass
     * {@link EffectRemovalPolicy#KEEP_FOR_RESTORE} to preserve descriptors for restore.
     */
    public static int stopAll(ServerLevel level, EffectRemovalPolicy policy) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(policy, "policy");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.cinematics().stopAll(policy);
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.cinematics().activeCount();
    }

    /** Whether one cinematic session is currently active; culled or finished handles return {@code false}. */
    public static boolean isActive(ServerLevel level, CinematicEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime != null && runtime.cinematics().isActive(handle);
    }
}
