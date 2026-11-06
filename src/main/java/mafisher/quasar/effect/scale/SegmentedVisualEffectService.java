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
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectHandle;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.network.UpdateSegmentedVisualEffectPayload;
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
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        if (level.isClientSide()) {
            throw new IllegalStateException("Segmented visual effects are server-authoritative");
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
            throw new IllegalStateException("Not enough visual effect capacity for segmented effect");
        }

        UUID coordinatorId = UUID.randomUUID();
        SegmentGridLayout layout = spec.layout();
        EffectScope scope = null;
        List<UUID> segments = new ArrayList<>(spec.segmentCount());
        int index = 0;
        try {
            scope = new EffectScope(level, coordinatorId);
            for (int row = 0; row < spec.rows(); row++) {
                for (int column = 0; column < spec.columns(); column++) {
                    VisualEffectSpec segment = spec.segmentSpec(column, row);
                    segments.add(scope.playVisual(segment, new SegmentLink(coordinatorId, index++, layout)));
                }
            }
        } catch (RuntimeException | Error exception) {
            if (scope != null) {
                scope.close();
            }
            throw exception;
        }

        ActiveSegmentedEffect activeEffect = new ActiveSegmentedEffect(
            spec,
            scope,
            List.copyOf(segments),
            level.getGameTime()
        );
        activeSegments(level).put(coordinatorId, activeEffect);
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
            removed.close();
        }
        if (active.isEmpty()) {
            ACTIVE_COORDINATORS.remove(level);
        }
    }

    /** Stops every coordinator in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSegmentedEffect> active = ACTIVE_COORDINATORS.remove(level);
        if (active == null) {
            return 0;
        }
        for (ActiveSegmentedEffect activeEffect : active.values()) {
            activeEffect.close();
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
                activeEffect.close();
                iterator.remove();
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

        private ActiveSegmentedEffect(
            SegmentedVisualEffectSpec spec,
            EffectScope scope,
            List<UUID> segmentIds,
            long startGameTime
        ) {
            this.spec = spec;
            this.scope = scope;
            this.segmentIds = segmentIds;
            this.startGameTime = startGameTime;
        }

        private void close() {
            this.scope.close();
        }
    }
}
