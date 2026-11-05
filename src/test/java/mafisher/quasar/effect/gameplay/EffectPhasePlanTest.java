package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EffectPhasePlanTest {
    private static final EffectPhasePlan PLAN = EffectPhasePlan.of(
        new EffectPhase("telegraph", 20),
        new EffectPhase("strike", 10),
        new EffectPhase("fade", 20)
    );

    @Test
    void derivesPhaseFromAgeAtExactBoundaries() {
        assertEquals(0, PLAN.phaseIndexAt(0));
        assertEquals(0, PLAN.phaseIndexAt(19));
        assertEquals(1, PLAN.phaseIndexAt(20));
        assertEquals(1, PLAN.phaseIndexAt(29));
        assertEquals(2, PLAN.phaseIndexAt(30));
        assertEquals(2, PLAN.phaseIndexAt(49));
        assertEquals(-1, PLAN.phaseIndexAt(50));
    }

    @Test
    void computesPhaseOffsetsAndProgress() {
        assertEquals(0, PLAN.phaseStartTick(0));
        assertEquals(20, PLAN.phaseStartTick(1));
        assertEquals(30, PLAN.phaseStartTick(2));
        assertEquals(10, PLAN.phaseDurationTicks(1));
        assertEquals(0, PLAN.phaseAgeTicks(30));
        assertEquals(5, PLAN.phaseAgeTicks(35));
        assertEquals(0.0F, PLAN.phaseProgress(30));
        assertEquals(0.5F, PLAN.phaseProgress(25));
        assertEquals(1.0F, PLAN.phaseProgress(50));
        assertEquals(50, PLAN.totalDurationTicks());
        assertEquals(3, PLAN.phaseCount());
    }

    @Test
    void rejectsInvalidPhasesAndAges() {
        assertThrows(IllegalArgumentException.class, () -> EffectPhasePlan.of());
        assertThrows(IllegalArgumentException.class, () -> new EffectPhase("", 10));
        assertThrows(IllegalArgumentException.class, () -> new EffectPhase("bad", 0));
        assertThrows(IllegalArgumentException.class, () -> EffectPhasePlan.of(
            new EffectPhase("first", 40_000),
            new EffectPhase("second", 40_000)
        ));
        assertThrows(IllegalArgumentException.class, () -> PLAN.phaseIndexAt(-1));
        assertThrows(IllegalArgumentException.class, () -> PLAN.phaseStartTick(3));
        assertThrows(IllegalArgumentException.class, () -> PLAN.phaseDurationTicks(-1));
    }
}
