package mafisher.quasar.api.effect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.api.scale.SegmentLink;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-thread facade for starting, moving, tracking, and cancelling transient client-rendered
 * effects. State belongs to the per-level {@link LevelEffectRuntime}.
 */
public final class VisualEffectService {
    private VisualEffectService() {
    }

    public static VisualEffectStartResult start(ServerLevel level, VisualEffectSpec spec) {
        return start(level, spec, null, EffectPersistenceOptions.DEFAULT);
    }

    /**
     * Starts a visual effect with a legacy coordinator link. New segmented effects use
     * {@code SegmentedVisualEffectService}, which sends the whole grid in one aggregated lifecycle.
     */
    public static VisualEffectStartResult start(
        ServerLevel level,
        VisualEffectSpec spec,
        SegmentLink segmentLink
    ) {
        return start(level, spec, segmentLink, EffectPersistenceOptions.DEFAULT);
    }

    public static VisualEffectStartResult start(
        ServerLevel level,
        VisualEffectSpec spec,
        EffectPersistenceOptions options
    ) {
        return start(level, spec, null, options);
    }

    public static VisualEffectStartResult start(
        ServerLevel level,
        VisualEffectSpec spec,
        SegmentLink segmentLink,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(options, "options");
        return EffectRuntimeRegistry.get(level).visuals().start(spec, segmentLink, options);
    }

    /**
     * Starts a visual effect with a caller-supplied identity and absolute start time. Used by
     * cinematic timelines and persistence restore, which must preserve ages across restarts.
     */
    public static VisualEffectStartResult startWithId(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        EffectPersistenceOptions options
    ) {
        return startWithId(level, spec, instanceId, startGameTime, options, null);
    }

    public static VisualEffectStartResult startWithId(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        EffectPersistenceOptions options,
        SegmentLink segmentLink
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(options, "options");
        return EffectRuntimeRegistry.get(level).visuals().startWithId(
            spec, instanceId, startGameTime, options, segmentLink
        );
    }

    /** Whether one visual is currently active; culled or finished handles return {@code false}. */
    public static boolean isActive(ServerLevel level, VisualEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime != null && runtime.visuals().isActive(handle);
    }

    /**
     * Adopts active visuals whose descriptor names {@code ownerSessionId}. Used when a deferred
     * restored gameplay session becomes active after its child visuals were restored.
     */
    public static void adoptOwnedBy(ServerLevel level, UUID ownerSessionId, EffectScope scope) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(ownerSessionId, "ownerSessionId");
        Objects.requireNonNull(scope, "scope");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.visuals().adoptOwnedBy(ownerSessionId, scope);
        }
    }

    /**
     * Restores one standalone visual with its original identity, start time, and transform. The
     * handle may be inactive when the soft budget culled it; query {@link #isActive}.
     */
    public static VisualEffectStartResult restore(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        EffectDescriptor descriptor
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(instanceId, "instanceId");
        return EffectRuntimeRegistry.get(level).visuals().restore(
            spec, instanceId, startGameTime, descriptor
        );
    }

    /** Restores one legacy coordinator-linked visual without recording a standalone descriptor. */
    public static VisualEffectStartResult restoreSegment(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        SegmentLink link
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(instanceId, "instanceId");
        return EffectRuntimeRegistry.get(level).visuals().restoreSegment(
            spec, instanceId, startGameTime, link
        );
    }

    /**
     * Updates the world transform of an active visual. The change is applied immediately and
     * delivered to trackers in the next per-tick transform batch.
     */
    public static void update(ServerLevel level, VisualEffectHandle handle, EffectTransform transform) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.visuals().update(handle, transform);
        }
    }

    /**
     * Replaces the server-authored parameter overrides of an active visual. The change is
     * persisted and delivered to tracking players in the next coalesced batch. Curves are sampled
     * on the client from progress; only overrides are sent.
     */
    public static void updateParameters(
        ServerLevel level,
        VisualEffectHandle handle,
        EffectParameterState parameters
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(parameters, "parameters");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.visuals().updateParameters(handle, parameters);
        }
    }

    /** Sends current parameter overrides to a player who already tracks the matching visuals. */
    public static void sendParameterSnapshots(ServerLevel level, ServerPlayer player) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.visuals().sendParameterSnapshots(player);
        }
    }

    /** Player IDs currently tracking one effect; empty for unknown, finished, or untracked handles. */
    public static List<UUID> trackingPlayers(ServerLevel level, VisualEffectHandle handle) {
        List<UUID> players = new ArrayList<>();
        collectTrackingPlayers(level, handle, players);
        return List.copyOf(players);
    }

    /** Adds player IDs tracking one effect to {@code target}; no-op for unknown or finished handles. */
    public static void collectTrackingPlayers(
        ServerLevel level,
        VisualEffectHandle handle,
        Collection<UUID> target
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(target, "target");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.visuals().collectTrackingPlayers(handle, target);
        }
    }

    /** Sends one payload to every listed player that is still present in this level. */
    public static void sendToTrackingPlayers(
        ServerLevel level,
        Collection<UUID> playerIds,
        CustomPacketPayload payload
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(playerIds, "playerIds");
        Objects.requireNonNull(payload, "payload");
        EffectRuntimeRegistry.get(level).visuals().sendToTrackingPlayers(playerIds, payload);
    }

    public static void stop(ServerLevel level, VisualEffectHandle handle) {
        stop(level, handle, EffectRemovalPolicy.DELETE);
    }

    /** Stops an instance; {@link EffectRemovalPolicy#KEEP_FOR_RESTORE} keeps its descriptor. */
    public static void stop(ServerLevel level, VisualEffectHandle handle, EffectRemovalPolicy policy) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(policy, "policy");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.visuals().stop(handle, policy);
        }
    }

    /** Keeps active visual state in sync with players who enter or leave its audience. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.visuals().tick();
        }
    }

    /** Number of active visual effects in one level. */
    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.visuals().activeCount();
    }

    /**
     * Builds and applies a resume snapshot for one joining player. Returned effects are marked as
     * tracked and effects outside the player's audience are untracked, so the next tick does not
     * resend duplicate starts. Callers are responsible for sending the returned payloads.
     */
    public static List<StartVisualEffectPayload> resumePayloads(ServerLevel level, ServerPlayer player) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? List.of() : runtime.visuals().resumePayloads(player);
    }

    /** Drops transient runtime state for a level without deleting persisted descriptors. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.visuals().clear();
            runtime.transforms().clear();
            runtime.audio().clear();
            runtime.budget().clear();
        }
    }
}
