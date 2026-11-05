package mafisher.quasar.effect;

import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-thread service for starting, tracking, and cancelling transient client-rendered effects. */
public final class VisualEffectService {
    private static final int MAX_ACTIVE_EFFECTS_PER_LEVEL = 128;
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
            spec.scale(),
            spec.durationTicks(),
            level.getGameTime(),
            spec.seed()
        );
        ActiveEffect active = new ActiveEffect(payload, spec.audience());
        Map<UUID, ActiveEffect> effects = activeEffects(level);
        effects.put(instanceId, active);
        for (ServerPlayer player : level.players()) {
            if (active.includes(player)) {
                sendStart(player, active);
            }
        }
        while (effects.size() > MAX_ACTIVE_EFFECTS_PER_LEVEL) {
            UUID oldest = effects.keySet().iterator().next();
            stop(level, new VisualEffectHandle(oldest));
        }
        return new VisualEffectHandle(instanceId);
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
        Set<UUID> onlinePlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            onlinePlayers.add(player.getUUID());
        }

        Iterator<Map.Entry<UUID, ActiveEffect>> iterator = effects.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveEffect active = iterator.next().getValue();
            if (gameTime - active.payload().startGameTime() >= active.payload().durationTicks()) {
                sendStop(level, active);
                iterator.remove();
                continue;
            }

            active.trackingPlayers().removeIf(playerId -> !onlinePlayers.contains(playerId));
            for (ServerPlayer player : level.players()) {
                boolean inRange = active.includes(player);
                boolean wasTracking = active.trackingPlayers().contains(player.getUUID());
                if (inRange && !wasTracking) {
                    sendStart(player, active);
                } else if (!inRange && wasTracking) {
                    PacketDistributor.sendToPlayer(player, stopPayload(level, active));
                    active.trackingPlayers().remove(player.getUUID());
                }
            }
        }
    }

    private static Map<UUID, ActiveEffect> activeEffects(ServerLevel level) {
        return ACTIVE_EFFECTS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    }

    private static void sendStart(ServerPlayer player, ActiveEffect active) {
        PacketDistributor.sendToPlayer(player, active.payload());
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

        private ActiveEffect(StartVisualEffectPayload payload, EffectAudience audience) {
            this.payload = payload;
            this.audience = audience;
        }

        private StartVisualEffectPayload payload() {
            return this.payload;
        }

        private boolean includes(ServerPlayer player) {
            if (this.audience instanceof EffectAudience.Nearby nearby) {
                return player.distanceToSqr(this.payload.x(), this.payload.y(), this.payload.z())
                    <= nearby.radius() * nearby.radius();
            }
            return true;
        }

        private Set<UUID> trackingPlayers() {
            return this.trackingPlayers;
        }
    }
}
