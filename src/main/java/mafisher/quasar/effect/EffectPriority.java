package mafisher.quasar.effect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(EffectPriority.class);

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

    /** Parses a persisted name and reports a missing or unreadable value with its effect context. */
    public static EffectPriority byName(String name, EffectPriority fallback, String context) {
        if (name == null || name.isEmpty()) {
            LOGGER.debug("Missing persisted priority for {}; using {}", context, fallback);
            return fallback;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException unknown) {
            LOGGER.warn("Unknown persisted priority '{}' for {}; using {}", name, context, fallback);
            return fallback;
        }
    }
}
