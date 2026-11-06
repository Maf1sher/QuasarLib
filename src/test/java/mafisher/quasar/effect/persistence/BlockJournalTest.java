package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import mafisher.quasar.effect.block.BlockChange;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockJournalTest {
    private ServerLevel level;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.hasChunkAt(any())).thenReturn(true);
        when(this.level.setBlockAndUpdate(any(), any())).thenReturn(true);
    }

    @Test
    void recordsAndDiscardsEntries() {
        BlockJournal journal = new BlockJournal();
        BlockChange change = new BlockChange(
            new BlockPos(1, 64, -2),
            Blocks.AIR.defaultBlockState()
        );

        assertTrue(journal.record(this.level, change, Blocks.STONE.defaultBlockState()));
        assertEquals(1, journal.size());
        assertTrue(journal.consumeDirty());

        journal.discardLast();

        assertEquals(0, journal.size());
        assertTrue(journal.consumeDirty());
    }

    @Test
    void truncatesAtCapacityAndDisablesRollback() {
        BlockJournal journal = new BlockJournal(1);
        BlockChange first = new BlockChange(BlockPos.ZERO, Blocks.AIR.defaultBlockState());
        BlockChange second = new BlockChange(new BlockPos(1, 64, 0), Blocks.AIR.defaultBlockState());

        assertTrue(journal.record(this.level, first, Blocks.STONE.defaultBlockState()));
        assertFalse(journal.record(this.level, second, Blocks.STONE.defaultBlockState()));

        assertTrue(journal.isTruncated());
        assertFalse(journal.canRollback());
    }

    @Test
    void roundTripsNbtAndRestoresThePreviousState() {
        BlockJournal journal = new BlockJournal();
        BlockPos position = new BlockPos(2, 64, 3);
        BlockState oldState = Blocks.STONE.defaultBlockState();
        BlockState newState = Blocks.AIR.defaultBlockState();
        journal.record(this.level, new BlockChange(position, newState), oldState);

        BlockJournal loaded = BlockJournal.load(journal.save());
        loaded.beginRollback();
        when(this.level.getBlockState(position)).thenReturn(newState);

        BlockJournal.RollbackStep step = loaded.rollbackNext(this.level, 1);

        assertEquals(1, step.processed());
        assertEquals(1, step.restored());
        assertEquals(0, step.conflicts());
        assertTrue(step.complete());
        verify(this.level).setBlockAndUpdate(position, oldState);
    }

    @Test
    void skipsConflictingRollbackEntries() {
        BlockJournal journal = new BlockJournal();
        BlockPos position = new BlockPos(4, 64, 5);
        BlockState newState = Blocks.AIR.defaultBlockState();
        journal.record(this.level, new BlockChange(position, newState), Blocks.STONE.defaultBlockState());
        journal.beginRollback();
        when(this.level.getBlockState(position)).thenReturn(Blocks.DIRT.defaultBlockState());

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 1);

        assertEquals(1, step.processed());
        assertEquals(0, step.restored());
        assertEquals(1, step.conflicts());
        verify(this.level, never()).setBlockAndUpdate(any(), any());
    }

    @Test
    void loadsUnloadedChunksBeforeRollback() {
        BlockJournal journal = new BlockJournal();
        BlockPos position = new BlockPos(6, 64, 7);
        BlockState newState = Blocks.AIR.defaultBlockState();
        journal.record(this.level, new BlockChange(position, newState), Blocks.STONE.defaultBlockState());
        journal.beginRollback();
        when(this.level.hasChunkAt(position)).thenReturn(false);
        when(this.level.getChunkAt(position)).thenReturn(
            mock(net.minecraft.world.level.chunk.LevelChunk.class)
        );
        when(this.level.getBlockState(position)).thenReturn(newState);

        journal.rollbackNext(this.level, 1);

        verify(this.level).getChunkAt(position);
    }

    @Test
    void rejectsRecordingAfterRollbackStarts() {
        BlockJournal journal = new BlockJournal();
        journal.record(
            this.level,
            new BlockChange(BlockPos.ZERO, Blocks.AIR.defaultBlockState()),
            Blocks.STONE.defaultBlockState()
        );
        journal.beginRollback();

        assertFalse(journal.record(
            this.level,
            new BlockChange(new BlockPos(1, 64, 0), Blocks.AIR.defaultBlockState()),
            Blocks.STONE.defaultBlockState()
        ));
    }

    @Test
    void rejectsInvalidCapacities() {
        assertThrows(IllegalArgumentException.class, () -> new BlockJournal(0));
        assertThrows(IllegalArgumentException.class, () -> new BlockJournal(-2));
    }

    @Test
    void ignoresUnreadableEntriesWhenLoading() {
        CompoundTag tag = new CompoundTag();
        tag.put("entries", new net.minecraft.nbt.ListTag());

        assertEquals(0, BlockJournal.load(tag).size());
    }
}
