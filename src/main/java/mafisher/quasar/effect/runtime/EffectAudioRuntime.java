package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioTrigger;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.network.EffectAudioPlanPayload;
import mafisher.quasar.effect.network.EffectAudioTriggerPayload;
import mafisher.quasar.effect.network.StopEffectAudioPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level audio plan registry. Plans are transient runtime state; the owning effect persists its
 * {@link mafisher.quasar.api.audio.EffectAudioSpec} and rebuilds the plan on restore.
 */
@ApiStatus.Internal
public final class EffectAudioRuntime {
    /** Soft per-level cap. At capacity the lowest-priority plan is evicted. */
    public static final int MAX_ACTIVE_PLANS_PER_LEVEL = 128;
    /** Runtime trigger events accepted per plan per server tick. */
    public static final int MAX_TRIGGER_EVENTS_PER_TICK_PER_PLAN = 16;

    private static final int RESUME_GRACE_TICKS = EffectAudioPlan.GRACE_TICKS;
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectAudioRuntime.class);

    private final ServerLevel level;
    private final EffectPacketSink packets;
    private final Map<UUID, ActiveAudio> plans = new LinkedHashMap<>();

    public EffectAudioRuntime(ServerLevel level, EffectPacketSink packets) {
        this.level = Objects.requireNonNull(level, "level");
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    /** Starts or replaces one effect's audio plan with default priority. */
    public void start(EffectAudioPlan plan, EffectAudience audience) {
        start(plan, audience, EffectPriority.NORMAL);
    }

    /** Starts or replaces one effect's audio plan and sends it to the current audience. */
    public void start(EffectAudioPlan plan, EffectAudience audience, EffectPriority priority) {
        start(plan, audience, priority, 0.0);
    }

    /** Starts a plan with extra audience padding for effects whose visuals extend past their center. */
    void start(EffectAudioPlan plan, EffectAudience audience, EffectPriority priority, double audiencePadding) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(priority, "priority");
        if (!Double.isFinite(audiencePadding) || audiencePadding < 0.0) {
            throw new IllegalArgumentException("Audio audience padding must be finite and non-negative");
        }
        if (this.level.isClientSide()) {
            throw new IllegalStateException("Effect audio is server-authoritative");
        }
        if (!plan.dimensionId().equals(this.level.dimension().location())) {
            throw new IllegalArgumentException("Effect audio plan dimension does not match its level");
        }

        ActiveAudio replaced = this.plans.remove(plan.effectId());
        if (replaced != null) {
            replaced.stop(this.level, this.packets);
        }
        if (plan.cueCount() == 0 && plan.triggerCount() == 0) {
            return;
        }
        while (this.plans.size() >= MAX_ACTIVE_PLANS_PER_LEVEL) {
            UUID victim = EffectBudgetService.lowestPriority(
                this.plans, active -> active.priority.weight(), null
            );
            ActiveAudio evicted = victim == null ? null : this.plans.get(victim);
            if (evicted == null) {
                break;
            }
            if (priority.weight() < evicted.priority.weight()) {
                EffectBudgetService.recordAudioDrop(this.level);
                LOGGER.warn(
                    "Dropping effect audio plan {} in {}: its priority {} is below every active plan",
                    plan.effectId(),
                    this.level.dimension().location(),
                    priority
                );
                return;
            }
            this.plans.remove(victim);
            EffectBudgetService.recordAudioEviction(this.level);
            LOGGER.warn(
                "Evicting effect audio plan {} in {}: active plan cap reached and priority {} is lowest",
                victim,
                this.level.dimension().location(),
                evicted.priority
            );
            evicted.stop(this.level, this.packets);
        }

        ActiveAudio active = new ActiveAudio(plan, audience, priority, audiencePadding);
        this.plans.put(plan.effectId(), active);
        active.syncAudience(this.level, this.packets);
    }

    /** Stops one effect's plan and notifies its current recipients. Unknown IDs are no-ops. */
    public void stop(UUID effectId) {
        Objects.requireNonNull(effectId, "effectId");
        ActiveAudio active = this.plans.remove(effectId);
        if (active != null) {
            active.stop(this.level, this.packets);
        }
    }

    /** Fires one named trigger at its declared position. */
    public boolean trigger(UUID effectId, String triggerId) {
        return trigger(effectId, triggerId, null);
    }

    /**
     * Fires one named trigger, optionally overriding its position with a runtime event position.
     *
     * @return {@code true} when the plan and trigger exist and the per-tick trigger budget
     *     accepted the event; {@code false} when the plan is gone, the trigger is unknown, or the
     *     budget is exhausted
     */
    public boolean trigger(UUID effectId, String triggerId, Vec3 position) {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(triggerId, "triggerId");
        if (this.level.isClientSide()) {
            throw new IllegalStateException("Effect audio is server-authoritative");
        }
        if (position != null
            && (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))) {
            throw new IllegalArgumentException("Effect audio trigger position must be finite");
        }

        ActiveAudio active = this.plans.get(effectId);
        if (active == null) {
            return false;
        }
        EffectAudioTrigger trigger = active.plan.triggerOrNull(triggerId);
        if (trigger == null
            || active.triggersThisTick >= MAX_TRIGGER_EVENTS_PER_TICK_PER_PLAN) {
            return false;
        }
        active.triggersThisTick++;

        Vec3 resolved = position == null ? trigger.position() : position;
        EffectAudioTriggerPayload payload = new EffectAudioTriggerPayload(
            effectId,
            this.level.dimension().location(),
            triggerId,
            resolved,
            this.level.getGameTime()
        );
        TrackedPlayers.send(this.level, this.packets, active.recipients, payload);
        return true;
    }

    /** Stops every plan in this level and returns how many were active. */
    public int stopAll() {
        int count = this.plans.size();
        for (ActiveAudio active : this.plans.values()) {
            active.stop(this.level, this.packets);
        }
        this.plans.clear();
        return count;
    }

    /** Drops runtime state without sending packets; used on level unload and server shutdown. */
    public void clear() {
        this.plans.clear();
    }

    /** Expires finished plans and reconciles audience membership. */
    public void tick() {
        if (this.plans.isEmpty()) {
            return;
        }

        long gameTime = this.level.getGameTime();
        Iterator<Map.Entry<UUID, ActiveAudio>> iterator = this.plans.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveAudio active = iterator.next().getValue();
            if (EffectTimeline.ageTicks(gameTime, active.plan.startGameTime())
                >= active.plan.durationTicks()) {
                active.stop(this.level, this.packets);
                iterator.remove();
                continue;
            }
            active.triggersThisTick = 0;
            active.syncAudience(this.level, this.packets);
        }
    }

    /**
     * Plans that a joining player should schedule now, and reconciles the player's recipient state.
     * Returned plans mark the player as a recipient so the next tick does not send duplicates;
     * plans outside the player's audience are untracked. Past-only plans are omitted.
     */
    public List<EffectAudioPlan> resumePlans(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (this.plans.isEmpty()) {
            return List.of();
        }

        List<EffectAudioPlan> result = new ArrayList<>();
        long gameTime = this.level.getGameTime();
        for (ActiveAudio active : this.plans.values()) {
            int planAge = EffectTimeline.ageTicks(gameTime, active.plan.startGameTime());
            boolean playable = active.plan.dimensionId().equals(this.level.dimension().location())
                && planAge < active.plan.durationTicks()
                && active.plan.hasPlayableCues(planAge, RESUME_GRACE_TICKS)
                && active.audienceContains(this.level, player);
            if (!playable) {
                active.recipients.remove(player.getUUID());
                continue;
            }
            result.add(active.plan);
            active.recipients.add(player.getUUID());
        }
        return List.copyOf(result);
    }

    public int activeCount() {
        return this.plans.size();
    }

    public EffectAudioPlan planOrNull(UUID effectId) {
        Objects.requireNonNull(effectId, "effectId");
        ActiveAudio active = this.plans.get(effectId);
        return active == null ? null : active.plan;
    }

    private static final class ActiveAudio {
        private final EffectAudioPlan plan;
        private final EffectAudience audience;
        private final EffectPriority priority;
        private final double audiencePadding;
        private final Set<UUID> recipients = new LinkedHashSet<>();
        private int triggersThisTick;

        private ActiveAudio(EffectAudioPlan plan, EffectAudience audience, EffectPriority priority,
            double audiencePadding) {
            this.plan = plan;
            this.audience = audience;
            this.priority = priority;
            this.audiencePadding = audiencePadding;
        }

        private boolean audienceContains(ServerLevel level, ServerPlayer player) {
            return this.audience.includes(level, this.plan.center(), this.audiencePadding, player);
        }

        private void syncAudience(ServerLevel level, EffectPacketSink packets) {
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
                    packets.sendToPlayer(player, stopPayload());
                }
                iterator.remove();
            }
            for (ServerPlayer player : current.values()) {
                if (this.recipients.add(player.getUUID())) {
                    packets.sendToPlayer(player,
                        new EffectAudioPlanPayload(this.plan, level.getGameTime()));
                }
            }
        }

        private void stop(ServerLevel level, EffectPacketSink packets) {
            TrackedPlayers.send(level, packets, this.recipients, stopPayload());
            this.recipients.clear();
        }

        private StopEffectAudioPayload stopPayload() {
            return new StopEffectAudioPayload(this.plan.effectId(), this.plan.dimensionId());
        }

        private static Map<UUID, ServerPlayer> collectAudience(ServerLevel level, ActiveAudio active) {
            Map<UUID, ServerPlayer> result = new LinkedHashMap<>();
            active.audience.collect(level, active.plan.center(), active.audiencePadding, result);
            return result;
        }
    }
}
