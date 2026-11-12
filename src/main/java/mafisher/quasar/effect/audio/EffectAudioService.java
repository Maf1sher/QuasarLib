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
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.LevelStateSlot;
import mafisher.quasar.effect.network.EffectAudioPlanPayload;
import mafisher.quasar.effect.network.EffectAudioTriggerPayload;
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
    /**
     * Soft per-level cap. At capacity the lowest-priority plan is evicted; an incoming plan below
     * every active priority is dropped instead of displacing a more important one.
     */
    public static final int MAX_ACTIVE_PLANS_PER_LEVEL = 128;
    /** Runtime trigger events accepted per plan per server tick. */
    public static final int MAX_TRIGGER_EVENTS_PER_TICK_PER_PLAN = 16;

    private static final int RESUME_GRACE_TICKS = EffectAudioPlan.GRACE_TICKS;
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final LevelStateSlot<AudioState> STATE = LevelStateSlot.create(AudioState::new);

    private EffectAudioService() {
    }

    /** Starts or replaces one effect's audio plan with default priority. */
    public static void start(ServerLevel level, EffectAudioPlan plan, EffectAudience audience) {
        start(level, plan, audience, EffectPriority.NORMAL);
    }

    /** Starts or replaces one effect's audio plan and sends it to the current audience. */
    public static void start(
        ServerLevel level,
        EffectAudioPlan plan,
        EffectAudience audience,
        EffectPriority priority
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(priority, "priority");
        if (level.isClientSide()) {
            throw new IllegalStateException("Effect audio is server-authoritative");
        }
        if (!plan.dimensionId().equals(level.dimension().location())) {
            throw new IllegalArgumentException("Effect audio plan dimension does not match its level");
        }

        AudioState state = STATE.get(level);
        Map<UUID, ActiveAudio> plans = state.plans;
        ActiveAudio replaced = plans.remove(plan.effectId());
        if (replaced != null) {
            replaced.stop(level);
        }
        if (plan.cueCount() == 0 && plan.triggerCount() == 0) {
            if (plans.isEmpty()) {
                STATE.remove(level);
            }
            return;
        }
        while (plans.size() >= MAX_ACTIVE_PLANS_PER_LEVEL) {
            UUID victim = EffectBudgetService.lowestPriority(
                plans, active -> active.priority.weight(), null
            );
            ActiveAudio evicted = victim == null ? null : plans.get(victim);
            if (evicted == null) {
                break;
            }
            if (priority.weight() < evicted.priority.weight()) {
                EffectBudgetService.recordAudioDrop(level);
                LOGGER.warn(
                    "Dropping effect audio plan {} in {}: its priority {} is below every active plan",
                    plan.effectId(),
                    level.dimension().location(),
                    priority
                );
                return;
            }
            plans.remove(victim);
            EffectBudgetService.recordAudioEviction(level);
            LOGGER.warn(
                "Evicting effect audio plan {} in {}: active plan cap reached and priority {} is lowest",
                victim,
                level.dimension().location(),
                evicted.priority
            );
            evicted.stop(level);
        }

        ActiveAudio active = new ActiveAudio(plan, audience, priority);
        plans.put(plan.effectId(), active);
        active.syncAudience(level);
    }

    /** Stops one effect's plan and notifies its current recipients. Unknown IDs are no-ops. */
    public static void stop(ServerLevel level, UUID effectId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(effectId, "effectId");
        AudioState state = STATE.peek(level);
        if (state == null) {
            return;
        }
        ActiveAudio active = state.plans.remove(effectId);
        if (active != null) {
            active.stop(level);
        }
        if (state.plans.isEmpty()) {
            STATE.remove(level);
        }
    }

    /** Fires one named trigger at its declared position. */
    public static boolean trigger(ServerLevel level, UUID effectId, String triggerId) {
        return trigger(level, effectId, triggerId, null);
    }

    /**
     * Fires one named trigger, optionally overriding its position with a runtime event position.
     *
     * @return {@code true} when the plan and trigger exist and the per-tick trigger budget
     *     accepted the event; {@code false} when the plan is gone, the trigger is unknown, or the
     *     budget is exhausted
     */
    public static boolean trigger(ServerLevel level, UUID effectId, String triggerId, Vec3 position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(triggerId, "triggerId");
        if (level.isClientSide()) {
            throw new IllegalStateException("Effect audio is server-authoritative");
        }
        if (position != null
            && (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))) {
            throw new IllegalArgumentException("Effect audio trigger position must be finite");
        }

        AudioState state = STATE.peek(level);
        ActiveAudio active = state == null ? null : state.plans.get(effectId);
        if (active == null) {
            return false;
        }
        EffectAudioTrigger trigger = active.plan.triggerOrNull(triggerId);
        if (trigger == null || active.triggersThisTick >= MAX_TRIGGER_EVENTS_PER_TICK_PER_PLAN) {
            return false;
        }
        active.triggersThisTick++;

        Vec3 resolved = position == null ? trigger.position() : position;
        EffectAudioTriggerPayload payload = new EffectAudioTriggerPayload(
            effectId,
            level.dimension().location(),
            triggerId,
            resolved,
            level.getGameTime()
        );
        if (level.getServer() != null) {
            for (UUID playerId : active.recipients) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && player.level() == level) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
        return true;
    }

    /** Stops every plan in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        AudioState state = STATE.peek(level);
        if (state == null) {
            return 0;
        }
        STATE.remove(level);
        int count = state.plans.size();
        for (ActiveAudio active : state.plans.values()) {
            active.stop(level);
        }
        state.plans.clear();
        return count;
    }

    /** Drops runtime state without sending packets; used on level unload and server shutdown. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        STATE.remove(level);
    }

    /** Expires finished plans and reconciles audience membership. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        AudioState state = STATE.peek(level);
        if (state == null || state.plans.isEmpty()) {
            if (state != null) {
                STATE.remove(level);
            }
            return;
        }
        Map<UUID, ActiveAudio> plans = state.plans;

        long gameTime = level.getGameTime();
        Iterator<Map.Entry<UUID, ActiveAudio>> iterator = plans.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveAudio active = iterator.next().getValue();
            if (ageTicks(level, active.plan) >= active.plan.durationTicks()) {
                active.stop(level);
                iterator.remove();
                continue;
            }
            active.triggersThisTick = 0;
            active.syncAudience(level);
        }
        if (plans.isEmpty()) {
            STATE.remove(level);
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
        AudioState state = STATE.peek(level);
        if (state == null || state.plans.isEmpty()) {
            return List.of();
        }

        List<EffectAudioPlan> result = new ArrayList<>();
        long gameTime = level.getGameTime();
        for (ActiveAudio active : state.plans.values()) {
            int planAge = EffectTimeline.ageTicks(gameTime, active.plan.startGameTime());
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
        AudioState state = STATE.peek(level);
        return state == null ? 0 : state.plans.size();
    }

    static EffectAudioPlan planOrNull(ServerLevel level, UUID effectId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(effectId, "effectId");
        AudioState state = STATE.peek(level);
        ActiveAudio active = state == null ? null : state.plans.get(effectId);
        return active == null ? null : active.plan;
    }

    private static int ageTicks(ServerLevel level, EffectAudioPlan plan) {
        return EffectTimeline.ageTicks(level.getGameTime(), plan.startGameTime());
    }

    private static final class AudioState {
        private final Map<UUID, ActiveAudio> plans = new LinkedHashMap<>();
    }

    private static final class ActiveAudio {
        private final EffectAudioPlan plan;
        private final EffectAudience audience;
        private final EffectPriority priority;
        private final Set<UUID> recipients = new LinkedHashSet<>();
        private int triggersThisTick;

        private ActiveAudio(EffectAudioPlan plan, EffectAudience audience, EffectPriority priority) {
            this.plan = plan;
            this.audience = audience;
            this.priority = priority;
        }

        private boolean audienceContains(ServerLevel level, ServerPlayer player) {
            return this.audience.includes(level, this.plan.center(), 0.0, player);
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
            active.audience.collect(level, active.plan.center(), 0.0, result);
            return result;
        }
    }
}
