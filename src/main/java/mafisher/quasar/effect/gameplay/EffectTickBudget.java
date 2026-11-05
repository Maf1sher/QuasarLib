package mafisher.quasar.effect.gameplay;

/** Caller-supplied work limits for one gameplay effect tick. */
public record EffectTickBudget(int maxTargetsExamined, int maxHitboxCalls, int maxEntitySpawns) {
    public EffectTickBudget {
        if (maxTargetsExamined < 0 || maxHitboxCalls < 0 || maxEntitySpawns < 0) {
            throw new IllegalArgumentException("Effect tick budgets cannot be negative");
        }
    }

    /** A diagnostic budget for tests and admin-only tools; never use it as a balance default. */
    public static EffectTickBudget unlimited() {
        return new EffectTickBudget(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
}
