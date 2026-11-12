package mafisher.quasar.effect.scale;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.EffectBudgets;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectRemovalPolicy;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.EffectTransformBatchService;
import mafisher.quasar.effect.LevelStateSlot;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.audio.EffectAudioPlan;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.network.StartSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.StopSegmentedVisualEffectPayload;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread coordinator for segmented, giant-scale world effects. A coordinator owns its cells directly and
 * reserves a dedicated soft per-level segment budget, so a large grid neither consumes standalone visual slots nor can
 * be evicted by standalone starts. When the budget is full, the lowest-priority coordinator is culled first. Start,
 * stop, and updates are aggregated per coordinator.
 */
public final class SegmentedVisualEffectService {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final LevelStateSlot<SegmentState> STATE = LevelStateSlot.create(SegmentState::new);
    private static Integer budgetOverride;

    private SegmentedVisualEffectService() {
    }

    /**
     * Starts a segmented effect and returns its root handle. When the level's dedicated segment budget is full, the
     * lowest-priority coordinator is culled first; a coordinator larger than the whole budget is admitted and reported
     * as over budget instead of being rejected.
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
        return startInternal(level, spec, UUID.randomUUID(), level.getGameTime(), options, null);
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
        return startInternal(level, spec, coordinatorId, startGameTime, options, null);
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

    private static void validateRestoredSegmentIds(UUID coordinatorId, int count, List<UUID> ids) {
        if (ids.size() != count) {
            throw new IllegalArgumentException(
                "Segmented effect " + coordinatorId + " expected " + count
                    + " segment IDs but found " + ids.size()
            );
        }
        Set<UUID> seen = new HashSet<>(count);
        for (int index = 0; index < count; index++) {
            UUID expected = segmentId(coordinatorId, index);
            UUID actual = ids.get(index);
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException(
                    "Segmented effect " + coordinatorId + " has segment ID " + actual
                        + " at index " + index + ", expected " + expected
                );
            }
            if (!seen.add(actual)) {
                throw new IllegalArgumentException(
                    "Segmented effect " + coordinatorId + " repeats segment ID " + actual
                        + " at index " + index
                );
            }
        }
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
        return restore(level, spec, coordinatorId, startGameTime, segmentIds, descriptor,
            EffectPersistenceOptions.DEFAULT);
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
        return startInternal(level, spec, coordinatorId, startGameTime, options, segmentIds, descriptor);
    }

    private static SegmentedVisualEffectHandle startInternal(
        ServerLevel level,
        SegmentedVisualEffectSpec spec,
        UUID coordinatorId,
        long startGameTime,
        EffectPersistenceOptions options,
        List<UUID> restoredSegmentIds
    ) {
        return startInternal(level, spec, coordinatorId, startGameTime, options, restoredSegmentIds, null);
    }

    private static SegmentedVisualEffectHandle startInternal(
        ServerLevel level,
        SegmentedVisualEffectSpec spec,
        UUID coordinatorId,
        long startGameTime,
        EffectPersistenceOptions options,
        List<UUID> restoredSegmentIds,
        EffectDescriptor restoredDescriptor
    ) {
        if (level.isClientSide()) {
            throw new IllegalStateException("Segmented visual effects are server-authoritative");
        }
        if (restoredSegmentIds != null) {
            validateRestoredSegmentIds(coordinatorId, spec.segmentCount(), restoredSegmentIds);
        }
        SegmentHeightProfile heights = resolveHeights(level, spec, restoredDescriptor);

        SegmentState segmentState = STATE.get(level);
        Map<UUID, ActiveSegmentedEffect> active = segmentState.coordinators;
        ActiveSegmentedEffect replaced = active.get(coordinatorId);
        int replacedSegments = replaced == null ? 0 : replaced.spec.segmentCount();
        int activeSegments = activeSegmentCount(active) - replacedSegments;
        int budget = budget();
        int excess = activeSegments + spec.segmentCount() - budget;
        if (excess > 0) {
            cullForBudget(level, active, coordinatorId, excess);
        }

        EffectTransform transform = new EffectTransform(
            new EffectPoint(spec.center().x, spec.center().y, spec.center().z),
            spec.yaw(),
            spec.pitch(),
            spec.roll()
        );
        CompoundTag state = options.state();
        List<UUID> segmentIds = new ArrayList<>(spec.segmentCount());
        for (int index = 0; index < spec.segmentCount(); index++) {
            segmentIds.add(restoredSegmentIds == null
                ? segmentId(coordinatorId, index)
                : restoredSegmentIds.get(index));
        }
        SegmentedEffectNbt.saveInto(state, spec, heights, segmentIds);
        EffectDescriptor descriptor = restoredDescriptor == null ? EffectDescriptor.builder(
                coordinatorId, EffectKind.SEGMENTED, spec.effectId(), level.dimension().location()
            )
            .placement(transform, spec.segmentScale())
            .timing(spec.seed(), startGameTime, spec.durationTicks())
            .audience(spec.audience())
            .capabilities(options.capabilities())
            .persistence(options.persistence())
            .state(state)
            .build() : restoredDescriptor;

        ActiveSegmentedEffect activeEffect = new ActiveSegmentedEffect(
            spec,
            transform,
            startGameTime,
            descriptor,
            heights
        );
        active.put(coordinatorId, activeEffect);
        if (replaced != null) {
            sendStop(level, replaced);
            EffectAudioService.stop(level, coordinatorId);
        }

        try {
            Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
            collectAudience(level, activeEffect, audience);
            for (ServerPlayer player : audience.values()) {
                sendStart(level, activeEffect, player);
            }
            EffectPersistenceService.recordStart(level, descriptor);
            startAudio(level, coordinatorId, spec, startGameTime);
        } catch (RuntimeException | Error exception) {
            active.remove(coordinatorId);
            sendStop(level, activeEffect);
            EffectAudioService.stop(level, coordinatorId);
            if (active.isEmpty()) {
                STATE.remove(level);
            }
            throw exception;
        }
        return new SegmentedVisualEffectHandle(coordinatorId);
    }

    /**
     * Moves the root transform. The change is applied immediately and delivered to trackers in
     * the next per-tick transform batch. Unknown handles are no-ops.
     */
    public static void update(ServerLevel level, SegmentedVisualEffectHandle handle, EffectTransform transform) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        ActiveSegmentedEffect activeEffect = find(level, handle);
        if (activeEffect == null) {
            return;
        }

        activeEffect.transform = transform;
        if (activeEffect.descriptor != null) {
            activeEffect.descriptor = activeEffect.descriptor.withTransform(transform);
            EffectPersistenceService.recordUpdate(level, activeEffect.descriptor);
        }
        EffectTransformBatchService.queueSegmented(
            level, handle.coordinatorId(), transform, activeEffect.trackingPlayers
        );
    }

    /** Stops every segment and releases the coordinator. Unknown handles are no-ops and repeated stops are safe. */
    public static void stop(ServerLevel level, SegmentedVisualEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        SegmentState state = STATE.peek(level);
        if (state == null) {
            return;
        }
        ActiveSegmentedEffect removed = state.coordinators.remove(handle.coordinatorId());
        if (removed != null) {
            release(level, handle.coordinatorId(), removed, EffectRemovalPolicy.DELETE);
        } else {
            EffectAudioService.stop(level, handle.coordinatorId());
        }
        if (state.coordinators.isEmpty()) {
            STATE.remove(level);
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
        SegmentState state = STATE.peek(level);
        if (state == null) {
            return 0;
        }
        STATE.remove(level);
        int count = state.coordinators.size();
        for (Map.Entry<UUID, ActiveSegmentedEffect> entry : state.coordinators.entrySet()) {
            release(level, entry.getKey(), entry.getValue(), policy);
        }
        state.coordinators.clear();
        return count;
    }

    /** Advances coordinator lifetimes and audience membership in one level. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        SegmentState state = STATE.peek(level);
        if (state == null || state.coordinators.isEmpty()) {
            if (state != null) {
                STATE.remove(level);
            }
            return;
        }
        Map<UUID, ActiveSegmentedEffect> active = state.coordinators;

        long gameTime = level.getGameTime();
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        Iterator<Map.Entry<UUID, ActiveSegmentedEffect>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveSegmentedEffect activeEffect = iterator.next().getValue();
            if (EffectTimeline.isFinished(gameTime, activeEffect.startGameTime,
                activeEffect.spec.durationTicks())) {
                sendStop(level, activeEffect);
                iterator.remove();
                EffectPersistenceService.recordStop(level, activeEffect.descriptor.instanceId());
                EffectAudioService.stop(level, activeEffect.descriptor.instanceId());
                continue;
            }

            audience.clear();
            collectAudience(level, activeEffect, audience);
            Iterator<UUID> trackers = activeEffect.trackingPlayers.iterator();
            while (trackers.hasNext()) {
                UUID playerId = trackers.next();
                if (!audience.containsKey(playerId)) {
                    ServerPlayer player = level.getServer() == null
                        ? null
                        : level.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null && player.level() == level) {
                        PacketDistributor.sendToPlayer(player,
                            new StopSegmentedVisualEffectPayload(
                                activeEffect.descriptor.instanceId(), level.dimension().location()
                            ));
                    }
                    trackers.remove();
                }
            }
            for (ServerPlayer player : audience.values()) {
                if (!activeEffect.trackingPlayers.contains(player.getUUID())) {
                    sendStart(level, activeEffect, player);
                }
            }
        }
        if (active.isEmpty()) {
            STATE.remove(level);
        }
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        SegmentState state = STATE.peek(level);
        return state == null ? 0 : state.coordinators.size();
    }

    /** Number of reserved segment cells in one level. */
    public static int activeSegmentCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        SegmentState state = STATE.peek(level);
        return state == null ? 0 : activeSegmentCount(state.coordinators);
    }

    /** Whether one coordinator is currently active; culled or finished handles return {@code false}. */
    public static boolean isActive(ServerLevel level, SegmentedVisualEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        ActiveSegmentedEffect effect = find(level, handle);
        return effect != null && !EffectTimeline.isFinished(
            level.getGameTime(), effect.startGameTime, effect.spec.durationTicks()
        );
    }

    static void overrideBudgetForTesting(int budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("Segment budget must be positive");
        }
        budgetOverride = budget;
    }

    static void resetBudgetForTesting() {
        budgetOverride = null;
    }

    private static int budget() {
        return budgetOverride == null ? EffectBudgets.segments() : budgetOverride;
    }

    /**
     * Culls whole coordinators, lowest priority first and oldest first on ties, until {@code excess}
     * segment slots are released. The newcomer is not in the map yet and is never culled.
     */
    private static void cullForBudget(
        ServerLevel level,
        Map<UUID, ActiveSegmentedEffect> active,
        UUID newcomerId,
        int excess
    ) {
        int remaining = excess;
        while (remaining > 0) {
            UUID victim = EffectBudgetService.lowestPriority(
                active, effect -> effect.spec.priority().weight(), newcomerId
            );
            if (victim == null) {
                LOGGER.warn(
                    "Admitted segmented effect over the soft segment budget of {}: {} slot(s) over",
                    budget(),
                    remaining
                );
                return;
            }
            ActiveSegmentedEffect victimEffect = active.remove(victim);
            int released = victimEffect == null ? 0 : victimEffect.spec.segmentCount();
            release(level, victim, victimEffect, EffectRemovalPolicy.DELETE);
            EffectBudgetService.recordCull(level);
            LOGGER.info(
                "Culled segmented effect {} in {} to free {} segment slot(s) of a soft budget of {}",
                victim,
                level.dimension().location(),
                released,
                budget()
            );
            remaining -= released;
        }
    }

    /**
     * Builds the segmented part of a joining player's resume snapshot. Returned payloads mark the player as tracking;
     * coordinators outside the player's audience are untracked so the next tick does not resend duplicates.
     */
    public static List<StartSegmentedVisualEffectPayload> resumePayloads(ServerLevel level, ServerPlayer player) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        SegmentState state = STATE.peek(level);
        if (state == null || state.coordinators.isEmpty()) {
            return List.of();
        }

        long serverGameTime = level.getGameTime();
        List<StartSegmentedVisualEffectPayload> payloads = new ArrayList<>();
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        for (ActiveSegmentedEffect activeEffect : state.coordinators.values()) {
            audience.clear();
            collectAudience(level, activeEffect, audience);
            if (audience.containsKey(player.getUUID())) {
                payloads.add(currentPayload(level, activeEffect, serverGameTime));
                activeEffect.trackingPlayers.add(player.getUUID());
            } else {
                activeEffect.trackingPlayers.remove(player.getUUID());
            }
        }
        return List.copyOf(payloads);
    }

    private static void startAudio(
        ServerLevel level,
        UUID coordinatorId,
        SegmentedVisualEffectSpec spec,
        long startGameTime
    ) {
        if (spec.audio().isEmpty()) {
            return;
        }
        EffectAudioPlan plan = EffectAudioPlan.from(
            coordinatorId,
            level.dimension().location(),
            spec.center(),
            startGameTime,
            spec.durationTicks(),
            spec.audio()
        );
        EffectAudioService.start(level, plan, spec.audience(), spec.priority());
    }

    private static void collectAudience(
        ServerLevel level,
        ActiveSegmentedEffect activeEffect,
        Map<UUID, ServerPlayer> audience
    ) {
        EffectPoint position = activeEffect.transform.position();
        Vec3 center = new Vec3(position.x(), position.y(), position.z());
        double heightDeviation = activeEffect.heights.maxAbsDeviation(position.y());
        double padding = activeEffect.spec.layout().halfDiagonal() + heightDeviation;
        activeEffect.spec.audience().collect(level, center, padding, audience);
    }

    private static void sendStart(
        ServerLevel level,
        ActiveSegmentedEffect activeEffect,
        ServerPlayer player
    ) {
        PacketDistributor.sendToPlayer(player, currentPayload(level, activeEffect));
        activeEffect.trackingPlayers.add(player.getUUID());
    }

    private static void sendStop(ServerLevel level, ActiveSegmentedEffect activeEffect) {
        if (activeEffect.trackingPlayers.isEmpty()) {
            return;
        }
        VisualEffectService.sendToTrackingPlayers(
            level,
            activeEffect.trackingPlayers,
            new StopSegmentedVisualEffectPayload(
                activeEffect.descriptor.instanceId(), level.dimension().location()
            )
        );
        activeEffect.trackingPlayers.clear();
    }

    private static StartSegmentedVisualEffectPayload currentPayload(
        ServerLevel level,
        ActiveSegmentedEffect activeEffect
    ) {
        return currentPayload(level, activeEffect, level.getGameTime());
    }

    private static StartSegmentedVisualEffectPayload currentPayload(
        ServerLevel level,
        ActiveSegmentedEffect activeEffect,
        long serverGameTime
    ) {
        EffectPoint position = activeEffect.transform.position();
        return new StartSegmentedVisualEffectPayload(
            activeEffect.spec.effectId(),
            activeEffect.descriptor.instanceId(),
            level.dimension().location(),
            position.x(),
            position.y(),
            position.z(),
            activeEffect.transform.yaw(),
            activeEffect.transform.pitch(),
            activeEffect.transform.roll(),
            activeEffect.spec.segmentScale(),
            activeEffect.spec.columns(),
            activeEffect.spec.rows(),
            activeEffect.spec.spacing(),
            activeEffect.spec.durationTicks(),
            activeEffect.startGameTime,
            serverGameTime,
            activeEffect.spec.seed(),
            activeEffect.spec.layers(),
            activeEffect.spec.layerSpacing(),
            activeEffect.heights
        );
    }

    private static SegmentHeightProfile resolveHeights(
        ServerLevel level,
        SegmentedVisualEffectSpec spec,
        EffectDescriptor restoredDescriptor
    ) {
        if (restoredDescriptor != null) {
            SegmentHeightProfile stored = SegmentedEffectNbt.readHeights(restoredDescriptor.state());
            if (!stored.isEmpty()) {
                return stored;
            }
        }
        if (spec.terrain().isNone()) {
            return SegmentHeightProfile.NONE;
        }
        return sampleHeights(level, spec);
    }

    private static SegmentHeightProfile sampleHeights(ServerLevel level, SegmentedVisualEffectSpec spec) {
        SegmentGridLayout layout = spec.layout();
        double[] values = new double[layout.columns() * layout.rows()];
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                Vec3 offset = layout.offset(column, row, 0, spec.yaw());
                int x = Mth.floor(spec.center().x + offset.x);
                int z = Mth.floor(spec.center().z + offset.z);
                values[column + row * layout.columns()] = level.hasChunkAt(x, z)
                    ? level.getHeight(spec.terrain().source().heightmap(), x, z)
                        + spec.terrain().verticalOffset()
                    : spec.center().y;
            }
        }
        return SegmentHeightProfile.of(layout.columns(), layout.rows(), values);
    }

    private static ActiveSegmentedEffect find(ServerLevel level, SegmentedVisualEffectHandle handle) {
        SegmentState state = STATE.peek(level);
        return state == null ? null : state.coordinators.get(handle.coordinatorId());
    }

    private static void release(
        ServerLevel level,
        UUID coordinatorId,
        ActiveSegmentedEffect effect,
        EffectRemovalPolicy policy
    ) {
        if (effect == null) {
            return;
        }
        sendStop(level, effect);
        if (policy.deletesDescriptor()) {
            EffectPersistenceService.recordStop(level, coordinatorId);
        }
        EffectAudioService.stop(level, coordinatorId);
    }

    private static int activeSegmentCount(Map<UUID, ActiveSegmentedEffect> active) {
        int count = 0;
        for (ActiveSegmentedEffect effect : active.values()) {
            count += effect.spec.segmentCount();
        }
        return count;
    }

    private static final class SegmentState {
        private final Map<UUID, ActiveSegmentedEffect> coordinators = new LinkedHashMap<>();
    }

    private static final class ActiveSegmentedEffect {
        private final SegmentedVisualEffectSpec spec;
        private final long startGameTime;
        private final SegmentHeightProfile heights;
        private final Set<UUID> trackingPlayers = new HashSet<>();
        private EffectTransform transform;
        private EffectDescriptor descriptor;

        private ActiveSegmentedEffect(
            SegmentedVisualEffectSpec spec,
            EffectTransform transform,
            long startGameTime,
            EffectDescriptor descriptor,
            SegmentHeightProfile heights
        ) {
            this.spec = spec;
            this.transform = transform;
            this.startGameTime = startGameTime;
            this.heights = heights;
            this.descriptor = descriptor;
        }
    }
}
