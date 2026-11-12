package mafisher.quasar.effect.runtime;

import mafisher.quasar.api.effect.EffectBudgets;

/** Mutable test budget source that starts from the configured defaults. */
public final class TestBudgetSource implements EffectBudgetSource {
    private int standaloneVisuals = EffectBudgets.standaloneVisuals();
    private int segments = EffectBudgets.segments();
    private int gameplaySessions = EffectBudgets.gameplaySessions();
    private int gameplaySessionsPerOwner = EffectBudgets.gameplaySessionsPerOwner();
    private int gameplayStartsPerOwnerPerTick = EffectBudgets.gameplayStartsPerOwnerPerTick();
    private int gameplayPending = EffectBudgets.gameplayPending();
    private int cinematicSessions = EffectBudgets.cinematicSessions();
    private int blockChangesPerLevelTick = EffectBudgets.blockChangesPerLevelTick();
    private int sectionEditsPerLevelTick = EffectBudgets.sectionEditsPerLevelTick();
    private int entitySpawnsPerLevelTick = EffectBudgets.entitySpawnsPerLevelTick();

    public TestBudgetSource standaloneVisuals(int value) {
        this.standaloneVisuals = value;
        return this;
    }

    public TestBudgetSource segments(int value) {
        this.segments = value;
        return this;
    }

    public TestBudgetSource cinematicSessions(int value) {
        this.cinematicSessions = value;
        return this;
    }

    @Override
    public int standaloneVisuals() {
        return this.standaloneVisuals;
    }

    @Override
    public int segments() {
        return this.segments;
    }

    @Override
    public int gameplaySessions() {
        return this.gameplaySessions;
    }

    @Override
    public int gameplaySessionsPerOwner() {
        return this.gameplaySessionsPerOwner;
    }

    @Override
    public int gameplayStartsPerOwnerPerTick() {
        return this.gameplayStartsPerOwnerPerTick;
    }

    @Override
    public int gameplayPending() {
        return this.gameplayPending;
    }

    @Override
    public int cinematicSessions() {
        return this.cinematicSessions;
    }

    @Override
    public int blockChangesPerLevelTick() {
        return this.blockChangesPerLevelTick;
    }

    @Override
    public int sectionEditsPerLevelTick() {
        return this.sectionEditsPerLevelTick;
    }

    @Override
    public int entitySpawnsPerLevelTick() {
        return this.entitySpawnsPerLevelTick;
    }
}
