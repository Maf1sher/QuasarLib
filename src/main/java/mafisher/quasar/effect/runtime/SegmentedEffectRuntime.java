package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentHeightProfile;
import mafisher.quasar.api.scale.SegmentedVisualEffectHandle;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import mafisher.quasar.api.scale.StartSegmentedVisualEffectPayload;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.network.StopSegmentedVisualEffectPayload;
import mafisher.quasar.effect.scale.SegmentIds;
import mafisher.quasar.effect.scale.SegmentedEffectNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level coordinator registry for segmented, giant-scale world effects. A coordinator owns its
 * cells directly and reserves a dedicated soft per-level segment budget.
 */
@ApiStatus.Internal
public final class SegmentedEffectRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentedEffectRuntime.class);

    private final ServerLevel level;
    private final EffectStore store;
    private final EffectPacketSink packets;
    private final EffectBudgetSource budgets;
    private final TransformUpdates transforms;
    private final EffectAudioRuntime audio;
    private final Map<UUID, ActiveSegmentedEffect> coordinators = new LinkedHashMap<>();

    public SegmentedEffectRuntime(
        ServerLevel level,
        EffectStore store,
        EffectPacketSink packets,
        EffectBudgetSource budgets,
        TransformUpdates transforms,
        EffectAudioRuntime audio
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.store = Objects.requireNonNull(store, "store");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
        this.transforms = Objects.requireNonNull(transforms, "transforms");
        this.audio = Objects.requireNonNull(audio, "audio");
    }

    /**
     * Starts a segmented effect and returns its root handle. When the level's dedicated segment
     * budget is full, the lowest-priority coordinator is culled first; a coordinator larger than
     * the whole budget is admitted and reported as over budget instead of being rejected.
     */
    public SegmentedVisualEffectHandle start(
        SegmentedVisualEffectSpec spec,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(options, "options");
        return startInternal(spec, UUID.randomUUID(), this.level.getGameTime(), options, null, null);
    }

    /** Starts a segmented effect with a stable identity and absolute start time for restore or cinematics. */
    public SegmentedVisualEffectHandle startWithId(
        SegmentedVisualEffectSpec spec,
        UUID coordinatorId,
        long startGameTime,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        Objects.requireNonNull(options, "options");
        return startInternal(spec, coordinatorId, startGameTime, options, null, null);
    }

    /** Restores a coordinator with its original segment identities and start time. */
    public SegmentedVisualEffectHandle restore(
        SegmentedVisualEffectSpec spec,
        UUID coordinatorId,
        long startGameTime,
        List<UUID> segmentIds,
        EffectDescriptor descriptor,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        Objects.requireNonNull(options, "options");
        return startInternal(spec, coordinatorId, startGameTime, options, segmentIds, descriptor);
    }

    private SegmentedVisualEffectHandle startInternal(
        SegmentedVisualEffectSpec spec,
        UUID coordinatorId,
        long startGameTime,
        EffectPersistenceOptions options,
        List<UUID> restoredSegmentIds,
        EffectDescriptor restoredDescriptor
    ) {
        if (this.level.isClientSide()) {
            throw new IllegalStateException("Segmented visual effects are server-authoritative");
        }
        if (restoredSegmentIds != null) {
            validateRestoredSegmentIds(coordinatorId, spec.segmentCount(), restoredSegmentIds);
        }
        SegmentHeightProfile heights = resolveHeights(spec, restoredDescriptor);

        ActiveSegmentedEffect replaced = this.coordinators.get(coordinatorId);
        int replacedSegments = replaced == null ? 0 : replaced.spec.segmentCount();
        int activeSegments = activeSegmentCount(this.coordinators) - replacedSegments;
        int budget = this.budgets.segments();
        int excess = activeSegments + spec.segmentCount() - budget;
        if (excess > 0) {
            cullForBudget(this.coordinators, coordinatorId, excess);
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
                ? SegmentIds.segmentId(coordinatorId, index)
                : restoredSegmentIds.get(index));
        }
        SegmentedEffectNbt.saveInto(state, spec, heights, segmentIds);
        EffectDescriptor descriptor = restoredDescriptor == null ? EffectDescriptor.builder(
                coordinatorId, EffectKind.SEGMENTED, spec.effectId(), this.level.dimension().location()
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
        this.coordinators.put(coordinatorId, activeEffect);
        if (replaced != null) {
            sendStop(replaced);
            this.audio.stop(coordinatorId);
        }

        try {
            Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
            collectAudience(activeEffect, audience);
            for (ServerPlayer player : audience.values()) {
                sendStart(activeEffect, player);
            }
            this.store.recordStart(descriptor);
            startAudio(coordinatorId, spec, startGameTime, heights);
        } catch (RuntimeException | Error exception) {
            this.coordinators.remove(coordinatorId);
            sendStop(activeEffect);
            this.audio.stop(coordinatorId);
            throw exception;
        }
        return new SegmentedVisualEffectHandle(coordinatorId);
    }

    /**
     * Moves the root transform. The change is applied immediately and delivered to trackers in
     * the next per-tick transform batch. Unknown handles are no-ops.
     */
    public void update(SegmentedVisualEffectHandle handle, EffectTransform transform) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        ActiveSegmentedEffect activeEffect = find(handle);
        if (activeEffect == null) {
            return;
        }

        activeEffect.transform = transform;
        if (activeEffect.descriptor != null) {
            activeEffect.descriptor = activeEffect.descriptor.withTransform(transform);
            this.store.recordUpdate(activeEffect.descriptor);
        }
        this.transforms.queueSegmented(handle.coordinatorId(), transform, activeEffect.trackingPlayers);
    }

    /** Stops every segment and releases the coordinator. Unknown handles are no-ops and repeated stops are safe. */
    public void stop(SegmentedVisualEffectHandle handle) {
        Objects.requireNonNull(handle, "handle");
        ActiveSegmentedEffect removed = this.coordinators.remove(handle.coordinatorId());
        if (removed != null) {
            release(handle.coordinatorId(), removed, EffectRemovalPolicy.DELETE);
        } else {
            this.audio.stop(handle.coordinatorId());
        }
    }

    /** Stops every coordinator in one level and returns how many were active. */
    public int stopAll(EffectRemovalPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        int count = this.coordinators.size();
        List<Map.Entry<UUID, ActiveSegmentedEffect>> entries =
            new ArrayList<>(this.coordinators.entrySet());
        this.coordinators.clear();
        for (Map.Entry<UUID, ActiveSegmentedEffect> entry : entries) {
            release(entry.getKey(), entry.getValue(), policy);
        }
        return count;
    }

    /** Advances coordinator lifetimes and audience membership in one level. */
    public void tick() {
        if (this.coordinators.isEmpty()) {
            return;
        }

        long gameTime = this.level.getGameTime();
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        Iterator<Map.Entry<UUID, ActiveSegmentedEffect>> iterator = this.coordinators.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveSegmentedEffect activeEffect = iterator.next().getValue();
            if (EffectTimeline.isFinished(gameTime, activeEffect.startGameTime,
                activeEffect.spec.durationTicks())) {
                sendStop(activeEffect);
                iterator.remove();
                this.store.recordStop(activeEffect.descriptor.instanceId());
                this.audio.stop(activeEffect.descriptor.instanceId());
                continue;
            }

            audience.clear();
            collectAudience(activeEffect, audience);
            Iterator<UUID> trackers = activeEffect.trackingPlayers.iterator();
            while (trackers.hasNext()) {
                UUID playerId = trackers.next();
                if (!audience.containsKey(playerId)) {
                    ServerPlayer player = this.level.getServer() == null
                        ? null
                        : this.level.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null && player.level() == this.level) {
                        this.packets.sendToPlayer(player,
                            new StopSegmentedVisualEffectPayload(
                                activeEffect.descriptor.instanceId(), this.level.dimension().location()
                            ));
                    }
                    trackers.remove();
                }
            }
            for (ServerPlayer player : audience.values()) {
                if (!activeEffect.trackingPlayers.contains(player.getUUID())) {
                    sendStart(activeEffect, player);
                }
            }
        }
    }

    public int activeCount() {
        return this.coordinators.size();
    }

    /** Number of reserved segment cells in one level. */
    public int activeSegmentCount() {
        return activeSegmentCount(this.coordinators);
    }

    /** Whether one coordinator is currently active; culled or finished handles return {@code false}. */
    public boolean isActive(SegmentedVisualEffectHandle handle) {
        Objects.requireNonNull(handle, "handle");
        ActiveSegmentedEffect effect = find(handle);
        return effect != null && !EffectTimeline.isFinished(
            this.level.getGameTime(), effect.startGameTime, effect.spec.durationTicks()
        );
    }

    /** Drops transient coordinator state without sending packets. */
    public void clear() {
        this.coordinators.clear();
    }

    /**
     * Culls whole coordinators, lowest priority first and oldest first on ties, until {@code excess}
     * segment slots are released. The newcomer is not in the map yet and is never culled.
     */
    private void cullForBudget(
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
                    this.budgets.segments(),
                    remaining
                );
                return;
            }
            ActiveSegmentedEffect victimEffect = active.remove(victim);
            int released = victimEffect == null ? 0 : victimEffect.spec.segmentCount();
            release(victim, victimEffect, EffectRemovalPolicy.DELETE);
            EffectBudgetService.recordCull(this.level);
            LOGGER.info(
                "Culled segmented effect {} in {} to free {} segment slot(s) of a soft budget of {}",
                victim,
                this.level.dimension().location(),
                released,
                this.budgets.segments()
            );
            remaining -= released;
        }
    }

    /**
     * Builds the segmented part of a joining player's resume snapshot. Returned payloads mark the
     * player as tracking; coordinators outside the player's audience are untracked so the next
     * tick does not resend duplicates.
     */
    public List<StartSegmentedVisualEffectPayload> resumePayloads(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (this.coordinators.isEmpty()) {
            return List.of();
        }

        long serverGameTime = this.level.getGameTime();
        List<StartSegmentedVisualEffectPayload> payloads = new ArrayList<>();
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        for (ActiveSegmentedEffect activeEffect : this.coordinators.values()) {
            audience.clear();
            collectAudience(activeEffect, audience);
            if (audience.containsKey(player.getUUID())) {
                payloads.add(currentPayload(activeEffect, serverGameTime));
                activeEffect.trackingPlayers.add(player.getUUID());
            } else {
                activeEffect.trackingPlayers.remove(player.getUUID());
            }
        }
        return List.copyOf(payloads);
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
            UUID expected = SegmentIds.segmentId(coordinatorId, index);
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

    private void startAudio(
        UUID coordinatorId,
        SegmentedVisualEffectSpec spec,
        long startGameTime,
        SegmentHeightProfile heights
    ) {
        if (spec.audio().isEmpty()) {
            return;
        }
        EffectAudioPlan plan = EffectAudioPlan.from(
            coordinatorId,
            this.level.dimension().location(),
            spec.center(),
            startGameTime,
            spec.durationTicks(),
            spec.audio()
        );
        double heightDeviation = heights.maxAbsDeviation(spec.center().y);
        double padding = spec.layout().halfDiagonal() + heightDeviation;
        this.audio.start(plan, spec.audience(), spec.priority(), padding);
    }

    private void collectAudience(
        ActiveSegmentedEffect activeEffect,
        Map<UUID, ServerPlayer> audience
    ) {
        EffectPoint position = activeEffect.transform.position();
        Vec3 center = new Vec3(position.x(), position.y(), position.z());
        double heightDeviation = activeEffect.heights.maxAbsDeviation(position.y());
        double padding = activeEffect.spec.layout().halfDiagonal() + heightDeviation;
        activeEffect.spec.audience().collect(this.level, center, padding, audience);
    }

    private void sendStart(ActiveSegmentedEffect activeEffect, ServerPlayer player) {
        this.packets.sendToPlayer(player, currentPayload(activeEffect));
        activeEffect.trackingPlayers.add(player.getUUID());
    }

    private void sendStop(ActiveSegmentedEffect activeEffect) {
        TrackedPlayers.send(
            this.level,
            this.packets,
            activeEffect.trackingPlayers,
            new StopSegmentedVisualEffectPayload(
                activeEffect.descriptor.instanceId(), this.level.dimension().location()
            )
        );
        activeEffect.trackingPlayers.clear();
    }

    private StartSegmentedVisualEffectPayload currentPayload(ActiveSegmentedEffect activeEffect) {
        return currentPayload(activeEffect, this.level.getGameTime());
    }

    private StartSegmentedVisualEffectPayload currentPayload(
        ActiveSegmentedEffect activeEffect,
        long serverGameTime
    ) {
        EffectPoint position = activeEffect.transform.position();
        return new StartSegmentedVisualEffectPayload(
            activeEffect.spec.effectId(),
            activeEffect.descriptor.instanceId(),
            this.level.dimension().location(),
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

    private SegmentHeightProfile resolveHeights(
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
        return sampleHeights(spec);
    }

    private SegmentHeightProfile sampleHeights(SegmentedVisualEffectSpec spec) {
        SegmentGridLayout layout = spec.layout();
        double[] values = new double[layout.columns() * layout.rows()];
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                Vec3 offset = layout.offset(column, row, 0, spec.yaw());
                int x = Mth.floor(spec.center().x + offset.x);
                int z = Mth.floor(spec.center().z + offset.z);
                values[column + row * layout.columns()] = this.level.hasChunkAt(x, z)
                    ? this.level.getHeight(spec.terrain().source().heightmap(), x, z)
                        + spec.terrain().verticalOffset()
                    : spec.center().y;
            }
        }
        return SegmentHeightProfile.of(layout.columns(), layout.rows(), values);
    }

    private ActiveSegmentedEffect find(SegmentedVisualEffectHandle handle) {
        return this.coordinators.get(handle.coordinatorId());
    }

    private void release(
        UUID coordinatorId,
        ActiveSegmentedEffect effect,
        EffectRemovalPolicy policy
    ) {
        if (effect == null) {
            return;
        }
        sendStop(effect);
        if (policy.deletesDescriptor()) {
            this.store.recordStop(coordinatorId);
        }
        this.audio.stop(coordinatorId);
    }

    private static int activeSegmentCount(Map<UUID, ActiveSegmentedEffect> active) {
        int count = 0;
        for (ActiveSegmentedEffect effect : active.values()) {
            count += effect.spec.segmentCount();
        }
        return count;
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
            this.descriptor = descriptor;
            this.heights = heights;
        }
    }
}
