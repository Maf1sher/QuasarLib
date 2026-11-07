package mafisher.quasar.effect;

/**
 * Snapshot of one level's soft capacity state. {@code overBudget} is true when a single effect
 * is larger than its whole budget, in which case the engine admits it and reports the excess
 * instead of rejecting it.
 */
public record EffectBudgetReport(
    int standaloneVisuals,
    int standaloneVisualBudget,
    int segments,
    int segmentBudget,
    int gameplaySessions,
    int gameplaySessionBudget,
    int pendingGameplaySessions,
    int gameplayPendingBudget,
    int cinematicSessions,
    int cinematicSessionBudget,
    long culledEffects,
    long deferredStarts,
    long droppedEntitySpawns
) {
    public boolean overBudget() {
        return this.standaloneVisuals > this.standaloneVisualBudget
            || this.segments > this.segmentBudget
            || this.gameplaySessions > this.gameplaySessionBudget
            || this.cinematicSessions > this.cinematicSessionBudget;
    }
}
