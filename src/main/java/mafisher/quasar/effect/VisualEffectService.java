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
import java.util.WeakHashMap;
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

    /** Hard safety cap per level. The oldest effect is stopped and evicted when it is exceeded. */
    public static final int MAX_ACTIVE_EFFECTS_PER_LEVEL = 128;
    private static final int GRID_MIN_PLAYERS = 8;
    private static final long GRID_MIN_QUERIES = 256L;
    private static final Map<ServerLevel, Map<UUID, ActiveEffect>> ACTIVE_EFFECTS = new WeakHashMap<>();

    private VisualEffectService() {
    }

    public static VisualEffectHandle start(ServerLevel level, VisualEffectSpec spec) {
        return start(level, spec, null, EffectPersistenceOptions.DEFAULT);
    }

    /**
     * Starts a visual effect, optionally linked to a segmented coordinator. The link is sent to clients so one
     * aggregated coordinator update can move every segment.
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
        Vec3 position = spec.position();
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            spec.effectId(),
            instanceId,
            level.dimension().location(),
            position.x,
            position.y,
            position.z,
            spec.yaw(),
            spec.pitch(),
            spec.roll(),
            spec.scale(),
            spec.durationTicks(),
            startGameTime,
            spec.seed(),
            segmentLink
        );
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
        ActiveEffect active = new ActiveEffect(payload, spec.audience(), spec.transform(), descriptor);
        Map<UUID, ActiveEffect> effects = activeEffects(level);
        effects.put(instanceId, active);
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        collectAudience(level, active, null, audience);
        for (ServerPlayer player : audience.values()) {
            sendStart(player, active);
        }
        while (effects.size() > MAX_ACTIVE_EFFECTS_PER_LEVEL) {
            UUID oldest = effects.keySet().iterator().next();
            stop(level, new VisualEffectHandle(oldest));
        }
        if (descriptor != null) {
            EffectPersistenceService.recordStart(level, descriptor);
        }
        if (segmentLink == null) {
            startAudio(level, instanceId, spec, startGameTime);
        }
        return new VisualEffectHandle(instanceId);
    }

    /**
     * Restores one standalone visual with its original identity, start time, and transform.
     * Returns {@code null} when the level is at the safety cap.
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
        Map<UUID, ActiveEffect> effects = activeEffects(level);
        if (effects.size() >= MAX_ACTIVE_EFFECTS_PER_LEVEL) {
            LOGGER.warn("Could not restore visual effect {}: active visual cap reached", instanceId);
            return null;
        }
        EffectTransform transform = spec.transform();
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            spec.effectId(),
            instanceId,
            level.dimension().location(),
            transform.position().x(),
            transform.position().y(),
            transform.position().z(),
            transform.yaw(),
            transform.pitch(),
            transform.roll(),
            spec.scale(),
            spec.durationTicks(),
            startGameTime,
            spec.seed(),
            null
        );
        ActiveEffect active = new ActiveEffect(payload, spec.audience(), transform, descriptor);
        effects.put(instanceId, active);
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        collectAudience(level, active, null, audience);
        for (ServerPlayer player : audience.values()) {
            sendStart(player, active);
        }
        startAudio(level, instanceId, spec, startGameTime);
        return new VisualEffectHandle(instanceId);
    }

    /** Restores one coordinator-owned segment without recording it as a standalone descriptor. */
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
        Map<UUID, ActiveEffect> effects = activeEffects(level);
        if (effects.size() >= MAX_ACTIVE_EFFECTS_PER_LEVEL) {
            LOGGER.warn("Could not restore visual effect {}: active visual cap reached", instanceId);
            return null;
        }
        EffectTransform transform = spec.transform();
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            spec.effectId(),
            instanceId,
            level.dimension().location(),
            transform.position().x(),
            transform.position().y(),
            transform.position().z(),
            transform.yaw(),
            transform.pitch(),
            transform.roll(),
            spec.scale(),
            spec.durationTicks(),
            startGameTime,
            spec.seed(),
            link
        );
        ActiveEffect active = new ActiveEffect(payload, spec.audience(), transform, descriptor);
        effects.put(instanceId, active);
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        collectAudience(level, active, null, audience);
        for (ServerPlayer player : audience.values()) {
            sendStart(player, active);
        }
        if (link == null) {
            startAudio(level, instanceId, spec, startGameTime);
        }
        return new VisualEffectHandle(instanceId);
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
        Map<UUID, ActiveEffect> effects = ACTIVE_EFFECTS.get(level);
        if (effects == null) {
            EffectAudioService.stop(level, handle.instanceId());
            return;
        }
        ActiveEffect active = effects.remove(handle.instanceId());
        if (active != null) {
            sendStop(level, active);
            if (persistRemoval) {
                EffectPersistenceService.recordStop(level, handle.instanceId());
            }
        }
        EffectAudioService.stop(level, handle.instanceId());
        if (effects.isEmpty()) {
            ACTIVE_EFFECTS.remove(level);
        }
    }

    /** Keeps active visual state in sync with players who enter or leave its audience. */
    public static void tick(ServerLevel level) {
        Map<UUID, ActiveEffect> effects = activeEffects(level);
        if (effects.isEmpty()) {
            ACTIVE_EFFECTS.remove(level);
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
            if (gameTime - active.payload().startGameTime() >= active.payload().durationTicks()) {
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
                    sendStart(player, active);
                }
            }
        }
    }

    /** Number of active visual effects in one level. */
    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveEffect> effects = ACTIVE_EFFECTS.get(level);
        return effects == null ? 0 : effects.size();
    }

    /**
     * Builds a resume snapshot for one joining player. The client replaces its visual state with
     * the snapshot; normal tick reconciliation still sends a start when needed.
     */
    public static List<StartVisualEffectPayload> resumePayloads(ServerLevel level, ServerPlayer player) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Map<UUID, ActiveEffect> effects = ACTIVE_EFFECTS.get(level);
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }

        List<StartVisualEffectPayload> payloads = new ArrayList<>();
        Map<UUID, ServerPlayer> audience = new LinkedHashMap<>();
        for (ActiveEffect active : effects.values()) {
            audience.clear();
            collectAudience(level, active, null, audience);
            if (audience.containsKey(player.getUUID())) {
                payloads.add(active.currentPayload());
            }
        }
        return List.copyOf(payloads);
    }

    /** Drops transient runtime state for a level without deleting persisted descriptors. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ACTIVE_EFFECTS.remove(level);
        EffectAudioService.clear(level);
    }

    private static CompoundTag descriptorState(EffectPersistenceOptions options, VisualEffectSpec spec) {
        CompoundTag state = options.state().copy();
        EffectAudioNbt.saveInto(state, spec.audio());
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
        if (!(active.audience() instanceof EffectAudience.Nearby nearby)) {
            for (ServerPlayer player : level.players()) {
                audience.put(player.getUUID(), player);
            }
            return;
        }

        EffectPoint position = active.transform().position();
        double radiusSqr = nearby.radius() * nearby.radius();
        if (index != null) {
            index.forEachNear(new Vec3(position.x(), position.y(), position.z()), nearby.radius(),
                player -> audience.put(player.getUUID(), player));
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (position.distanceToSqr(player.getX(), player.getY(), player.getZ()) <= radiusSqr) {
                audience.put(player.getUUID(), player);
            }
        }
    }

    private static Map<UUID, ActiveEffect> activeEffects(ServerLevel level) {
        return ACTIVE_EFFECTS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    }

    private static ActiveEffect findActive(ServerLevel level, VisualEffectHandle handle) {
        Map<UUID, ActiveEffect> effects = ACTIVE_EFFECTS.get(level);
        ActiveEffect active = effects == null ? null : effects.get(handle.instanceId());
        if (active == null || EffectTimeline.isFinished(
            level.getGameTime(), active.payload().startGameTime(), active.payload().durationTicks()
        )) {
            return null;
        }
        return active;
    }

    private static void sendStart(ServerPlayer player, ActiveEffect active) {
        PacketDistributor.sendToPlayer(player, active.currentPayload());
        active.trackingPlayers().add(player.getUUID());
    }

    private static void sendStop(ServerLevel level, ActiveEffect active) {
        sendToTrackingPlayers(level, active.trackingPlayers(), stopPayload(level, active));
        active.trackingPlayers().clear();
    }

    private static StopVisualEffectPayload stopPayload(ServerLevel level, ActiveEffect active) {
        return new StopVisualEffectPayload(active.payload().instanceId(), level.dimension().location());
    }

    private static final class ActiveEffect {
        private final StartVisualEffectPayload payload;
        private final EffectAudience audience;
        private final Set<UUID> trackingPlayers = new HashSet<>();
        private EffectTransform transform;
        private EffectDescriptor descriptor;

        private ActiveEffect(
            StartVisualEffectPayload payload,
            EffectAudience audience,
            EffectTransform transform,
            EffectDescriptor descriptor
        ) {
            this.payload = payload;
            this.audience = audience;
            this.transform = transform;
            this.descriptor = descriptor;
        }

        private StartVisualEffectPayload payload() {
            return this.payload;
        }

        private EffectAudience audience() {
            return this.audience;
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

        private StartVisualEffectPayload currentPayload() {
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
                this.payload.seed(),
                this.payload.segmentLink()
            );
        }
    }
}
