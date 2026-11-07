package mafisher.quasar.effect.audio;

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
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.network.EffectAudioPlanPayload;
import mafisher.quasar.effect.network.StopEffectAudioPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread registry for client-scheduled effect audio. Plans are runtime state: the owning
 * effect persists its {@link EffectAudioSpec} and rebuilds the plan on restore.
 */
public final class EffectAudioService {
    /** Provisional safety cap; exceeding it mutes the oldest plan instead of rejecting the new effect. */
    public static final int MAX_ACTIVE_PLANS_PER_LEVEL = 128;

    private static final int RESUME_GRACE_TICKS = EffectAudioPlan.GRACE_TICKS;
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, Map<UUID, ActiveAudio>> ACTIVE_PLANS = new WeakHashMap<>();

    private EffectAudioService() {
    }

    /** Starts or replaces one effect's audio plan and sends it to the current audience. */
    public static void start(ServerLevel level, EffectAudioPlan plan, EffectAudience audience) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(audience, "audience");
        if (level.isClientSide()) {
            throw new IllegalStateException("Effect audio is server-authoritative");
        }
        if (!plan.dimensionId().equals(level.dimension().location())) {
            throw new IllegalArgumentException("Effect audio plan dimension does not match its level");
        }

        Map<UUID, ActiveAudio> plans = activePlans(level);
        ActiveAudio replaced = plans.remove(plan.effectId());
        if (replaced != null) {
            replaced.stop(level);
        }
        if (plan.cueCount() == 0) {
            if (plans.isEmpty()) {
                ACTIVE_PLANS.remove(level);
            }
            return;
        }
        while (plans.size() >= MAX_ACTIVE_PLANS_PER_LEVEL) {
            UUID oldest = plans.keySet().iterator().next();
            ActiveAudio evicted = plans.remove(oldest);
            if (evicted != null) {
                LOGGER.warn("Evicting effect audio plan {} in {}: active plan cap reached",
                    oldest, level.dimension().location());
                evicted.stop(level);
            }
        }

        ActiveAudio active = new ActiveAudio(plan, audience);
        plans.put(plan.effectId(), active);
        active.syncAudience(level);
    }

    /** Stops one effect's plan and notifies its current recipients. Unknown IDs are no-ops. */
    public static void stop(ServerLevel level, UUID effectId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(effectId, "effectId");
        Map<UUID, ActiveAudio> plans = ACTIVE_PLANS.get(level);
        if (plans == null) {
            return;
        }
        ActiveAudio active = plans.remove(effectId);
        if (active != null) {
            active.stop(level);
        }
        if (plans.isEmpty()) {
            ACTIVE_PLANS.remove(level);
        }
    }

    /** Stops every plan in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveAudio> plans = ACTIVE_PLANS.remove(level);
        if (plans == null) {
            return 0;
        }
        for (ActiveAudio active : plans.values()) {
            active.stop(level);
        }
        return plans.size();
    }

    /** Drops runtime state without sending packets; used on level unload and server shutdown. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ACTIVE_PLANS.remove(level);
    }

    /** Expires finished plans and reconciles audience membership. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveAudio> plans = ACTIVE_PLANS.get(level);
        if (plans == null || plans.isEmpty()) {
            ACTIVE_PLANS.remove(level);
            return;
        }

        long gameTime = level.getGameTime();
        Iterator<Map.Entry<UUID, ActiveAudio>> iterator = plans.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveAudio active = iterator.next().getValue();
            if (ageTicks(level, active.plan) >= active.plan.durationTicks()) {
                active.stop(level);
                iterator.remove();
                continue;
            }
            active.syncAudience(level);
        }
        if (plans.isEmpty()) {
            ACTIVE_PLANS.remove(level);
        }
    }

    /**
     * Plans that a joining player should schedule now, and reconciles the player's recipient state.
     * Returned plans mark the player as a recipient so the next tick does not send duplicates;
     * plans outside the player's audience are untracked. Past-only plans are omitted.
     */
    public static List<EffectAudioPlan> resumePlans(ServerLevel level, ServerPlayer player) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Map<UUID, ActiveAudio> plans = ACTIVE_PLANS.get(level);
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }

        List<EffectAudioPlan> result = new ArrayList<>();
        long gameTime = level.getGameTime();
        for (ActiveAudio active : plans.values()) {
            int planAge = (int)Math.min(Integer.MAX_VALUE,
                Math.max(0L, gameTime - active.plan.startGameTime()));
            boolean playable = active.plan.dimensionId().equals(level.dimension().location())
                && planAge < active.plan.durationTicks()
                && active.plan.hasPlayableCues(planAge, RESUME_GRACE_TICKS)
                && active.audienceContains(level, player);
            if (!playable) {
                active.recipients.remove(player.getUUID());
                continue;
            }
            result.add(active.plan);
            active.recipients.add(player.getUUID());
        }
        return List.copyOf(result);
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveAudio> plans = ACTIVE_PLANS.get(level);
        return plans == null ? 0 : plans.size();
    }

    static EffectAudioPlan planOrNull(ServerLevel level, UUID effectId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(effectId, "effectId");
        Map<UUID, ActiveAudio> plans = ACTIVE_PLANS.get(level);
        ActiveAudio active = plans == null ? null : plans.get(effectId);
        return active == null ? null : active.plan;
    }

    private static int ageTicks(ServerLevel level, EffectAudioPlan plan) {
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, level.getGameTime() - plan.startGameTime()));
    }

    private static Map<UUID, ActiveAudio> activePlans(ServerLevel level) {
        return ACTIVE_PLANS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    }

    private static final class ActiveAudio {
        private final EffectAudioPlan plan;
        private final EffectAudience audience;
        private final Set<UUID> recipients = new LinkedHashSet<>();

        private ActiveAudio(EffectAudioPlan plan, EffectAudience audience) {
            this.plan = plan;
            this.audience = audience;
        }

        private boolean audienceContains(ServerLevel level, ServerPlayer player) {
            if (!(this.audience instanceof EffectAudience.Nearby nearby)) {
                return player.level() == level;
            }
            if (player.level() != level) {
                return false;
            }
            return this.plan.center().distanceToSqr(player.position()) <= nearby.radius() * nearby.radius();
        }

        private void syncAudience(ServerLevel level) {
            Map<UUID, ServerPlayer> current = collectAudience(level, this);
            Iterator<UUID> iterator = this.recipients.iterator();
            while (iterator.hasNext()) {
                UUID playerId = iterator.next();
                if (current.containsKey(playerId)) {
                    continue;
                }
                ServerPlayer player = level.getServer() == null
                    ? null
                    : level.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && player.level() == level) {
                    PacketDistributor.sendToPlayer(player, stopPayload());
                }
                iterator.remove();
            }
            for (ServerPlayer player : current.values()) {
                if (this.recipients.add(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player,
                        new EffectAudioPlanPayload(this.plan, level.getGameTime()));
                }
            }
        }

        private void stop(ServerLevel level) {
            if (this.recipients.isEmpty() || level.getServer() == null) {
                this.recipients.clear();
                return;
            }
            StopEffectAudioPayload payload = stopPayload();
            for (UUID playerId : this.recipients) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && player.level() == level) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
            this.recipients.clear();
        }

        private StopEffectAudioPayload stopPayload() {
            return new StopEffectAudioPayload(this.plan.effectId(), this.plan.dimensionId());
        }

        private static Map<UUID, ServerPlayer> collectAudience(ServerLevel level, ActiveAudio active) {
            Map<UUID, ServerPlayer> result = new LinkedHashMap<>();
            if (active.audience instanceof EffectAudience.Nearby nearby) {
                Vec3 center = active.plan.center();
                double radiusSqr = nearby.radius() * nearby.radius();
                for (ServerPlayer player : level.players()) {
                    if (center.distanceToSqr(player.position()) <= radiusSqr) {
                        result.put(player.getUUID(), player);
                    }
                }
            } else {
                for (ServerPlayer player : level.players()) {
                    result.put(player.getUUID(), player);
                }
            }
            return result;
        }
    }
}
