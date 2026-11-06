package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PendingBlockChangesTest {
    private ServerLevel level;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isInWorldBounds(any())).thenReturn(true);
        when(this.level.hasChunkAt(any())).thenReturn(true);
        when(this.level.getBlockState(any())).thenReturn(mock(BlockState.class));
        when(this.level.setBlockAndUpdate(any(), any())).thenReturn(true);
    }

    @Test
    void appliesInInsertionOrderWithinTheBudget() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockChange first = change(0);
        BlockChange second = change(1);
        assertTrue(pending.enqueue(first));
        assertTrue(pending.enqueue(second));

        PendingBlockChanges.ApplyResult firstApply = pending.apply(this.level, 1);
        assertEquals(1, firstApply.processed());
        assertEquals(1, firstApply.applied());
        assertEquals(0, firstApply.vetoed());
        assertEquals(1, pending.size());
        PendingBlockChanges.ApplyResult secondApply = pending.apply(this.level, 1);
        assertEquals(1, secondApply.processed());
        assertEquals(1, secondApply.applied());
        assertEquals(0, secondApply.vetoed());
        assertEquals(0, pending.size());

        InOrder order = inOrder(this.level);
        order.verify(this.level).setBlockAndUpdate(first.position(), first.state());
        order.verify(this.level).setBlockAndUpdate(second.position(), second.state());
    }

    @Test
    void rejectsChangesAtTheSafetyCap() {
        PendingBlockChanges pending = new PendingBlockChanges(1);

        assertTrue(pending.enqueue(change(0)));
        assertFalse(pending.enqueue(change(1)));
        assertEquals(1, pending.size());
    }

    @Test
    void skipsOutOfBoundsPositionsAndUnloadedChunks() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockChange outOfBounds = change(0);
        BlockChange unloaded = change(1);
        when(this.level.isInWorldBounds(outOfBounds.position())).thenReturn(false);
        when(this.level.hasChunkAt(unloaded.position())).thenReturn(false);
        pending.enqueue(outOfBounds);
        pending.enqueue(unloaded);

        PendingBlockChanges.ApplyResult result = pending.apply(this.level, 4);
        assertEquals(2, result.processed());
        assertEquals(0, result.applied());
        assertEquals(0, pending.size());
        verify(this.level, never()).setBlockAndUpdate(any(), any());
    }

    @Test
    void continuesAfterAnIndividualBlockUpdateFailure() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockChange failing = change(0);
        BlockChange succeeding = change(1);
        when(this.level.setBlockAndUpdate(failing.position(), failing.state()))
            .thenThrow(new RuntimeException("test failure"));
        pending.enqueue(failing);
        pending.enqueue(succeeding);

        PendingBlockChanges.ApplyResult result = pending.apply(this.level, 4);
        assertEquals(2, result.processed());
        assertEquals(1, result.applied());
        assertEquals(0, result.vetoed());
        verify(this.level).setBlockAndUpdate(succeeding.position(), succeeding.state());
    }

    @Test
    void vetoesOneChangeAndContinuesWithTheRest() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockChange vetoed = change(0);
        BlockChange applied = change(1);
        pending.enqueue(vetoed);
        pending.enqueue(applied);

        PendingBlockChanges.ApplyResult result = pending.apply(
            this.level,
            4,
            (change, currentState) -> change != vetoed
        );

        assertEquals(2, result.processed());
        assertEquals(1, result.applied());
        assertEquals(1, result.vetoed());
        verify(this.level, never()).setBlockAndUpdate(vetoed.position(), vetoed.state());
        verify(this.level).setBlockAndUpdate(applied.position(), applied.state());
    }

    @Test
    void exposesTheCurrentStateToTheGate() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockChange change = change(0);
        BlockState currentState = mock(BlockState.class);
        when(this.level.getBlockState(change.position())).thenReturn(currentState);
        AtomicReference<BlockState> seen = new AtomicReference<>();
        pending.enqueue(change);

        pending.apply(this.level, 1, (queued, state) -> {
            seen.set(state);
            return true;
        });

        assertEquals(currentState, seen.get());
    }

    @Test
    void skipsUnchangedBlocksWithoutInvokingTheGate() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockChange change = change(0);
        when(this.level.getBlockState(change.position())).thenReturn(change.state());
        AtomicBoolean gateCalled = new AtomicBoolean();
        pending.enqueue(change);

        PendingBlockChanges.ApplyResult result = pending.apply(this.level, 1, (queued, state) -> {
            gateCalled.set(true);
            return true;
        });

        assertEquals(1, result.processed());
        assertEquals(0, result.applied());
        assertEquals(0, result.vetoed());
        assertFalse(gateCalled.get());
        verify(this.level, never()).setBlockAndUpdate(any(), any());
    }

    @Test
    void countsAGateFailureAsAVeto() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockChange change = change(0);
        pending.enqueue(change);

        PendingBlockChanges.ApplyResult result = pending.apply(this.level, 1, (queued, state) -> {
            throw new RuntimeException("test failure");
        });

        assertEquals(1, result.processed());
        assertEquals(0, result.applied());
        assertEquals(1, result.vetoed());
        verify(this.level, never()).setBlockAndUpdate(any(), any());
    }

    @Test
    void processesAChangeThatTheLevelDoesNotApply() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockChange change = change(0);
        when(this.level.setBlockAndUpdate(change.position(), change.state())).thenReturn(false);
        pending.enqueue(change);

        PendingBlockChanges.ApplyResult result = pending.apply(this.level, 1);

        assertEquals(1, result.processed());
        assertEquals(0, result.applied());
        assertEquals(0, pending.size());
    }

    @Test
    void recordsOnlyAppliedChangesInTheJournal() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockJournal journal = new BlockJournal();
        BlockChange applied = change(0);
        BlockChange vetoed = change(1);
        pending.enqueue(vetoed);
        pending.enqueue(applied);

        PendingBlockChanges.ApplyResult result = pending.apply(
            this.level,
            4,
            (change, currentState) -> change != vetoed,
            journal
        );

        assertEquals(1, result.vetoed());
        assertEquals(1, result.applied());
        assertEquals(1, journal.size());
        assertTrue(journal.consumeDirty());
    }

    @Test
    void discardsJournalEntriesWhenTheWorldWriteFails() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockJournal journal = new BlockJournal();
        BlockChange change = change(0);
        when(this.level.setBlockAndUpdate(change.position(), change.state())).thenReturn(false);
        pending.enqueue(change);

        PendingBlockChanges.ApplyResult result = pending.apply(
            this.level, 1, (queued, currentState) -> true, journal
        );

        assertEquals(0, result.applied());
        assertEquals(0, journal.size());
    }

    @Test
    void doesNotJournalNoOpChanges() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        BlockJournal journal = new BlockJournal();
        BlockChange change = change(0);
        when(this.level.getBlockState(change.position())).thenReturn(change.state());
        pending.enqueue(change);

        PendingBlockChanges.ApplyResult result = pending.apply(
            this.level, 1, (queued, currentState) -> true, journal
        );

        assertEquals(1, result.processed());
        assertEquals(0, result.applied());
        assertEquals(0, journal.size());
    }

    @Test
    void clearsPendingChanges() {
        PendingBlockChanges pending = new PendingBlockChanges(4);
        pending.enqueue(change(0));

        pending.clear();

        assertEquals(0, pending.size());
    }

    @Test
    void rejectsNegativeCapacityAndBudget() {
        assertThrows(IllegalArgumentException.class, () -> new PendingBlockChanges(-1));
        PendingBlockChanges pending = new PendingBlockChanges(1);

        assertThrows(IllegalArgumentException.class, () -> pending.apply(this.level, -1));
    }

    private static BlockChange change(int x) {
        return new BlockChange(new BlockPos(x, 64, 0), mock(BlockState.class));
    }
}
