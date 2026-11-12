package mafisher.quasar.effect.runtime;

import org.jetbrains.annotations.ApiStatus;

/** Resolves the soft capacity limits for one level. Tests can supply fixed values. */
@ApiStatus.Internal
public interface EffectBudgetSource {
    int standaloneVisuals();

    int segments();

    int gameplaySessions();

    int gameplaySessionsPerOwner();

    int gameplayStartsPerOwnerPerTick();

    int gameplayPending();

    int cinematicSessions();

    int blockChangesPerLevelTick();

    int sectionEditsPerLevelTick();

    int entitySpawnsPerLevelTick();
}
