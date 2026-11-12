package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.effect.runtime.RecordingEffectStore;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.effect.runtime.TestEffectRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import mafisher.quasar.testing.GlobalStateTest;

@GlobalStateTest
class BlockRollbackServiceTest {
    private static final UUID EFFECT = UUID.fromString("00000000-0000-0000-0000-000000000040");
    private static final BlockPos FIRST = new BlockPos(0, 64, 0);
    private static final BlockPos SECOND = new BlockPos(1, 64, 0);

    private ServerLevel level;
    private BlockState air;
    private BlockState stone;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        this.air = Blocks.AIR.defaultBlockState();
        this.stone = Blocks.STONE.defaultBlockState();
        when(this.level.hasChunkAt(any())).thenReturn(true);
        when(this.level.setBlockAndUpdate(any(), any())).thenReturn(true);
        TestEffectRuntime.install(
            this.level, new RecordingEffectStore(), new RecordingPacketSink(), new TestBudgetSource()
        );
    }

    @AfterEach
    void tearDown() {
        TestEffectRuntime.uninstall(this.level);
    }

    @Test
    void rollsBackWithinTheBudgetAndCompletes() {
        BlockJournal journal = journal();
        journal.beginRollback();
        when(this.level.getBlockState(FIRST)).thenReturn(this.air);
        when(this.level.getBlockState(SECOND)).thenReturn(this.air);

        assertTrue(BlockRollbackService.schedule(this.level, EFFECT, journal));
        assertEquals(1, BlockRollbackService.activeCount(this.level));

        assertEquals(0, BlockRollbackService.tick(this.level, 1));
        assertEquals(1, BlockRollbackService.activeCount(this.level));
        verify(this.level, times(1)).setBlockAndUpdate(any(), any());

        BlockRollbackService.tick(this.level, 1);
        assertEquals(0, BlockRollbackService.activeCount(this.level));
        verify(this.level).setBlockAndUpdate(FIRST, this.stone);
        verify(this.level).setBlockAndUpdate(SECOND, this.stone);
    }

    @Test
    void skipsTruncatedJournals() {
        BlockJournal journal = new BlockJournal(1);
        journal.record(this.level, new BlockChange(FIRST, this.air), this.stone);
        journal.record(this.level, new BlockChange(SECOND, this.air), this.stone);

        assertFalse(BlockRollbackService.schedule(this.level, EFFECT, journal));
        assertEquals(0, BlockRollbackService.activeCount(this.level));
    }

    @Test
    void keepsFailedRollbacksScheduledUntilTheyAdvance() {
        BlockJournal journal = journal();
        journal.beginRollback();
        when(this.level.getBlockState(SECOND)).thenReturn(this.air);
        when(this.level.setBlockAndUpdate(any(), any())).thenReturn(false);

        assertTrue(BlockRollbackService.schedule(this.level, EFFECT, journal));
        assertEquals(0, BlockRollbackService.tick(this.level, 1));
        assertEquals(1, BlockRollbackService.activeCount(this.level));

        when(this.level.setBlockAndUpdate(any(), any())).thenReturn(true);
        when(this.level.getBlockState(FIRST)).thenReturn(this.air);
        BlockRollbackService.tick(this.level, 2);

        assertEquals(0, BlockRollbackService.activeCount(this.level));
        verify(this.level, times(2)).setBlockAndUpdate(SECOND, this.stone);
        verify(this.level).setBlockAndUpdate(FIRST, this.stone);
    }

    @Test
    void skipsPendingJournalsWithMissingCursors() {
        BlockJournal journal = journal();
        journal.beginRollback();
        CompoundTag tag = journal.save();
        tag.remove("cursor");

        BlockJournal loaded = BlockJournal.load(tag);

        assertTrue(loaded.isTruncated());
        assertFalse(BlockRollbackService.schedule(this.level, EFFECT, loaded));
        assertEquals(0, BlockRollbackService.activeCount(this.level));
    }

    @Test
    void clearsScheduledTasks() {
        BlockJournal journal = journal();
        assertTrue(BlockRollbackService.schedule(this.level, EFFECT, journal));

        BlockRollbackService.clear(this.level);

        assertEquals(0, BlockRollbackService.activeCount(this.level));
    }

    @Test
    void reschedulingDoesNotResetProgress() {
        BlockJournal journal = journal();
        journal.beginRollback();
        when(this.level.getBlockState(FIRST)).thenReturn(this.air);
        when(this.level.getBlockState(SECOND)).thenReturn(this.air);
        BlockRollbackService.schedule(this.level, EFFECT, journal);
        BlockRollbackService.tick(this.level, 1);

        BlockRollbackService.schedule(this.level, EFFECT, journal);
        BlockRollbackService.tick(this.level, 1);

        assertEquals(0, BlockRollbackService.activeCount(this.level));
        verify(this.level, times(2)).setBlockAndUpdate(any(), any());
    }

    @Test
    void keepsRollbackTasksPerLevel() {
        ServerLevel other = mock(ServerLevel.class);
        when(other.hasChunkAt(any())).thenReturn(true);
        TestEffectRuntime.install(
            other, new RecordingEffectStore(), new RecordingPacketSink(), new TestBudgetSource()
        );
        try {
            assertTrue(BlockRollbackService.schedule(this.level, EFFECT, journal()));
            assertTrue(BlockRollbackService.schedule(other, UUID.randomUUID(), journal()));

            assertEquals(1, BlockRollbackService.activeCount(this.level));
            assertEquals(1, BlockRollbackService.activeCount(other));

            BlockRollbackService.clear(this.level);

            assertEquals(0, BlockRollbackService.activeCount(this.level));
            assertEquals(1, BlockRollbackService.activeCount(other));
        } finally {
            TestEffectRuntime.uninstall(other);
        }
    }

    private BlockJournal journal() {
        BlockJournal journal = new BlockJournal();
        journal.record(this.level, new BlockChange(FIRST, this.air), this.stone);
        journal.record(this.level, new BlockChange(SECOND, this.air), this.stone);
        return journal;
    }
}
