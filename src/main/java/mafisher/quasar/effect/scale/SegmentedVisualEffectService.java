package mafisher.quasar.effect.scale;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectHandle;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.network.UpdateSegmentedVisualEffectPayload;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread coordinator for segmented, giant-scale world effects. Each segment is a normal
 * {@link VisualEffectSpec} started through {@link VisualEffectService}, so existing caps, audience policy, and
 * transient lifetime rules still apply. A coordinator reserves capacity up front instead of evicting unrelated effects.
 */
public final class SegmentedVisualEffectService {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, Map<UUID, ActiveSegmentedEffect>> ACTIVE_COORDINATORS = new WeakHashMap<>();

    private SegmentedVisualEffectService() {
    }

    /**
     * Starts a segmented effect and returns its root handle. Throws when the level does not have enough visual-effect
     * capacity; no segment is started and no unrelated effect is evicted in that case.
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
        SegmentedVisualEffectHandle handle = startInternal(
            level, spec, UUID.randomUUID(), level.getGameTime(), options, null
        );
        if (handle == null) {
            throw new IllegalStateException("Not enough visual effect capacity for segmented effect");
        }
        return handle;
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
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        if (index < 0) {
            throw new IllegalArgumentException("Segment index cannot be negative");
        }
        return UUID.nameUUIDFromBytes(
            ("quasar:segment:" + coordinatorId + ":" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
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
        List<UUID> ids = segmentIds == null
            ? restoreSegmentIds(coordinatorId, spec.segmentCount())
            : segmentIds;
        return startInternal(level, spec, coordinatorId, startGameTime, options, ids, descriptor);
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

        int active = VisualEffectService.activeCount(level);
        if (active + spec.segmentCount() > VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL) {
            LOGGER.warn(
                "Rejected segmented effect {} in {}: requires {} segment slots but only {} of {} remain",
                spec.effectId(),
                level.dimension().location(),
                spec.segmentCount(),
                Math.max(0, VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL - active),
                VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL
            );
            return null;
        }

        SegmentGridLayout layout = spec.layout();
        EffectScope scope = null;
        List<UUID> segments = new ArrayList<>(spec.segmentCount());
        int index = 0;
        try {
            scope = new EffectScope(level, coordinatorId);
            for (int row = 0; row < spec.rows(); row++) {
                for (int column = 0; column < spec.columns(); column++) {
                    VisualEffectSpec segment = spec.segmentSpec(column, row);
                    SegmentLink link = new SegmentLink(coordinatorId, index, layout);
                    UUID segmentId = restoredSegmentIds == null
                        ? segmentId(coordinatorId, index)
                        : restoredSegmentIds.get(index);
                    if (restoredSegmentIds == null) {
                        VisualEffectHandle handle = VisualEffectService.startWithId(
                            level, segment, segmentId, startGameTime, EffectPersistenceOptions.cosmetic(), link
                        );
                        scope.adoptVisual(segmentId, handle);
                    } else {
                        VisualEffectHandle handle = VisualEffectService.restoreSegment(
                            level, segment, segmentId, startGameTime, link
                        );
                        if (handle == null) {
                            throw new IllegalStateException("Could not restore segment " + index);
                        }
                        scope.adoptVisual(segmentId, handle);
                    }
                    segments.add(segmentId);
                    index++;
                }
            }
        } catch (RuntimeException | Error exception) {
            if (scope != null) {
                scope.close();
            }
            throw exception;
        }

        CompoundTag state = options.state();
        state.putInt("columns", spec.columns());
        state.putInt("rows", spec.rows());
        state.putDouble("spacing", spec.spacing());
        state.putFloat("segment_scale", spec.segmentScale());
        ListTag ids = new ListTag();
        for (UUID segmentId : segments) {
            ids.add(StringTag.valueOf(segmentId.toString()));
        }
        state.put("segment_ids", ids);
        EffectDescriptor descriptor = restoredDescriptor == null ? new EffectDescriptor(
            coordinatorId,
            EffectKind.SEGMENTED,
            spec.effectId(),
            level.dimension().location(),
            new EffectTransform(
                new EffectPoint(spec.center().x, spec.center().y, spec.center().z),
                spec.yaw(),
                spec.pitch(),
                spec.roll()
            ),
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
            scope,
            List.copyOf(segments),
            startGameTime,
            descriptor
        );
        activeSegments(level).put(coordinatorId, activeEffect);
        EffectPersistenceService.recordStart(level, descriptor);
        return new SegmentedVisualEffectHandle(coordinatorId);
    }

    /**
     * Moves the root transform, stages every segment for late joiners, and sends one aggregated update per tracking
     * player instead of one packet per segment. Unknown handles are no-ops.
     */
    public static void update(ServerLevel level, SegmentedVisualEffectHandle handle, EffectTransform transform) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        ActiveSegmentedEffect activeEffect = find(level, handle);
        if (activeEffect == null) {
            return;
        }

        SegmentedVisualEffectSpec spec = activeEffect.spec;
        SegmentGridLayout layout = spec.layout();
        Set<UUID> recipients = new LinkedHashSet<>();
        int index = 0;
        for (int row = 0; row < spec.rows(); row++) {
            for (int column = 0; column < spec.columns(); column++) {
                VisualEffectHandle segment = new VisualEffectHandle(activeEffect.segmentIds.get(index++));
                VisualEffectService.stageTransform(level, segment, layout.transform(transform, column, row));
                VisualEffectService.collectTrackingPlayers(level, segment, recipients);
            }
        }
        if (activeEffect.descriptor != null) {
            activeEffect.descriptor = activeEffect.descriptor.withTransform(transform);
            EffectPersistenceService.recordUpdate(level, activeEffect.descriptor);
        }
        VisualEffectService.sendToTrackingPlayers(
            level,
            recipients,
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
            removed.close(true);
            EffectPersistenceService.recordStop(level, handle.coordinatorId());
        }
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
            activeEffect.close(persistRemoval);
            if (persistRemoval) {
                EffectPersistenceService.recordStop(level, activeEffect.descriptor.instanceId());
            }
        }
        return active.size();
    }

    /** Advances coordinator lifetimes in one level. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.get(level);
        if (active == null || active.isEmpty()) {
            ACTIVE_COORDINATORS.remove(level);
            return;
        }

        long gameTime = level.getGameTime();
        Iterator<Map.Entry<UUID, ActiveSegmentedEffect>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveSegmentedEffect activeEffect = iterator.next().getValue();
            if (gameTime - activeEffect.startGameTime >= activeEffect.spec.durationTicks()) {
                activeEffect.close(true);
                iterator.remove();
                EffectPersistenceService.recordStop(level, activeEffect.descriptor.instanceId());
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

    private static ActiveSegmentedEffect find(ServerLevel level, SegmentedVisualEffectHandle handle) {
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.get(level);
        return active == null ? null : active.get(handle.coordinatorId());
    }

    private static Map<UUID, ActiveSegmentedEffect> activeSegments(ServerLevel level) {
        return ACTIVE_COORDINATORS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    }

    private static final class ActiveSegmentedEffect {
        private final SegmentedVisualEffectSpec spec;
        private final EffectScope scope;
        private final List<UUID> segmentIds;
        private final long startGameTime;
        private EffectDescriptor descriptor;

        private ActiveSegmentedEffect(
            SegmentedVisualEffectSpec spec,
            EffectScope scope,
            List<UUID> segmentIds,
            long startGameTime,
            EffectDescriptor descriptor
        ) {
            this.spec = spec;
            this.scope = scope;
            this.segmentIds = segmentIds;
            this.startGameTime = startGameTime;
            this.descriptor = descriptor;
        }

        private void close(boolean persistRemoval) {
            this.scope.close(persistRemoval);
        }
    }
}
