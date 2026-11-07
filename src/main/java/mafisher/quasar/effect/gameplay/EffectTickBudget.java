package mafisher.quasar.effect.gameplay;

/** Caller-supplied work limits for one gameplay effect tick. */
public record EffectTickBudget(
    int maxTargetsExamined,
    int maxHitboxCalls,
    int maxEntitySpawns,
    int maxBlockChanges,
    int maxWorldSamples,
    int maxForceTargets,
    int maxDebrisPieces
) {
    public EffectTickBudget {
        if (maxTargetsExamined < 0 || maxHitboxCalls < 0 || maxEntitySpawns < 0
            || maxBlockChanges < 0 || maxWorldSamples < 0
            || maxForceTargets < 0 || maxDebrisPieces < 0) {
            throw new IllegalArgumentException("Effect tick budgets cannot be negative");
        }
    }

    /** Source-compatible constructor for effects that do not use forces or debris. */
    public EffectTickBudget(
        int maxTargetsExamined,
        int maxHitboxCalls,
        int maxEntitySpawns,
        int maxBlockChanges,
        int maxWorldSamples
    ) {
        this(maxTargetsExamined, maxHitboxCalls, maxEntitySpawns, maxBlockChanges, maxWorldSamples, 0, 0);
    }

    /** Source-compatible constructor for effects that do not sample the world. */
    public EffectTickBudget(
        int maxTargetsExamined,
        int maxHitboxCalls,
        int maxEntitySpawns,
        int maxBlockChanges
    ) {
        this(maxTargetsExamined, maxHitboxCalls, maxEntitySpawns, maxBlockChanges, 0, 0, 0);
    }

    /** Source-compatible constructor for effects that do not change blocks or sample the world. */
    public EffectTickBudget(int maxTargetsExamined, int maxHitboxCalls, int maxEntitySpawns) {
        this(maxTargetsExamined, maxHitboxCalls, maxEntitySpawns, 0, 0, 0, 0);
    }

    /**
     * A diagnostic budget for tests and admin-only tools; never use it as a balance default.
     * Safety ceilings such as the per-level block application cap and the raycast distance limit
     * still apply.
     */
    public static EffectTickBudget unlimited() {
        return new EffectTickBudget(
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE
        );
    }
}
