package mafisher.quasar.api.scale;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import mafisher.quasar.effect.scale.SegmentIds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-thread facade for segmented, giant-scale world effects. The per-level coordinator and
 * budget state belongs to {@link LevelEffectRuntime}.
 */
public final class SegmentedVisualEffectService {
    private SegmentedVisualEffectService() {
    }

    /**
     * Starts a segmented effect and returns its root handle. When the level's dedicated segment
     * budget is full, the lowest-priority coordinator is culled first; a coordinator larger than
     * the whole budget is admitted and reported as over budget instead of being rejected.
     */
    public static SegmentedVisualEffectHandle start(ServerLevel level, SegmentedVisualEffectSpec spec) {
        return start(level, spec, EffectPersistenceOptions.DEFAULT);
    }

    public static SegmentedVisualEffectHandle start(
        ServerLevel level,
        SegmentedVisualEffectSpec spec,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(options, "options");
        return EffectRuntimeRegistry.get(level).segmented().start(spec, options);
    }

    /** Starts a segmented effect with a stable identity and absolute start time for restore or cinematics. */
    public static SegmentedVisualEffectHandle startWithId(
        ServerLevel level,
        SegmentedVisualEffectSpec spec,
        UUID coordinatorId,
        long startGameTime,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        Objects.requireNonNull(options, "options");
        return EffectRuntimeRegistry.get(level).segmented().startWithId(
            spec, coordinatorId, startGameTime, options
        );
    }

    public static List<UUID> restoreSegmentIds(UUID coordinatorId, int segmentCount) {
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        List<UUID> ids = new ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) {
            ids.add(segmentId(coordinatorId, index));
        }
        return List.copyOf(ids);
    }

    /** Stable segment identity derived from its coordinator and cell index. */
    public static UUID segmentId(UUID coordinatorId, int index) {
        return SegmentIds.segmentId(coordinatorId, index);
    }

    /** Restores a coordinator with its original segment identities and start time. */
    public static SegmentedVisualEffectHandle restore(
        ServerLevel level,
        SegmentedVisualEffectSpec spec,
        UUID coordinatorId,
        long startGameTime,
        List<UUID> segmentIds,
        EffectDescriptor descriptor
    ) {
        return restore(
            level, spec, coordinatorId, startGameTime, segmentIds, descriptor,
            EffectPersistenceOptions.DEFAULT
        );
    }

    public static SegmentedVisualEffectHandle restore(
        ServerLevel level,
        SegmentedVisualEffectSpec spec,
        UUID coordinatorId,
        long startGameTime,
        List<UUID> segmentIds,
        EffectDescriptor descriptor,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        Objects.requireNonNull(options, "options");
        return EffectRuntimeRegistry.get(level).segmented().restore(
            spec, coordinatorId, startGameTime, segmentIds, descriptor, options
        );
    }

    /**
     * Moves the root transform. The change is applied immediately and delivered to trackers in
     * the next per-tick transform batch. Unknown handles are no-ops.
     */
    public static void update(
        ServerLevel level,
        SegmentedVisualEffectHandle handle,
        EffectTransform transform
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.segmented().update(handle, transform);
        }
    }

    /** Stops every segment and releases the coordinator. Unknown handles are no-ops and repeated stops are safe. */
    public static void stop(ServerLevel level, SegmentedVisualEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.segmented().stop(handle);
        }
    }

    /** Stops every coordinator in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        return stopAll(level, EffectRemovalPolicy.DELETE);
    }

    /**
     * Stops every coordinator in one level. Level unload and shutdown pass
     * {@link EffectRemovalPolicy#KEEP_FOR_RESTORE} so the persisted descriptors survive; an
     * explicit stop passes {@link EffectRemovalPolicy#DELETE}.
     */
    public static int stopAll(ServerLevel level, EffectRemovalPolicy policy) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(policy, "policy");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.segmented().stopAll(policy);
    }

    /** Advances coordinator lifetimes and audience membership in one level. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.segmented().tick();
        }
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.segmented().activeCount();
    }

    /** Number of reserved segment cells in one level. */
    public static int activeSegmentCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.segmented().activeSegmentCount();
    }

    /** Whether one coordinator is currently active; culled or finished handles return {@code false}. */
    public static boolean isActive(ServerLevel level, SegmentedVisualEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime != null && runtime.segmented().isActive(handle);
    }

    /**
     * Builds the segmented part of a joining player's resume snapshot. Returned payloads mark the
     * player as tracking; coordinators outside the player's audience are untracked so the next
     * tick does not resend duplicates.
     */
    public static List<StartSegmentedVisualEffectPayload> resumePayloads(
        ServerLevel level,
        ServerPlayer player
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? List.of() : runtime.segmented().resumePayloads(player);
    }
}
