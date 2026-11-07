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
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.EffectBudgets;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.audio.EffectAudioPlan;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.network.StartSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.StopSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateSegmentedVisualEffectPayload;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
    /** Default dedicated per-level budget; segmented cells never count against the standalone visual cap. */
    public static final int MAX_ACTIVE_SEGMENTS_PER_LEVEL = SegmentedEffectLimits.MAX_SEGMENTS_PER_LEVEL;
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, Map<UUID, ActiveSegmentedEffect>> ACTIVE_COORDINATORS = new WeakHashMap<>();
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
        if (restoredSegmentIds != null && restoredSegmentIds.size() != spec.segmentCount()) {
            LOGGER.warn("Could not restore segmented effect {}: expected {} segment IDs but found {}",
                coordinatorId, spec.segmentCount(), restoredSegmentIds.size());
            return null;
        }
        SegmentHeightProfile heights = resolveHeights(level, spec, restoredDescriptor);

        Map<UUID, ActiveSegmentedEffect> active = activeSegments(level);
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
        state.putInt("columns", spec.columns());
        state.putInt("rows", spec.rows());
        state.putInt("layers", spec.layers());
        state.putDouble("spacing", spec.spacing());
        state.putDouble("layer_spacing", spec.layerSpacing());
        state.putFloat("segment_scale", spec.segmentScale());
        state.putString("terrain_source", spec.terrain().source().name());
        state.putDouble("terrain_offset", spec.terrain().verticalOffset());
        saveHeights(state, heights);
        ListTag ids = new ListTag();
        for (int index = 0; index < spec.segmentCount(); index++) {
            ids.add(StringTag.valueOf(segmentId(coordinatorId, index).toString()));
        }
        state.put("segment_ids", ids);
        state.putString("priority", spec.priority().name());
        EffectAudioNbt.saveInto(state, spec.audio());
        EffectDescriptor descriptor = restoredDescriptor == null ? new EffectDescriptor(
            coordinatorId,
            EffectKind.SEGMENTED,
            spec.effectId(),
            level.dimension().location(),
            transform,
            spec.segmentScale(),
            spec.seed(),
            startGameTime,
            spec.durationTicks(),
            spec.audience(),
            options.capabilities(),
            options.persistence(),
            null,
            state
        ) : restoredDescriptor;

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
            throw exception;
        }
        return new SegmentedVisualEffectHandle(coordinatorId);
    }

    /** Moves the root transform and sends one aggregated update per tracking player. Unknown handles are no-ops. */
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
        VisualEffectService.sendToTrackingPlayers(
            level,
            activeEffect.trackingPlayers,
            UpdateSegmentedVisualEffectPayload.of(
                handle.coordinatorId(), level.dimension().location(), transform, level.getGameTime()
            )
        );
    }

    /** Stops every segment and releases the coordinator. Unknown handles are no-ops and repeated stops are safe. */
    public static void stop(ServerLevel level, SegmentedVisualEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.get(level);
        if (active == null) {
            return;
        }
        ActiveSegmentedEffect removed = active.remove(handle.coordinatorId());
        if (removed != null) {
            sendStop(level, removed);
            EffectPersistenceService.recordStop(level, handle.coordinatorId());
        }
        EffectAudioService.stop(level, handle.coordinatorId());
        if (active.isEmpty()) {
            ACTIVE_COORDINATORS.remove(level);
        }
    }

    /** Stops every coordinator in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        return stopAll(level, true);
    }

    /**
     * Stops every coordinator in one level. Level unload and shutdown pass {@code false} so the
     * persisted descriptors survive; an explicit stop passes {@code true}.
     */
    public static int stopAll(ServerLevel level, boolean persistRemoval) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.remove(level);
        if (active == null) {
            return 0;
        }
        for (ActiveSegmentedEffect activeEffect : active.values()) {
            sendStop(level, activeEffect);
            EffectAudioService.stop(level, activeEffect.descriptor.instanceId());
            if (persistRemoval) {
                EffectPersistenceService.recordStop(level, activeEffect.descriptor.instanceId());
            }
        }
        return active.size();
    }

    /** Advances coordinator lifetimes and audience membership in one level. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.get(level);
        if (active == null || active.isEmpty()) {
            ACTIVE_COORDINATORS.remove(level);
            return;
        }

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
            ACTIVE_COORDINATORS.remove(level);
        }
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.get(level);
        return active == null ? 0 : active.size();
    }

    /** Number of reserved segment cells in one level. */
    public static int activeSegmentCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.get(level);
        return active == null ? 0 : activeSegmentCount(active);
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
            UUID victim = lowestPriority(active, newcomerId);
            if (victim == null) {
                LOGGER.warn(
                    "Admitted segmented effect over the soft segment budget of {}: {} slot(s) over",
                    budget(),
                    remaining
                );
                return;
            }
            ActiveSegmentedEffect victimEffect = active.get(victim);
            int released = victimEffect == null ? 0 : victimEffect.spec.segmentCount();
            stop(level, new SegmentedVisualEffectHandle(victim));
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

    private static UUID lowestPriority(Map<UUID, ActiveSegmentedEffect> active, UUID excludedId) {
        UUID victim = null;
        int lowest = Integer.MAX_VALUE;
        for (Map.Entry<UUID, ActiveSegmentedEffect> entry : active.entrySet()) {
            if (entry.getKey().equals(excludedId)) {
                continue;
            }
            int weight = entry.getValue().spec.priority().weight();
            if (weight < lowest) {
                lowest = weight;
                victim = entry.getKey();
            }
        }
        return victim;
    }

    /**
     * Builds the segmented part of a joining player's resume snapshot. Returned payloads mark the player as tracking;
     * coordinators outside the player's audience are untracked so the next tick does not resend duplicates.
     */
    public static List<StartSegmentedVisualEffectPayload> resumePayloads(ServerLevel level, ServerPlayer player) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.get(level);
        if (active == null || active.isEmpty()) {
            return List.of();
        }

        long serverGameTime = level.getGameTime();
        List<StartSegmentedVisualEffectPayload> payloads = new ArrayList<>();
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        for (ActiveSegmentedEffect activeEffect : active.values()) {
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
        EffectAudioService.start(level, plan, spec.audience());
    }

    private static void collectAudience(
        ServerLevel level,
        ActiveSegmentedEffect activeEffect,
        Map<UUID, ServerPlayer> audience
    ) {
        if (!(activeEffect.spec.audience() instanceof EffectAudience.Nearby nearby)) {
            for (ServerPlayer player : level.players()) {
                audience.put(player.getUUID(), player);
            }
            return;
        }

        EffectPoint position = activeEffect.transform.position();
        double heightDeviation = activeEffect.heights.maxAbsDeviation(position.y());
        double radius = nearby.radius() + activeEffect.spec.layout().halfDiagonal() + heightDeviation;
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (position.distanceToSqr(player.getX(), player.getY(), player.getZ()) <= radiusSqr) {
                audience.put(player.getUUID(), player);
            }
        }
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
            SegmentHeightProfile stored = readHeights(restoredDescriptor.state());
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

    private static void saveHeights(CompoundTag state, SegmentHeightProfile heights) {
        if (heights.isEmpty()) {
            state.remove("column_heights");
            return;
        }
        double[] values = heights.values();
        long[] bits = new long[values.length];
        for (int index = 0; index < values.length; index++) {
            bits[index] = Double.doubleToRawLongBits(values[index]);
        }
        state.putLongArray("column_heights", bits);
    }

    private static SegmentHeightProfile readHeights(CompoundTag state) {
        int columns = state.getInt("columns");
        int rows = state.getInt("rows");
        if (columns < 1 || rows < 1 || !state.contains("column_heights", Tag.TAG_LONG_ARRAY)) {
            return SegmentHeightProfile.NONE;
        }
        long[] bits = state.getLongArray("column_heights");
        if (bits.length != columns * rows) {
            return SegmentHeightProfile.NONE;
        }
        double[] values = new double[bits.length];
        for (int index = 0; index < bits.length; index++) {
            values[index] = Double.longBitsToDouble(bits[index]);
        }
        return SegmentHeightProfile.of(columns, rows, values);
    }

    private static ActiveSegmentedEffect find(ServerLevel level, SegmentedVisualEffectHandle handle) {
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.get(level);
        return active == null ? null : active.get(handle.coordinatorId());
    }

    private static Map<UUID, ActiveSegmentedEffect> activeSegments(ServerLevel level) {
        return ACTIVE_COORDINATORS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
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
            this.heights = heights;
            this.descriptor = descriptor;
        }
    }
}
