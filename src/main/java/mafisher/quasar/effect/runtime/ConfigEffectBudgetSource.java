package mafisher.quasar.effect.runtime;

import mafisher.quasar.api.effect.EffectBudgets;
import org.jetbrains.annotations.ApiStatus;

/** Budget source that reads the current server configuration on every query. */
@ApiStatus.Internal
public final class ConfigEffectBudgetSource implements EffectBudgetSource {
    @Override
    public int standaloneVisuals() {
        return EffectBudgets.standaloneVisuals();
    }

    @Override
    public int segments() {
        return EffectBudgets.segments();
    }

    @Override
    public int gameplaySessions() {
        return EffectBudgets.gameplaySessions();
    }

    @Override
    public int gameplaySessionsPerOwner() {
        return EffectBudgets.gameplaySessionsPerOwner();
    }

    @Override
    public int gameplayStartsPerOwnerPerTick() {
        return EffectBudgets.gameplayStartsPerOwnerPerTick();
    }

    @Override
    public int gameplayPending() {
        return EffectBudgets.gameplayPending();
    }

    @Override
    public int cinematicSessions() {
        return EffectBudgets.cinematicSessions();
    }

    @Override
    public int blockChangesPerLevelTick() {
        return EffectBudgets.blockChangesPerLevelTick();
    }

    @Override
    public int sectionEditsPerLevelTick() {
        return EffectBudgets.sectionEditsPerLevelTick();
    }

    @Override
    public int entitySpawnsPerLevelTick() {
        return EffectBudgets.entitySpawnsPerLevelTick();
    }
}
