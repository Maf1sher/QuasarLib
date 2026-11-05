package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import mafisher.quasar.effect.EffectHitResult;

/** Mutable per-tick accounting guarded by one immutable budget. */
final class EffectTickBudgetState {
    private final EffectTickBudget budget;
    private int targetsExamined;
    private int damaged;
    private int hitboxCalls;
    private int entitiesSpawned;

    EffectTickBudgetState(EffectTickBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    void reset() {
        this.targetsExamined = 0;
        this.damaged = 0;
        this.hitboxCalls = 0;
        this.entitiesSpawned = 0;
    }

    int remainingTargets() {
        return Math.max(0, this.budget.maxTargetsExamined() - this.targetsExamined);
    }

    boolean canApplyHitbox() {
        return this.remainingTargets() > 0 && this.hitboxCalls < this.budget.maxHitboxCalls();
    }

    void recordHitboxCall() {
        this.hitboxCalls++;
    }

    void recordHitResult(EffectHitResult result) {
        this.targetsExamined += result.examined();
        this.damaged += result.damaged();
    }

    boolean canSpawnEntity() {
        return this.entitiesSpawned < this.budget.maxEntitySpawns();
    }

    void recordEntitySpawn() {
        this.entitiesSpawned++;
    }

    EffectTickCounters counters() {
        return new EffectTickCounters(this.targetsExamined, this.damaged, this.hitboxCalls, this.entitiesSpawned);
    }
}
