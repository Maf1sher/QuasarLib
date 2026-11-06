package mafisher.quasar.effect;

import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-thread service for starting, moving, tracking, and cancelling transient client-rendered effects. */
public final class VisualEffectService {
    /** Hard safety cap per level. The oldest effect is stopped and evicted when it is exceeded. */
    public static final int MAX_ACTIVE_EFFECTS_PER_LEVEL = 128;
    private static final int GRID_MIN_PLAYERS = 8;
    private static final long GRID_MIN_QUERIES = 256L;
    private static final Map<ServerLevel, Map<UUID, ActiveEffect>> ACTIVE_EFFECTS = new WeakHashMap<>();

    private VisualEffectService() {
    }

    public static VisualEffectHandle start(ServerLevel level, VisualEffectSpec spec) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        UUID instanceId = UUID.randomUUID();
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
            level.getGameTime(),
            spec.seed()
        );
        ActiveEffect active = new ActiveEffect(payload, spec.audience(), spec.transform());
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
        return new VisualEffectHandle(instanceId);
    }

    /** Updates the world transform of an active visual and sends it to its current observers. */
    public static void update(ServerLevel level, VisualEffectHandle handle, EffectTransform transform) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(transform, "transform");
        Map<UUID, ActiveEffect> effects = ACTIVE_EFFECTS.get(level);
        ActiveEffect active = effects == null ? null : effects.get(handle.instanceId());
        if (active == null || EffectTimeline.isFinished(
            level.getGameTime(), active.payload().startGameTime(), active.payload().durationTicks()
        )) {
            return;
        }

        active.setTransform(transform);
        UpdateVisualEffectPayload payload = UpdateVisualEffectPayload.of(
            active.payload().instanceId(), level.dimension().location(), transform, level.getGameTime()
        );
        for (UUID playerId : new ArrayList<>(active.trackingPlayers())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && player.level() == level) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void stop(ServerLevel level, VisualEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Map<UUID, ActiveEffect> effects = ACTIVE_EFFECTS.get(level);
        if (effects == null) {
            return;
        }
        ActiveEffect active = effects.remove(handle.instanceId());
        if (active != null) {
            sendStop(level, active);
        }
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

    private static void sendStart(ServerPlayer player, ActiveEffect active) {
        PacketDistributor.sendToPlayer(player, active.currentPayload());
        active.trackingPlayers().add(player.getUUID());
    }

    private static void sendStop(ServerLevel level, ActiveEffect active) {
        StopVisualEffectPayload payload = stopPayload(level, active);
        for (UUID playerId : new ArrayList<>(active.trackingPlayers())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && player.level() == level) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
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

        private ActiveEffect(StartVisualEffectPayload payload, EffectAudience audience, EffectTransform transform) {
            this.payload = payload;
            this.audience = audience;
            this.transform = transform;
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
                this.payload.seed()
            );
        }
    }
}
