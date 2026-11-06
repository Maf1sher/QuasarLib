package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.EffectHitResult;
import org.junit.jupiter.api.Test;

class EffectTickBudgetStateTest {
    @Test
    void enforcesTargetsCallsAndSpawnsThenResets() {
        EffectTickBudgetState state = new EffectTickBudgetState(new EffectTickBudget(2, 2, 1));

        assertTrue(state.canApplyHitbox());
        state.recordHitboxCall();
        state.recordHitResult(new EffectHitResult(1, 1));
        assertEquals(1, state.remainingTargets());

        assertTrue(state.canApplyHitbox());
        state.recordHitboxCall();
        state.recordHitResult(new EffectHitResult(1, 0));
        assertEquals(0, state.remainingTargets());
        assertFalse(state.canApplyHitbox());

        assertTrue(state.canSpawnEntity());
        state.recordEntitySpawn();
        assertFalse(state.canSpawnEntity());

        state.recordBlockChangeQueued();
        state.recordBlockChangesApplied(2);
        state.recordBlockChangesVetoed(3);

        assertEquals(new EffectTickCounters(2, 1, 2, 1, 1, 2, 3), state.counters());

        state.reset();
        assertEquals(EffectTickCounters.EMPTY, state.counters());
        assertEquals(2, state.remainingTargets());
        assertTrue(state.canApplyHitbox());
        assertTrue(state.canSpawnEntity());
    }

    @Test
    void returnsNoWorkWhenEitherLimitIsZero() {
        EffectTickBudgetState noTargets = new EffectTickBudgetState(new EffectTickBudget(0, 5, 0));
        EffectTickBudgetState noCalls = new EffectTickBudgetState(new EffectTickBudget(5, 0, 0));

        assertFalse(noTargets.canApplyHitbox());
        assertFalse(noCalls.canApplyHitbox());
        assertFalse(noTargets.canSpawnEntity());
    }

    @Test
    void rejectsNegativeBudgetsAndInvalidCounters() {
        assertThrows(IllegalArgumentException.class, () -> new EffectTickBudget(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectTickBudget(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectTickBudget(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new EffectTickBudget(0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new EffectTickCounters(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectTickCounters(1, 2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectTickCounters(0, 0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectTickCounters(0, 0, 0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new EffectTickCounters(0, 0, 0, 0, 0, 0, -1));
    }
}
