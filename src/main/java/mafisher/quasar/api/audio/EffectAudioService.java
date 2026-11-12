package mafisher.quasar.api.audio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.effect.runtime.EffectAudioRuntime;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-thread facade for client-scheduled effect audio. State belongs to the per-level
 * {@link LevelEffectRuntime}; plans are runtime state and the owning effect persists its
 * {@link EffectAudioSpec} and rebuilds the plan on restore.
 */
public final class EffectAudioService {
    /**
     * Soft per-level cap. At capacity the lowest-priority plan is evicted; an incoming plan below
     * every active priority is dropped instead of displacing a more important one.
     */
    public static final int MAX_ACTIVE_PLANS_PER_LEVEL = EffectAudioRuntime.MAX_ACTIVE_PLANS_PER_LEVEL;
    /** Runtime trigger events accepted per plan per server tick. */
    public static final int MAX_TRIGGER_EVENTS_PER_TICK_PER_PLAN =
        EffectAudioRuntime.MAX_TRIGGER_EVENTS_PER_TICK_PER_PLAN;

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
        EffectRuntimeRegistry.get(level).audio().start(plan, audience, priority);
    }

    /** Stops one effect's plan and notifies its current recipients. Unknown IDs are no-ops. */
    public static void stop(ServerLevel level, UUID effectId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(effectId, "effectId");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.audio().stop(effectId);
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
    public static boolean trigger(
        ServerLevel level,
        UUID effectId,
        String triggerId,
        Vec3 position
    ) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime != null && runtime.audio().trigger(effectId, triggerId, position);
    }

    /** Stops every plan in one level and returns how many were active. */
    public static int stopAll(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.audio().stopAll();
    }

    /** Drops runtime state without sending packets; used on level unload and server shutdown. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.audio().clear();
        }
    }

    /** Expires finished plans and reconciles audience membership. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.audio().tick();
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
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? List.of() : runtime.audio().resumePlans(player);
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.audio().activeCount();
    }

    public static EffectAudioPlan planOrNull(ServerLevel level, UUID effectId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(effectId, "effectId");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? null : runtime.audio().planOrNull(effectId);
    }
}
