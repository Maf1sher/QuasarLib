package mafisher.quasar.effect;

/**
 * Author-declared importance of one effect. Capacity budgets are soft: when a level is over
 * budget the engine culls the lowest-priority effect instead of rejecting a start with an
 * exception. Ties are broken by age, oldest first.
 */
public enum EffectPriority {
    /** Ambient decoration that may be culled first. */
    BACKGROUND(0),
    /** Non-essential presentation below the default. */
    LOW(1),
    /** Default for unannotated effects. */
    NORMAL(2),
    /** Gameplay-relevant presentation that should outlive ambient effects. */
    HIGH(3),
    /** Scripted or focal effects that should only be culled by another critical effect. */
    CRITICAL(4);

    private final int weight;

    EffectPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return this.weight;
    }

    public boolean isAtLeast(EffectPriority other) {
        return this.weight >= other.weight;
    }

    /** Parses a persisted name, returning {@code fallback} for absent or unknown values. */
    public static EffectPriority byName(String name, EffectPriority fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }
}
