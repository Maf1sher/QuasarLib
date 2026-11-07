package mafisher.quasar.effect;

import mafisher.quasar.QuasarConfig;

/**
 * Resolved server-side capacity budgets. Values come from the server config and fall back to
 * their defaults while the config is not loaded, so headless tests and dedicated servers see
 * deterministic budgets.
 *
 * <p>Budgets are soft quality thresholds. They never reject a start with an exception; see
 * {@link EffectPriority} and {@link EffectBudgetService}.
 */
public final class EffectBudgets {
    private EffectBudgets() {
    }

    public static int standaloneVisuals() {
        return QuasarConfig.standaloneVisualBudget();
    }

    public static int segments() {
        return QuasarConfig.segmentBudget();
    }

    public static int gameplaySessions() {
        return QuasarConfig.gameplaySessionBudget();
    }

    public static int gameplaySessionsPerOwner() {
        return QuasarConfig.gameplaySessionsPerOwnerBudget();
    }

    public static int gameplayStartsPerOwnerPerTick() {
        return QuasarConfig.gameplayStartsPerOwnerPerTickBudget();
    }

    public static int gameplayPending() {
        return QuasarConfig.gameplayPendingBudget();
    }

    public static int cinematicSessions() {
        return QuasarConfig.cinematicSessionBudget();
    }

    public static int entitySpawnsPerLevelTick() {
        return QuasarConfig.entitySpawnsPerLevelTickBudget();
    }
}
