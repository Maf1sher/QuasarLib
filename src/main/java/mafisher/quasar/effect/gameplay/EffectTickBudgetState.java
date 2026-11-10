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
    private int blockChangesQueued;
    private int blockChangesApplied;
    private int blockChangesVetoed;
    private int sectionEdits;
    private int worldSamples;
    private int forceTargetsExamined;
    private int forceTargetsAffected;
    private int debrisPieces;

    EffectTickBudgetState(EffectTickBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    void reset() {
        this.targetsExamined = 0;
        this.damaged = 0;
        this.hitboxCalls = 0;
        this.entitiesSpawned = 0;
        this.blockChangesQueued = 0;
        this.blockChangesApplied = 0;
        this.blockChangesVetoed = 0;
        this.sectionEdits = 0;
        this.worldSamples = 0;
        this.forceTargetsExamined = 0;
        this.forceTargetsAffected = 0;
        this.debrisPieces = 0;
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

    void recordBlockChangeQueued() {
        this.blockChangesQueued++;
    }

    void recordBlockChangesApplied(int applied) {
        this.blockChangesApplied += applied;
    }

    void recordBlockChangesVetoed(int vetoed) {
        this.blockChangesVetoed += vetoed;
    }

    int remainingSectionEdits() {
        return Math.max(0, this.budget.maxSectionEdits() - this.sectionEdits);
    }

    boolean tryAcquireSectionEdit() {
        if (this.remainingSectionEdits() <= 0) {
            return false;
        }
        this.sectionEdits++;
        return true;
    }

    boolean canSampleWorld() {
        return this.worldSamples < this.budget.maxWorldSamples();
    }

    /** Consumes one world sample; refuses and returns {@code false} when the budget is exhausted. */
    boolean trySampleWorld() {
        if (!this.canSampleWorld()) {
            return false;
        }
        this.worldSamples++;
        return true;
    }

    int remainingForceTargets() {
        return Math.max(0, this.budget.maxForceTargets() - this.forceTargetsExamined);
    }

    /** Consumes one force-field candidate; returns {@code false} when the budget is exhausted. */
    boolean tryConsumeForceTarget() {
        if (this.remainingForceTargets() <= 0) {
            return false;
        }
        this.forceTargetsExamined++;
        return true;
    }

    void recordForceAffected() {
        this.forceTargetsAffected++;
    }

    int remainingDebrisPieces() {
        return Math.max(0, this.budget.maxDebrisPieces() - this.debrisPieces);
    }

    void recordDebrisPieces(int pieces) {
        if (pieces < 0) {
            throw new IllegalArgumentException("Debris piece count cannot be negative");
        }
        this.debrisPieces += pieces;
    }

    EffectTickCounters counters() {
        return new EffectTickCounters(
            this.targetsExamined,
            this.damaged,
            this.hitboxCalls,
            this.entitiesSpawned,
            this.blockChangesQueued,
            this.blockChangesApplied,
            this.blockChangesVetoed,
            this.worldSamples,
            this.forceTargetsExamined,
            this.forceTargetsAffected,
            this.debrisPieces,
            this.sectionEdits
        );
    }
}
