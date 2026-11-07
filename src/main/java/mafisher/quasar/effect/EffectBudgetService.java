package mafisher.quasar.effect;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import mafisher.quasar.effect.cinematic.CinematicEffectService;
import mafisher.quasar.effect.gameplay.GameplayEffectService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-level soft capacity accounting. Every service reports culls, deferrals, and dropped
 * spawns here so operators can inspect degradation from one place.
 */
public final class EffectBudgetService {
    private static final Map<ServerLevel, Counters> COUNTERS = new WeakHashMap<>();

    private EffectBudgetService() {
    }

    /** Current capacity state and cumulative degradation counters for one level. */
    public static EffectBudgetReport report(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Counters counters = counters(level);
        return new EffectBudgetReport(
            VisualEffectService.activeCount(level),
            EffectBudgets.standaloneVisuals(),
            SegmentedVisualEffectService.activeSegmentCount(level),
            EffectBudgets.segments(),
            GameplayEffectService.activeCount(level),
            EffectBudgets.gameplaySessions(),
            GameplayEffectService.pendingCount(level),
            EffectBudgets.gameplayPending(),
            CinematicEffectService.activeCount(level),
            EffectBudgets.cinematicSessions(),
            counters.culledEffects,
            counters.deferredStarts,
            counters.droppedEntitySpawns
        );
    }

    /** Records that one active effect was culled because its level was over budget. */
    public static void recordCull(ServerLevel level) {
        counters(level).culledEffects++;
    }

    /** Records that a gameplay start was queued until capacity becomes available. */
    public static void recordDeferral(ServerLevel level) {
        counters(level).deferredStarts++;
    }

    /** Records that one entity spawn was dropped by a soft spawn budget. */
    public static void recordDroppedEntitySpawn(ServerLevel level) {
        counters(level).droppedEntitySpawns++;
    }

    /** Whether one more entity spawn fits in the level's current tick without consuming a slot. */
    public static boolean entitySpawnBudgetAvailable(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Counters counters = counters(level);
        return counters.spawnTick != level.getGameTime()
            || counters.spawnsThisTick < EffectBudgets.entitySpawnsPerLevelTick();
    }

    /**
     * Reserves one level-wide entity spawn slot for the current game tick.
     *
     * @return {@code false} when the level tick budget is exhausted; the caller must drop the
     *     spawn without throwing
     */
    public static boolean tryAcquireEntitySpawn(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Counters counters = counters(level);
        long gameTime = level.getGameTime();
        if (counters.spawnTick != gameTime) {
            counters.spawnTick = gameTime;
            counters.spawnsThisTick = 0;
        }
        if (counters.spawnsThisTick >= EffectBudgets.entitySpawnsPerLevelTick()) {
            counters.droppedEntitySpawns++;
            return false;
        }
        counters.spawnsThisTick++;
        return true;
    }

    /** Drops transient counters for a level; active services are unaffected. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        COUNTERS.remove(level);
    }

    private static Counters counters(ServerLevel level) {
        return COUNTERS.computeIfAbsent(level, ignored -> new Counters());
    }

    private static final class Counters {
        private long culledEffects;
        private long deferredStarts;
        private long droppedEntitySpawns;
        private long spawnTick = Long.MIN_VALUE;
        private int spawnsThisTick;
    }
}
