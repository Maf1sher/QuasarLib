package mafisher.quasar.effect.gameplay;

/** Caller-supplied work limits for one gameplay effect tick. */
public record EffectTickBudget(
    int maxTargetsExamined,
    int maxHitboxCalls,
    int maxEntitySpawns,
    int maxBlockChanges
) {
    public EffectTickBudget {
        if (maxTargetsExamined < 0 || maxHitboxCalls < 0 || maxEntitySpawns < 0 || maxBlockChanges < 0) {
            throw new IllegalArgumentException("Effect tick budgets cannot be negative");
        }
    }

    /** Source-compatible constructor for effects that do not change blocks. */
    public EffectTickBudget(int maxTargetsExamined, int maxHitboxCalls, int maxEntitySpawns) {
        this(maxTargetsExamined, maxHitboxCalls, maxEntitySpawns, 0);
    }

    /**
     * A diagnostic budget for tests and admin-only tools; never use it as a balance default.
     * Safety ceilings such as the per-level block application cap still apply.
     */
    public static EffectTickBudget unlimited() {
        return new EffectTickBudget(
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE
        );
    }
}
