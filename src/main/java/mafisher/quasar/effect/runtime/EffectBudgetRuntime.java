package mafisher.quasar.effect.runtime;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Per-level soft capacity counters and the level-wide entity spawn budget. The runtime owns this
 * state so no service keeps hidden level-keyed fields.
 */
@ApiStatus.Internal
public final class EffectBudgetRuntime {
    private final ServerLevel level;
    private final EffectBudgetSource budgets;

    private long culledEffects;
    private long deferredStarts;
    private long droppedEntitySpawns;
    private long audioEvictions;
    private long audioDrops;
    private long spawnTick = Long.MIN_VALUE;
    private int spawnsThisTick;

    public EffectBudgetRuntime(ServerLevel level, EffectBudgetSource budgets) {
        this.level = Objects.requireNonNull(level, "level");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
    }

    public void recordCull() {
        this.culledEffects++;
    }

    public void recordDeferral() {
        this.deferredStarts++;
    }

    public void recordDroppedEntitySpawn() {
        this.droppedEntitySpawns++;
    }

    public void recordAudioEviction() {
        this.audioEvictions++;
    }

    public void recordAudioDrop() {
        this.audioDrops++;
    }

    public boolean entitySpawnBudgetAvailable() {
        return this.spawnTick != this.level.getGameTime()
            || this.spawnsThisTick < this.budgets.entitySpawnsPerLevelTick();
    }

    /**
     * Reserves one level-wide entity spawn slot for the current game tick.
     *
     * @return {@code false} when the level tick budget is exhausted
     */
    public boolean tryAcquireEntitySpawn() {
        long gameTime = this.level.getGameTime();
        if (this.spawnTick != gameTime) {
            this.spawnTick = gameTime;
            this.spawnsThisTick = 0;
        }
        if (this.spawnsThisTick >= this.budgets.entitySpawnsPerLevelTick()) {
            this.droppedEntitySpawns++;
            return false;
        }
        this.spawnsThisTick++;
        return true;
    }

    /** Immutable snapshot of the cumulative degradation counters. */
    public record BudgetCounters(
        long culledEffects,
        long deferredStarts,
        long droppedEntitySpawns,
        long audioEvictions,
        long audioDrops
    ) {
    }

    public BudgetCounters counters() {
        return new BudgetCounters(
            this.culledEffects,
            this.deferredStarts,
            this.droppedEntitySpawns,
            this.audioEvictions,
            this.audioDrops
        );
    }

    /** Drops cumulative counters and the current tick's spawn reservation. */
    public void clear() {
        this.culledEffects = 0L;
        this.deferredStarts = 0L;
        this.droppedEntitySpawns = 0L;
        this.audioEvictions = 0L;
        this.audioDrops = 0L;
        this.spawnTick = Long.MIN_VALUE;
        this.spawnsThisTick = 0;
    }
}
