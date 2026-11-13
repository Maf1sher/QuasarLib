package mafisher.quasar.api.gameplay.tick;

import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import mafisher.quasar.api.effect.damage.EffectHitResult;

/** Mutable per-tick accounting guarded by one immutable budget. */
@ApiStatus.Internal
public final class EffectTickBudgetState {
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

    public EffectTickBudgetState(EffectTickBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public void reset() {
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

    public int remainingTargets() {
        return Math.max(0, this.budget.maxTargetsExamined() - this.targetsExamined);
    }

    public boolean canApplyHitbox() {
        return this.remainingTargets() > 0 && this.hitboxCalls < this.budget.maxHitboxCalls();
    }

    public void recordHitboxCall() {
        this.hitboxCalls++;
    }

    public void recordHitResult(EffectHitResult result) {
        this.targetsExamined += result.examined();
        this.damaged += result.damaged();
    }

    public boolean canSpawnEntity() {
        return this.entitiesSpawned < this.budget.maxEntitySpawns();
    }

    public void recordEntitySpawn() {
        this.entitiesSpawned++;
    }

    public void recordBlockChangeQueued() {
        this.blockChangesQueued++;
    }

    public void recordBlockChangesApplied(int applied) {
        this.blockChangesApplied += applied;
    }

    public void recordBlockChangesVetoed(int vetoed) {
        this.blockChangesVetoed += vetoed;
    }

    public int remainingSectionEdits() {
        return Math.max(0, this.budget.maxSectionEdits() - this.sectionEdits);
    }

    public boolean tryAcquireSectionEdit() {
        if (this.remainingSectionEdits() <= 0) {
            return false;
        }
        this.sectionEdits++;
        return true;
    }

    public boolean canSampleWorld() {
        return this.worldSamples < this.budget.maxWorldSamples();
    }

    /** Consumes one world sample; refuses and returns {@code false} when the budget is exhausted. */
    public boolean trySampleWorld() {
        if (!this.canSampleWorld()) {
            return false;
        }
        this.worldSamples++;
        return true;
    }

    public int remainingForceTargets() {
        return Math.max(0, this.budget.maxForceTargets() - this.forceTargetsExamined);
    }

    /** Consumes one force-field candidate; returns {@code false} when the budget is exhausted. */
    public boolean tryConsumeForceTarget() {
        if (this.remainingForceTargets() <= 0) {
            return false;
        }
        this.forceTargetsExamined++;
        return true;
    }

    public void recordForceAffected() {
        this.forceTargetsAffected++;
    }

    public int remainingDebrisPieces() {
        return Math.max(0, this.budget.maxDebrisPieces() - this.debrisPieces);
    }

    public void recordDebrisPieces(int pieces) {
        if (pieces < 0) {
            throw new IllegalArgumentException("Debris piece count cannot be negative");
        }
        this.debrisPieces += pieces;
    }

    public EffectTickCounters counters() {
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
