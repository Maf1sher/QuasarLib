package mafisher.quasar.effect;

import mafisher.quasar.effect.audio.EffectAudioPlan;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import mafisher.quasar.effect.scale.SegmentLink;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-thread service for starting, moving, tracking, and cancelling transient client-rendered effects. */
public final class VisualEffectService {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    /**
     * Default soft budget per level for visuals tracked by this service. Segmented coordinators use their own
     * dedicated budget and are not stored here.
     */
    public static final int MAX_ACTIVE_EFFECTS_PER_LEVEL = 128;
    private static final int GRID_MIN_PLAYERS = 8;
    private static final long GRID_MIN_QUERIES = 256L;
    private static final LevelStateSlot<VisualState> STATE = LevelStateSlot.create(VisualState::new);
    private static Integer budgetOverride;

    private VisualEffectService() {
    }

    public static VisualEffectHandle start(ServerLevel level, VisualEffectSpec spec) {
        return start(level, spec, null, EffectPersistenceOptions.DEFAULT);
    }

    /**
     * Starts a visual effect with a legacy coordinator link. New segmented effects use
     * {@code SegmentedVisualEffectService}, which sends the whole grid in one aggregated lifecycle.
     */
    public static VisualEffectHandle start(ServerLevel level, VisualEffectSpec spec, SegmentLink segmentLink) {
        return start(level, spec, segmentLink, EffectPersistenceOptions.DEFAULT);
    }

    public static VisualEffectHandle start(
        ServerLevel level,
        VisualEffectSpec spec,
        EffectPersistenceOptions options
    ) {
        return start(level, spec, null, options);
    }

    public static VisualEffectHandle start(
        ServerLevel level,
        VisualEffectSpec spec,
        SegmentLink segmentLink,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(options, "options");
        return startInternal(level, spec, segmentLink, options, UUID.randomUUID(), level.getGameTime());
    }

    /**
     * Starts a visual effect with a caller-supplied identity and absolute start time. Used by
     * cinematic timelines and persistence restore, which must preserve ages across restarts.
     */
    public static VisualEffectHandle startWithId(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        EffectPersistenceOptions options
    ) {
        return startWithId(level, spec, instanceId, startGameTime, options, null);
    }

    public static VisualEffectHandle startWithId(
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
        return startInternal(level, spec, segmentLink, options, instanceId, startGameTime);
    }

    private static VisualEffectHandle startInternal(
        ServerLevel level,
        VisualEffectSpec spec,
        SegmentLink segmentLink,
        EffectPersistenceOptions options,
        UUID instanceId,
        long startGameTime
    ) {
        EffectDescriptor descriptor = segmentLink == null ? new EffectDescriptor(
            instanceId,
            EffectKind.VISUAL,
            spec.effectId(),
            level.dimension().location(),
            spec.transform(),
            spec.scale(),
            spec.seed(),
            startGameTime,
            spec.durationTicks(),
            spec.audience(),
            options.capabilities(),
            options.persistence(),
            options.ownerSessionId(),
            descriptorState(options, spec)
        ) : null;
        return activate(level, spec, instanceId, startGameTime, segmentLink, descriptor, true);
    }

    /**
     * Adds one visual to the level, enforces the soft budget, announces it to its audience, and
     * records or starts its side effects. Start and restore differ only in descriptor ownership and
     * whether a new persistence record is written.
     */
    private static VisualEffectHandle activate(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        SegmentLink segmentLink,
        EffectDescriptor descriptor,
        boolean recordStart
    ) {
        EffectTransform transform = spec.transform();
        Vec3 position = spec.position();
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            spec.effectId(),
            instanceId,
            level.dimension().location(),
            position.x,
            position.y,
            position.z,
            transform.yaw(),
            transform.pitch(),
            transform.roll(),
            spec.scale(),
            spec.durationTicks(),
            startGameTime,
            level.getGameTime(),
            spec.seed(),
            segmentLink
        );
        ActiveEffect active = new ActiveEffect(payload, spec.audience(), transform, descriptor,
            spec.priority());
        Map<UUID, ActiveEffect> effects = activeEffects(level);
        effects.put(instanceId, active);
        if (!enforceBudget(level, effects, instanceId)) {
            if (effects.isEmpty()) {
                STATE.remove(level);
            }
            return new VisualEffectHandle(instanceId);
        }
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        collectAudience(level, active, null, audience);
        for (ServerPlayer player : audience.values()) {
            sendStart(level, player, active);
        }
        if (recordStart && descriptor != null) {
            EffectPersistenceService.recordStart(level, descriptor);
        }
        if (segmentLink == null) {
            startAudio(level, instanceId, spec, startGameTime);
        }
        return new VisualEffectHandle(instanceId);
    }

    /**
     * Enforces the soft per-level budget by culling the lowest-priority effect, oldest first on ties.
     * The newcomer participates, so a background effect started while the level is full is culled
     * before it is announced instead of throwing.
     */
    private static boolean enforceBudget(ServerLevel level, Map<UUID, ActiveEffect> effects, UUID newcomerId) {
        int budget = budget();
        while (effects.size() > budget) {
            UUID victim = EffectBudgetService.lowestPriority(
                effects, effect -> effect.priority().weight(), null
            );
            if (victim == null) {
                return effects.containsKey(newcomerId);
            }
            EffectBudgetService.recordCull(level);
            LOGGER.info(
                "Culled visual effect {} in {} because the soft budget of {} is full",
                victim,
                level.dimension().location(),
                budget
            );
            if (victim.equals(newcomerId)) {
                effects.remove(victim);
                return false;
            }
            stop(level, new VisualEffectHandle(victim));
        }
        return effects.containsKey(newcomerId);
    }

    /** Whether one visual is currently active; culled or finished handles return {@code false}. */
    public static boolean isActive(ServerLevel level, VisualEffectHandle handle) {
        return findActive(level, handle) != null;
    }

    /**
     * Adopts active visuals whose descriptor names {@code ownerSessionId}. Used when a deferred
     * restored gameplay session becomes active after its child visuals were restored.
     */
    public static void adoptOwnedBy(ServerLevel level, UUID ownerSessionId, EffectScope scope) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(ownerSessionId, "ownerSessionId");
        Objects.requireNonNull(scope, "scope");
        VisualState state = STATE.peek(level);
        if (state == null) {
            return;
        }
        for (ActiveEffect active : state.effects.values()) {
            if (active.descriptor() != null && ownerSessionId.equals(active.descriptor().ownerSessionId())) {
                UUID instanceId = active.payload().instanceId();
                scope.adoptVisual(instanceId, new VisualEffectHandle(instanceId));
            }
        }
    }

    static void overrideBudgetForTesting(int budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("Visual budget must be positive");
        }
        budgetOverride = budget;
    }

    static void resetBudgetForTesting() {
        budgetOverride = null;
    }

    private static int budget() {
        return budgetOverride == null ? EffectBudgets.standaloneVisuals() : budgetOverride;
    }

    /**
     * Restores one standalone visual with its original identity, start time, and transform. The
     * handle may be inactive when the soft budget culled it; query {@link #isActive}.
     */
    public static VisualEffectHandle restore(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        EffectDescriptor descriptor
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(instanceId, "instanceId");
        return activate(level, spec, instanceId, startGameTime, null, descriptor, false);
    }

    /** Restores one legacy coordinator-linked visual without recording a standalone descriptor. */
    public static VisualEffectHandle restoreSegment(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        SegmentLink link
    ) {
        return restore(level, spec, instanceId, startGameTime, null, link);
    }

    private static VisualEffectHandle restore(
        ServerLevel level,
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        EffectDescriptor descriptor,
        SegmentLink link
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(instanceId, "instanceId");
        return activate(level, spec, instanceId, startGameTime, link, descriptor, false);
    }

    /** Updates the world transform of an active visual and sends it to its current observers. */
    public static void update(ServerLevel level, VisualEffectHandle handle, EffectTransform transform) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        ActiveEffect active = findActive(level, handle);
        if (active == null) {
            return;
        }

        active.setTransform(transform);
        if (active.descriptor() != null) {
            active.setDescriptor(active.descriptor().withTransform(transform));
            EffectPersistenceService.recordUpdate(level, active.descriptor());
        }
        UpdateVisualEffectPayload payload = UpdateVisualEffectPayload.of(
            active.payload().instanceId(), level.dimension().location(), transform, level.getGameTime()
        );
        sendToTrackingPlayers(level, active.trackingPlayers(), payload);
    }

    /**
     * Records a transform without sending packets. Segmented coordinators stage every segment so late joiners receive
     * the current placement, then send one aggregated update themselves.
     */
    public static void stageTransform(ServerLevel level, VisualEffectHandle handle, EffectTransform transform) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        ActiveEffect active = findActive(level, handle);
        if (active != null) {
            active.setTransform(transform);
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
        ActiveEffect active = findActive(level, handle);
        if (active != null) {
            target.addAll(active.trackingPlayers());
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
        if (playerIds.isEmpty() || level.getServer() == null) {
            return;
        }
        for (UUID playerId : playerIds) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && player.level() == level) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void stop(ServerLevel level, VisualEffectHandle handle) {
        stop(level, handle, true);
    }

    /** Stops an instance; {@code persistRemoval=false} keeps its descriptor for a later restore. */
    public static void stop(ServerLevel level, VisualEffectHandle handle, boolean persistRemoval) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        VisualState state = STATE.peek(level);
        if (state == null) {
            EffectAudioService.stop(level, handle.instanceId());
            return;
        }
        ActiveEffect active = state.effects.remove(handle.instanceId());
        if (active != null) {
            sendStop(level, active);
            if (persistRemoval) {
                EffectPersistenceService.recordStop(level, handle.instanceId());
            }
        }
        EffectAudioService.stop(level, handle.instanceId());
        if (state.effects.isEmpty()) {
            STATE.remove(level);
        }
    }

    /** Keeps active visual state in sync with players who enter or leave its audience. */
    public static void tick(ServerLevel level) {
        VisualState state = STATE.peek(level);
        if (state == null) {
            return;
        }
        Map<UUID, ActiveEffect> effects = state.effects;
        if (effects.isEmpty()) {
            removeEmptyState(level, state);
            return;
        }

        long gameTime = level.getGameTime();
        List<ServerPlayer> players = level.players();
        long directQueries = (long)players.size() * effects.size();
        SpatialGrid<ServerPlayer> index = players.size() > GRID_MIN_PLAYERS
            && effects.size() > 1
            && directQueries > GRID_MIN_QUERIES
            ? buildPlayerIndex(players)
            : null;
        Map<UUID, ServerPlayer> nearby = new LinkedHashMap<>();

        Iterator<Map.Entry<UUID, ActiveEffect>> iterator = effects.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveEffect active = iterator.next().getValue();
            if (EffectTimeline.isFinished(
                gameTime, active.payload().startGameTime(), active.payload().durationTicks()
            )) {
                sendStop(level, active);
                iterator.remove();
                EffectPersistenceService.recordStop(level, active.payload().instanceId());
                EffectAudioService.stop(level, active.payload().instanceId());
                continue;
            }

            nearby.clear();
            collectAudience(level, active, index, nearby);
            Iterator<UUID> trackers = active.trackingPlayers().iterator();
            while (trackers.hasNext()) {
                UUID playerId = trackers.next();
                if (!nearby.containsKey(playerId)) {
                    ServerPlayer player = level.getServer() == null
                        ? null
                        : level.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null && player.level() == level) {
                        PacketDistributor.sendToPlayer(player, stopPayload(level, active));
                    }
                    trackers.remove();
                }
            }
            for (ServerPlayer player : nearby.values()) {
                if (!active.trackingPlayers().contains(player.getUUID())) {
                    sendStart(level, player, active);
                }
            }
        }
        removeEmptyState(level, state);
    }

    /** Number of active visual effects in one level. */
    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        VisualState state = STATE.peek(level);
        return state == null ? 0 : state.effects.size();
    }

    /**
     * Builds and applies a resume snapshot for one joining player. Returned effects are marked as
     * tracked and effects outside the player's audience are untracked, so the next tick does not
     * resend duplicate starts. Callers are responsible for sending the returned payloads.
     */
    public static List<StartVisualEffectPayload> resumePayloads(ServerLevel level, ServerPlayer player) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        VisualState state = STATE.peek(level);
        if (state == null || state.effects.isEmpty()) {
            return List.of();
        }

        long serverGameTime = level.getGameTime();
        List<StartVisualEffectPayload> payloads = new ArrayList<>();
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        for (ActiveEffect active : state.effects.values()) {
            audience.clear();
            collectAudience(level, active, null, audience);
            if (audience.containsKey(player.getUUID())) {
                payloads.add(active.currentPayload(serverGameTime));
                active.trackingPlayers().add(player.getUUID());
            } else {
                active.trackingPlayers().remove(player.getUUID());
            }
        }
        return List.copyOf(payloads);
    }

    /** Drops transient runtime state for a level without deleting persisted descriptors. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        STATE.remove(level);
        EffectAudioService.clear(level);
        EffectBudgetService.clear(level);
    }

    private static CompoundTag descriptorState(EffectPersistenceOptions options, VisualEffectSpec spec) {
        CompoundTag state = options.state().copy();
        EffectAudioNbt.saveInto(state, spec.audio());
        state.putString("priority", spec.priority().name());
        return state;
    }

    private static void startAudio(ServerLevel level, UUID instanceId, VisualEffectSpec spec, long startGameTime) {
        if (spec.audio().isEmpty()) {
            return;
        }
        EffectAudioPlan plan = EffectAudioPlan.from(
            instanceId,
            level.dimension().location(),
            spec.position(),
            startGameTime,
            spec.durationTicks(),
            spec.audio()
        );
        EffectAudioService.start(level, plan, spec.audience());
    }

    private static SpatialGrid<ServerPlayer> buildPlayerIndex(List<ServerPlayer> players) {
        SpatialGrid<ServerPlayer> index = new SpatialGrid<>(ServerPlayer::position);
        for (ServerPlayer player : players) {
            index.add(player);
        }
        return index;
    }

    private static void collectAudience(
        ServerLevel level,
        ActiveEffect active,
        SpatialGrid<ServerPlayer> index,
        Map<UUID, ServerPlayer> audience
    ) {
        EffectPoint position = active.transform().position();
        Vec3 center = new Vec3(position.x(), position.y(), position.z());
        if (active.audience() instanceof EffectAudience.Nearby nearby && index != null) {
            index.forEachNear(center, nearby.radius(), player -> {
                if (active.audience().includes(level, center, 0.0, player)) {
                    audience.put(player.getUUID(), player);
                }
            });
            return;
        }
        active.audience().collect(level, center, 0.0, audience);
    }

    private static void removeEmptyState(ServerLevel level, VisualState state) {
        if (state.effects.isEmpty()) {
            STATE.remove(level);
        }
    }

    private static Map<UUID, ActiveEffect> activeEffects(ServerLevel level) {
        return STATE.get(level).effects;
    }

    private static ActiveEffect findActive(ServerLevel level, VisualEffectHandle handle) {
        VisualState state = STATE.peek(level);
        ActiveEffect active = state == null ? null : state.effects.get(handle.instanceId());
        if (active == null || EffectTimeline.isFinished(
            level.getGameTime(), active.payload().startGameTime(), active.payload().durationTicks()
        )) {
            return null;
        }
        return active;
    }

    private static void sendStart(ServerLevel level, ServerPlayer player, ActiveEffect active) {
        PacketDistributor.sendToPlayer(player, active.currentPayload(level.getGameTime()));
        active.trackingPlayers().add(player.getUUID());
    }

    private static void sendStop(ServerLevel level, ActiveEffect active) {
        sendToTrackingPlayers(level, active.trackingPlayers(), stopPayload(level, active));
        active.trackingPlayers().clear();
    }

    private static StopVisualEffectPayload stopPayload(ServerLevel level, ActiveEffect active) {
        return new StopVisualEffectPayload(active.payload().instanceId(), level.dimension().location());
    }

    private static final class VisualState {
        private final Map<UUID, ActiveEffect> effects = new LinkedHashMap<>();
    }

    private static final class ActiveEffect {
        private final StartVisualEffectPayload payload;
        private final EffectAudience audience;
        private final Set<UUID> trackingPlayers = new HashSet<>();
        private final EffectPriority priority;
        private EffectTransform transform;
        private EffectDescriptor descriptor;

        private ActiveEffect(
            StartVisualEffectPayload payload,
            EffectAudience audience,
            EffectTransform transform,
            EffectDescriptor descriptor,
            EffectPriority priority
        ) {
            this.payload = payload;
            this.audience = audience;
            this.transform = transform;
            this.descriptor = descriptor;
            this.priority = priority;
        }

        private StartVisualEffectPayload payload() {
            return this.payload;
        }

        private EffectAudience audience() {
            return this.audience;
        }

        private EffectPriority priority() {
            return this.priority;
        }

        private EffectTransform transform() {
            return this.transform;
        }

        private EffectDescriptor descriptor() {
            return this.descriptor;
        }

        private void setDescriptor(EffectDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        private Set<UUID> trackingPlayers() {
            return this.trackingPlayers;
        }

        private void setTransform(EffectTransform transform) {
            this.transform = transform;
        }

        private StartVisualEffectPayload currentPayload(long serverGameTime) {
            EffectPoint position = this.transform.position();
            return new StartVisualEffectPayload(
                this.payload.effectId(),
                this.payload.instanceId(),
                this.payload.dimensionId(),
                position.x(),
                position.y(),
                position.z(),
                this.transform.yaw(),
                this.transform.pitch(),
                this.transform.roll(),
                this.payload.scale(),
                this.payload.durationTicks(),
                this.payload.startGameTime(),
                serverGameTime,
                this.payload.seed(),
                this.payload.segmentLink()
            );
        }
    }
}
