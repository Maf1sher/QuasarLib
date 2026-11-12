package mafisher.quasar.effect.runtime;

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
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.EffectStartStatus;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.effect.VisualEffectHandle;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.effect.VisualEffectStartResult;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.api.scale.SegmentLink;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Per-level replication state for standalone visuals. */
@ApiStatus.Internal
public final class VisualEffectRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private static final int GRID_MIN_PLAYERS = 8;
    private static final long GRID_MIN_QUERIES = 256L;

    private final ServerLevel level;
    private final EffectStore store;
    private final EffectPacketSink packets;
    private final EffectBudgetSource budgets;
    private final TransformUpdates transforms;
    private final EffectAudioRuntime audio;
    private final Map<UUID, ActiveEffect> effects = new LinkedHashMap<>();

    public VisualEffectRuntime(
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

    public VisualEffectStartResult start(
        VisualEffectSpec spec,
        SegmentLink segmentLink,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(options, "options");
        return startInternal(
            spec, segmentLink, options, UUID.randomUUID(), this.level.getGameTime()
        );
    }

    public VisualEffectStartResult startWithId(
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        EffectPersistenceOptions options,
        SegmentLink segmentLink
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(options, "options");
        return startInternal(spec, segmentLink, options, instanceId, startGameTime);
    }

    private VisualEffectStartResult startInternal(
        VisualEffectSpec spec,
        SegmentLink segmentLink,
        EffectPersistenceOptions options,
        UUID instanceId,
        long startGameTime
    ) {
        EffectDescriptor descriptor = segmentLink == null ? EffectDescriptor.builder(
                instanceId, EffectKind.VISUAL, spec.effectId(), this.level.dimension().location()
            )
            .placement(spec.transform(), spec.scale())
            .timing(spec.seed(), startGameTime, spec.durationTicks())
            .audience(spec.audience())
            .capabilities(options.capabilities())
            .persistence(options.persistence())
            .ownerSessionId(options.ownerSessionId())
            .state(descriptorState(options, spec))
            .build() : null;
        return activate(spec, instanceId, startGameTime, segmentLink, descriptor, true);
    }

    /**
     * Adds one visual, enforces the soft budget, announces it to its audience, and records or
     * starts its side effects. Start and restore differ only in descriptor ownership and whether
     * a new persistence record is written.
     */
    private VisualEffectStartResult activate(
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
            this.level.dimension().location(),
            position.x,
            position.y,
            position.z,
            transform.yaw(),
            transform.pitch(),
            transform.roll(),
            spec.scale(),
            spec.durationTicks(),
            startGameTime,
            this.level.getGameTime(),
            spec.seed(),
            segmentLink
        );
        ActiveEffect active = new ActiveEffect(payload, spec.audience(), transform, descriptor,
            spec.priority());
        this.effects.put(instanceId, active);
        if (!enforceBudget(this.effects, instanceId)) {
            EffectStartStatus status = recordStart
                ? EffectStartStatus.CULLED_ON_START
                : EffectStartStatus.RESTORED_INACTIVE;
            return new VisualEffectStartResult(new VisualEffectHandle(instanceId), status);
        }
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        collectAudience(active, null, audience);
        for (ServerPlayer player : audience.values()) {
            sendStart(player, active);
        }
        if (recordStart && descriptor != null) {
            this.store.recordStart(descriptor);
        }
        if (segmentLink == null) {
            startAudio(instanceId, spec, startGameTime);
        }
        EffectStartStatus status = recordStart
            ? EffectStartStatus.ACTIVE
            : EffectStartStatus.RESTORED_ACTIVE;
        return new VisualEffectStartResult(new VisualEffectHandle(instanceId), status);
    }

    /**
     * Enforces the soft per-level budget by culling the lowest-priority effect, oldest first on
     * ties. The newcomer participates, so a background effect started while the level is full is
     * culled before it is announced instead of throwing.
     */
    private boolean enforceBudget(Map<UUID, ActiveEffect> activeEffects, UUID newcomerId) {
        int budget = this.budgets.standaloneVisuals();
        while (activeEffects.size() > budget) {
            UUID victim = EffectBudgetService.lowestPriority(
                activeEffects, effect -> effect.priority().weight(), null
            );
            if (victim == null) {
                return activeEffects.containsKey(newcomerId);
            }
            EffectBudgetService.recordCull(this.level);
            LOGGER.info(
                "Culled visual effect {} in {} because the soft budget of {} is full",
                victim,
                this.level.dimension().location(),
                budget
            );
            if (victim.equals(newcomerId)) {
                activeEffects.remove(victim);
                return false;
            }
            stop(new VisualEffectHandle(victim));
        }
        return activeEffects.containsKey(newcomerId);
    }

    public boolean isActive(VisualEffectHandle handle) {
        return findActive(handle) != null;
    }

    public void adoptOwnedBy(UUID ownerSessionId, EffectScope scope) {
        Objects.requireNonNull(ownerSessionId, "ownerSessionId");
        Objects.requireNonNull(scope, "scope");
        for (ActiveEffect active : this.effects.values()) {
            if (active.descriptor() != null && ownerSessionId.equals(active.descriptor().ownerSessionId())) {
                UUID instanceId = active.payload().instanceId();
                scope.adoptVisual(instanceId, new VisualEffectHandle(instanceId));
            }
        }
    }

    /**
     * Restores one standalone visual with its original identity, start time, and transform. The
     * handle may be inactive when the soft budget culled it; query {@code isActive}.
     */
    public VisualEffectStartResult restore(
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        EffectDescriptor descriptor
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(instanceId, "instanceId");
        return activate(spec, instanceId, startGameTime, null, descriptor, false);
    }

    /** Restores one legacy coordinator-linked visual without recording a standalone descriptor. */
    public VisualEffectStartResult restoreSegment(
        VisualEffectSpec spec,
        UUID instanceId,
        long startGameTime,
        SegmentLink link
    ) {
        Objects.requireNonNull(link, "link");
        return activate(spec, instanceId, startGameTime, link, null, false);
    }

    /**
     * Updates the world transform of an active visual. The change is applied immediately and
     * delivered to trackers in the next per-tick transform batch.
     */
    public void update(VisualEffectHandle handle, EffectTransform transform) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        ActiveEffect active = findActive(handle);
        if (active == null) {
            return;
        }

        active.setTransform(transform);
        if (active.descriptor() != null) {
            active.setDescriptor(active.descriptor().withTransform(transform));
            this.store.recordUpdate(active.descriptor());
        }
        this.transforms.queueVisual(handle.instanceId(), transform, active.trackingPlayers());
    }

    /** Player IDs currently tracking one effect; empty for unknown, finished, or untracked handles. */
    public List<UUID> trackingPlayers(VisualEffectHandle handle) {
        List<UUID> players = new ArrayList<>();
        collectTrackingPlayers(handle, players);
        return List.copyOf(players);
    }

    /** Adds player IDs tracking one effect to {@code target}; no-op for unknown or finished handles. */
    public void collectTrackingPlayers(VisualEffectHandle handle, Collection<UUID> target) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(target, "target");
        ActiveEffect active = findActive(handle);
        if (active != null) {
            target.addAll(active.trackingPlayers());
        }
    }

    /** Sends one payload to every listed player that is still present in this level. */
    public void sendToTrackingPlayers(Collection<UUID> playerIds, CustomPacketPayload payload) {
        TrackedPlayers.send(this.level, this.packets, playerIds, payload);
    }

    public void stop(VisualEffectHandle handle) {
        stop(handle, EffectRemovalPolicy.DELETE);
    }

    /** Stops an instance; {@link EffectRemovalPolicy#KEEP_FOR_RESTORE} keeps its descriptor. */
    public void stop(VisualEffectHandle handle, EffectRemovalPolicy policy) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(policy, "policy");
        ActiveEffect active = this.effects.remove(handle.instanceId());
        if (active != null) {
            sendStop(active);
            if (policy.deletesDescriptor()) {
                this.store.recordStop(handle.instanceId());
            }
        }
        this.audio.stop(handle.instanceId());
    }

    /** Keeps active visual state in sync with players who enter or leave its audience. */
    public void tick() {
        if (this.effects.isEmpty()) {
            return;
        }

        long gameTime = this.level.getGameTime();
        List<ServerPlayer> players = this.level.players();
        long directQueries = (long) players.size() * this.effects.size();
        SpatialGrid<ServerPlayer> index = players.size() > GRID_MIN_PLAYERS
            && this.effects.size() > 1
            && directQueries > GRID_MIN_QUERIES
            ? buildPlayerIndex(players)
            : null;
        Map<UUID, ServerPlayer> nearby = new LinkedHashMap<>();

        Iterator<Map.Entry<UUID, ActiveEffect>> iterator = this.effects.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveEffect active = iterator.next().getValue();
            if (EffectTimeline.isFinished(
                gameTime, active.payload().startGameTime(), active.payload().durationTicks()
            )) {
                sendStop(active);
                iterator.remove();
                this.store.recordStop(active.payload().instanceId());
                this.audio.stop(active.payload().instanceId());
                continue;
            }

            nearby.clear();
            collectAudience(active, index, nearby);
            Iterator<UUID> trackers = active.trackingPlayers().iterator();
            while (trackers.hasNext()) {
                UUID playerId = trackers.next();
                if (!nearby.containsKey(playerId)) {
                    ServerPlayer player = this.level.getServer() == null
                        ? null
                        : this.level.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null && player.level() == this.level) {
                        this.packets.sendToPlayer(player, stopPayload(active));
                    }
                    trackers.remove();
                }
            }
            for (ServerPlayer player : nearby.values()) {
                if (!active.trackingPlayers().contains(player.getUUID())) {
                    sendStart(player, active);
                }
            }
        }
    }

    /** Number of active visual effects in one level. */
    public int activeCount() {
        return this.effects.size();
    }

    /**
     * Builds and applies a resume snapshot for one joining player. Returned effects are marked as
     * tracked and effects outside the player's audience are untracked, so the next tick does not
     * resend duplicate starts. Callers are responsible for sending the returned payloads.
     */
    public List<StartVisualEffectPayload> resumePayloads(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (this.effects.isEmpty()) {
            return List.of();
        }

        long serverGameTime = this.level.getGameTime();
        List<StartVisualEffectPayload> payloads = new ArrayList<>();
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        for (ActiveEffect active : this.effects.values()) {
            audience.clear();
            collectAudience(active, null, audience);
            if (audience.containsKey(player.getUUID())) {
                payloads.add(active.currentPayload(serverGameTime));
                active.trackingPlayers().add(player.getUUID());
            } else {
                active.trackingPlayers().remove(player.getUUID());
            }
        }
        return List.copyOf(payloads);
    }

    /** Drops transient visual state without deleting persisted descriptors. */
    public void clear() {
        this.effects.clear();
    }

    private static CompoundTag descriptorState(EffectPersistenceOptions options, VisualEffectSpec spec) {
        CompoundTag state = options.state().copy();
        EffectAudioNbt.saveInto(state, spec.audio());
        state.putString("priority", spec.priority().name());
        return state;
    }

    private void startAudio(UUID instanceId, VisualEffectSpec spec, long startGameTime) {
        if (spec.audio().isEmpty()) {
            return;
        }
        EffectAudioPlan plan = EffectAudioPlan.from(
            instanceId,
            this.level.dimension().location(),
            spec.position(),
            startGameTime,
            spec.durationTicks(),
            spec.audio()
        );
        this.audio.start(plan, spec.audience(), spec.priority());
    }

    private static SpatialGrid<ServerPlayer> buildPlayerIndex(List<ServerPlayer> players) {
        SpatialGrid<ServerPlayer> index = new SpatialGrid<>(ServerPlayer::position);
        for (ServerPlayer player : players) {
            index.add(player);
        }
        return index;
    }

    private void collectAudience(
        ActiveEffect active,
        SpatialGrid<ServerPlayer> index,
        Map<UUID, ServerPlayer> audience
    ) {
        EffectPoint position = active.transform().position();
        Vec3 center = new Vec3(position.x(), position.y(), position.z());
        if (active.audience() instanceof EffectAudience.Nearby nearby && index != null) {
            index.forEachNear(center, nearby.radius(), player -> {
                if (active.audience().includes(this.level, center, 0.0, player)) {
                    audience.put(player.getUUID(), player);
                }
            });
            return;
        }
        active.audience().collect(this.level, center, 0.0, audience);
    }

    private ActiveEffect findActive(VisualEffectHandle handle) {
        Objects.requireNonNull(handle, "handle");
        ActiveEffect active = this.effects.get(handle.instanceId());
        if (active == null || EffectTimeline.isFinished(
            this.level.getGameTime(), active.payload().startGameTime(), active.payload().durationTicks()
        )) {
            return null;
        }
        return active;
    }

    private void sendStart(ServerPlayer player, ActiveEffect active) {
        this.packets.sendToPlayer(player, active.currentPayload(this.level.getGameTime()));
        active.trackingPlayers().add(player.getUUID());
    }

    private void sendStop(ActiveEffect active) {
        TrackedPlayers.send(this.level, this.packets, active.trackingPlayers(), stopPayload(active));
        active.trackingPlayers().clear();
    }

    private StopVisualEffectPayload stopPayload(ActiveEffect active) {
        return new StopVisualEffectPayload(active.payload().instanceId(), this.level.dimension().location());
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
