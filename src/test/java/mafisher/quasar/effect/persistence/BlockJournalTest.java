package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import mafisher.quasar.effect.block.BlockChange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockJournalTest {
    private static final BlockPos POSITION = new BlockPos(1, 64, 2);

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
    void refusesToRecordWhenTheBlockEntityCannotBeCaptured() {
        BlockEntity blockEntity = mock(BlockEntity.class);
        when(this.level.getBlockEntity(POSITION)).thenReturn(blockEntity);
        when(blockEntity.saveWithFullMetadata(any())).thenThrow(new RuntimeException("capture failed"));
        BlockJournal journal = new BlockJournal();

        assertFalse(journal.record(
            this.level,
            new BlockChange(POSITION, Blocks.AIR.defaultBlockState()),
            Blocks.STONE.defaultBlockState()
        ));

        assertEquals(0, journal.size());
        assertFalse(journal.isTruncated());
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusesToSnapshotASectionWhenABlockEntityCannotBeCaptured() {
        ChunkPos chunkPos = new ChunkPos(2, -3);
        BlockPos blockEntityPos = new BlockPos(33, 66, -45);
        LevelChunk chunk = mock(LevelChunk.class);
        BlockEntity blockEntity = mock(BlockEntity.class);
        when(chunk.getPos()).thenReturn(chunkPos);
        when(chunk.getBlockEntities()).thenReturn(java.util.Map.of(blockEntityPos, blockEntity));
        when(blockEntity.getBlockPos()).thenReturn(blockEntityPos);
        when(blockEntity.saveWithFullMetadata(any())).thenThrow(new RuntimeException("capture failed"));
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        LevelChunkSection section = new LevelChunkSection(
            states, mock(PalettedContainerRO.class)
        );
        BlockJournal journal = new BlockJournal();

        assertFalse(journal.recordSection(this.level, chunk, 4, section));

        assertEquals(0, journal.sectionCount());
        assertFalse(journal.isTruncated());
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
        journal.beginRollback();
        assertThrows(IllegalStateException.class, () -> journal.rollbackNext(this.level, 1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsAndRoundTripsASectionSnapshot() {
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(2, -3));
        when(chunk.getBlockEntities()).thenReturn(java.util.Map.of());
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        states.set(1, 2, 3, Blocks.STONE.defaultBlockState());
        LevelChunkSection section = new LevelChunkSection(
            states, mock(PalettedContainerRO.class)
        );

        BlockJournal journal = new BlockJournal();
        assertTrue(journal.recordSection(this.level, chunk, 4, section));
        assertEquals(1, journal.size());
        assertEquals(1, journal.sectionCount());
        assertTrue(journal.consumeDirty());

        BlockJournal loaded = BlockJournal.load(journal.save());
        assertEquals(1, loaded.sectionCount());
        assertEquals(1, loaded.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sectionRollbackReplaysChangedBlocksThroughSetBlock() {
        ChunkPos chunkPos = new ChunkPos(2, -3);
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getPos()).thenReturn(chunkPos);
        when(chunk.getBlockEntities()).thenReturn(java.util.Map.of());
        PalettedContainer<BlockState> snapshotStates = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        snapshotStates.set(1, 2, 3, Blocks.STONE.defaultBlockState());
        LevelChunkSection snapshot = new LevelChunkSection(
            snapshotStates, mock(PalettedContainerRO.class)
        );
        BlockJournal journal = new BlockJournal();
        assertTrue(journal.recordSection(this.level, chunk, 4, snapshot));

        PalettedContainer<BlockState> liveStates = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        LevelChunkSection live = sectionWithMockedBiomes(liveStates);
        when(chunk.getSections()).thenReturn(new LevelChunkSection[]{live});
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(chunkSource.getChunkNow(chunkPos.x, chunkPos.z)).thenReturn(chunk);
        when(this.level.getChunkSource()).thenReturn(chunkSource);
        when(this.level.getSectionIndexFromSectionY(4)).thenReturn(0);
        when(this.level.setBlock(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

        journal.beginRollback();
        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 4_096);

        assertTrue(step.complete());
        verify(this.level).setBlock(
            new BlockPos(33, 66, -45),
            Blocks.STONE.defaultBlockState(),
            Block.UPDATE_ALL
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void sectionRollbackRestoresBlockEntityContents() {
        ChunkPos chunkPos = new ChunkPos(2, -3);
        BlockPos chestPos = new BlockPos(33, 66, -45);
        LevelChunk chunk = mock(LevelChunk.class);
        BlockEntity blockEntity = mock(BlockEntity.class);
        CompoundTag saved = new CompoundTag();
        saved.putString("id", "minecraft:chest");
        when(chunk.getPos()).thenReturn(chunkPos);
        when(chunk.getBlockEntities()).thenReturn(java.util.Map.of(chestPos, blockEntity));
        when(blockEntity.getBlockPos()).thenReturn(chestPos);
        when(blockEntity.saveWithFullMetadata(any())).thenReturn(saved);
        PalettedContainer<BlockState> snapshotStates = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        snapshotStates.set(1, 2, 3, Blocks.CHEST.defaultBlockState());
        LevelChunkSection snapshot = new LevelChunkSection(
            snapshotStates, mock(PalettedContainerRO.class)
        );
        BlockJournal journal = new BlockJournal();
        assertTrue(journal.recordSection(this.level, chunk, 4, snapshot));

        PalettedContainer<BlockState> liveStates = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        LevelChunkSection live = sectionWithMockedBiomes(liveStates);
        when(chunk.getSections()).thenReturn(new LevelChunkSection[]{live});
        when(chunk.getBlockEntity(eq(chestPos), eq(LevelChunk.EntityCreationType.IMMEDIATE)))
            .thenReturn(blockEntity);
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(chunkSource.getChunkNow(chunkPos.x, chunkPos.z)).thenReturn(chunk);
        when(this.level.getChunkSource()).thenReturn(chunkSource);
        when(this.level.getSectionIndexFromSectionY(4)).thenReturn(0);
        when(this.level.setBlock(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

        journal.beginRollback();
        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 4_096);

        assertTrue(step.complete());
        verify(blockEntity).loadWithComponents(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsSectionRollbackPendingWhenAWriteFails() {
        ChunkPos chunkPos = new ChunkPos(2, -3);
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getPos()).thenReturn(chunkPos);
        when(chunk.getBlockEntities()).thenReturn(java.util.Map.of());
        PalettedContainer<BlockState> snapshotStates = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        snapshotStates.set(1, 2, 3, Blocks.STONE.defaultBlockState());
        LevelChunkSection snapshot = new LevelChunkSection(
            snapshotStates, mock(PalettedContainerRO.class)
        );
        BlockJournal journal = new BlockJournal();
        assertTrue(journal.recordSection(this.level, chunk, 4, snapshot));

        PalettedContainer<BlockState> liveStates = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        LevelChunkSection live = sectionWithMockedBiomes(liveStates);
        when(chunk.getSections()).thenReturn(new LevelChunkSection[]{live});
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(chunkSource.getChunkNow(chunkPos.x, chunkPos.z)).thenReturn(chunk);
        when(this.level.getChunkSource()).thenReturn(chunkSource);
        when(this.level.getSectionIndexFromSectionY(4)).thenReturn(0);
        when(this.level.setBlock(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        when(this.level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());

        journal.beginRollback();
        BlockJournal.RollbackStep failed = journal.rollbackNext(this.level, 4_096);

        assertFalse(failed.complete());
        assertEquals(1, failed.failed());
        assertEquals(0, failed.advanced());

        when(this.level.setBlock(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        BlockJournal.RollbackStep retried = journal.rollbackNext(this.level, 4_096);

        assertTrue(retried.complete());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deduplicatesSectionSnapshotsAcrossScatteredEdits() {
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(2, -3));
        when(chunk.getBlockEntities()).thenReturn(java.util.Map.of());
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        LevelChunkSection section = new LevelChunkSection(
            states, mock(PalettedContainerRO.class)
        );

        BlockJournal journal = new BlockJournal();
        assertTrue(journal.recordSection(this.level, chunk, 4, section));
        assertTrue(journal.recordSection(this.level, chunk, 4, section));
        assertEquals(1, journal.sectionCount());

        BlockJournal loaded = BlockJournal.load(journal.save());
        assertTrue(loaded.recordSection(this.level, chunk, 4, section));
        assertEquals(1, loaded.sectionCount());
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
        assertEquals(1, step.advanced());
        assertEquals(1, step.restored());
        assertEquals(0, step.conflicts());
        assertEquals(0, step.failed());
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
        assertEquals(1, step.advanced());
        assertEquals(0, step.restored());
        assertEquals(1, step.conflicts());
        assertEquals(0, step.failed());
        verify(this.level, never()).setBlockAndUpdate(any(), any());
    }

    @Test
    void requestsAChunkTicketInsteadOfLoadingAnUnloadedChunk() {
        BlockJournal journal = new BlockJournal();
        BlockPos position = new BlockPos(6, 64, 7);
        ChunkPos chunk = new ChunkPos(position);
        BlockState newState = Blocks.AIR.defaultBlockState();
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(this.level.getChunkSource()).thenReturn(chunkSource);
        when(this.level.hasChunkAt(position)).thenReturn(false);
        journal.record(this.level, new BlockChange(position, newState), Blocks.STONE.defaultBlockState());
        journal.beginRollback();

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 1);

        assertEquals(0, step.processed());
        assertEquals(1, step.waiting());
        assertFalse(step.complete());
        verify(chunkSource).addRegionTicket(any(), eq(chunk), eq(0), eq(chunk));
        verify(this.level, never()).getChunkAt(any());

        when(this.level.hasChunkAt(position)).thenReturn(true);
        when(this.level.getBlockState(position)).thenReturn(newState);
        step = journal.rollbackNext(this.level, 1);

        assertTrue(step.complete());
        verify(this.level).setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
        verify(chunkSource).removeRegionTicket(any(), eq(chunk), eq(0), eq(chunk));
    }

    @Test
    void keepsTheEntryPendingWhenTheChunkCannotLoad() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        when(this.level.hasChunkAt(POSITION)).thenReturn(false);

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 1);

        assertEquals(0, step.processed());
        assertEquals(0, step.advanced());
        assertEquals(1, step.waiting());
        assertFalse(step.complete());
        verify(this.level, never()).setBlockAndUpdate(any(), any());

        when(this.level.hasChunkAt(POSITION)).thenReturn(true);
        when(this.level.getBlockState(POSITION)).thenReturn(Blocks.AIR.defaultBlockState());
        step = journal.rollbackNext(this.level, 1);

        assertTrue(step.complete());
        verify(this.level).setBlockAndUpdate(POSITION, Blocks.STONE.defaultBlockState());
    }

    @Test
    void keepsTheEntryPendingWhenTheReadFails() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        when(this.level.getBlockState(POSITION)).thenThrow(new RuntimeException("read failed"));

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 1);

        assertEquals(0, step.advanced());
        assertEquals(1, step.failed());
        assertFalse(step.complete());

        doReturn(Blocks.AIR.defaultBlockState()).when(this.level).getBlockState(POSITION);
        step = journal.rollbackNext(this.level, 1);

        assertTrue(step.complete());
        verify(this.level).setBlockAndUpdate(POSITION, Blocks.STONE.defaultBlockState());
    }

    @Test
    void keepsTheEntryPendingWhenTheWriteIsRejected() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        when(this.level.getBlockState(POSITION)).thenReturn(Blocks.AIR.defaultBlockState());
        when(this.level.setBlockAndUpdate(POSITION, Blocks.STONE.defaultBlockState())).thenReturn(false);

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 1);

        assertEquals(0, step.advanced());
        assertEquals(1, step.failed());
        assertFalse(step.complete());

        doReturn(true).when(this.level).setBlockAndUpdate(
            POSITION,
            Blocks.STONE.defaultBlockState()
        );
        step = journal.rollbackNext(this.level, 1);

        assertTrue(step.complete());
        verify(this.level, times(2)).setBlockAndUpdate(POSITION, Blocks.STONE.defaultBlockState());
    }

    @Test
    void keepsTheEntryPendingWhenTheWriteThrows() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        when(this.level.getBlockState(POSITION)).thenReturn(Blocks.AIR.defaultBlockState());
        when(this.level.setBlockAndUpdate(POSITION, Blocks.STONE.defaultBlockState()))
            .thenThrow(new RuntimeException("write failed"));

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 1);

        assertEquals(0, step.advanced());
        assertEquals(1, step.failed());
        assertFalse(step.complete());

        doReturn(true).when(this.level).setBlockAndUpdate(
            POSITION,
            Blocks.STONE.defaultBlockState()
        );
        step = journal.rollbackNext(this.level, 1);

        assertTrue(step.complete());
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsTheSectionPendingUntilItsChunkLoads() {
        ChunkPos chunkPos = new ChunkPos(2, -3);
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getPos()).thenReturn(chunkPos);
        when(chunk.getBlockEntities()).thenReturn(java.util.Map.of());
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        LevelChunkSection section = new LevelChunkSection(
            states, mock(PalettedContainerRO.class)
        );
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(this.level.getChunkSource()).thenReturn(chunkSource);

        BlockJournal journal = new BlockJournal();
        assertTrue(journal.recordSection(this.level, chunk, 4, section));
        journal.beginRollback();
        when(chunkSource.getChunkNow(chunkPos.x, chunkPos.z)).thenReturn(null);

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 1);

        assertEquals(0, step.processed());
        assertEquals(1, step.waiting());
        assertFalse(step.complete());
        verify(chunkSource).addRegionTicket(any(), eq(chunkPos), eq(0), eq(chunkPos));

        when(chunkSource.getChunkNow(chunkPos.x, chunkPos.z)).thenReturn(chunk);
        when(chunk.getSections()).thenReturn(new LevelChunkSection[0]);
        step = journal.rollbackNext(this.level, 1);

        assertFalse(step.complete());
        assertEquals(1, step.processed());
        assertEquals(1, step.failed());
    }

    @Test
    void chargesOneSectionWithinABudgetBelowTheSectionCost() {
        assertEquals(1, BlockJournal.sectionCost(1));
        assertEquals(100, BlockJournal.sectionCost(100));
        assertEquals(4_096, BlockJournal.sectionCost(4_096));
        assertEquals(4_096, BlockJournal.sectionCost(16_384));
    }

    @Test
    void keepsPartialProgressWhenALaterEntryFails() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(1, 64, 0);
        BlockJournal journal = new BlockJournal();
        journal.record(this.level, new BlockChange(first, air), stone);
        journal.record(this.level, new BlockChange(second, air), stone);
        journal.beginRollback();
        when(this.level.getBlockState(second)).thenReturn(air);
        when(this.level.getBlockState(first)).thenThrow(new RuntimeException("read failed"));

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 2);

        assertEquals(2, step.processed());
        assertEquals(1, step.advanced());
        assertEquals(1, step.restored());
        assertEquals(1, step.failed());
        assertFalse(step.complete());

        doReturn(air).when(this.level).getBlockState(first);
        step = journal.rollbackNext(this.level, 2);

        assertTrue(step.complete());
        verify(this.level).setBlockAndUpdate(first, stone);
        verify(this.level).setBlockAndUpdate(second, stone);
    }

    @Test
    void writesTheJournalSchemaVersion() {
        assertEquals(
            BlockJournal.SCHEMA_VERSION,
            new BlockJournal().save().getInt("journal_schema")
        );
    }

    @Test
    void roundTripsPartialRollbackProgress() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(1, 64, 0);
        BlockJournal journal = new BlockJournal();
        journal.record(this.level, new BlockChange(first, air), stone);
        journal.record(this.level, new BlockChange(second, air), stone);
        journal.beginRollback();
        when(this.level.getBlockState(second)).thenReturn(air);

        BlockJournal.RollbackStep step = journal.rollbackNext(this.level, 1);
        assertFalse(step.complete());
        assertEquals(1, step.advanced());

        BlockJournal loaded = BlockJournal.load(journal.save());
        when(this.level.getBlockState(first)).thenReturn(air);
        step = loaded.rollbackNext(this.level, 1);

        assertTrue(step.complete());
        verify(this.level).setBlockAndUpdate(first, stone);
        verify(this.level, times(1)).setBlockAndUpdate(second, stone);
    }

    @Test
    void rejectsPendingRollbackWithoutCursors() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        CompoundTag tag = journal.save();
        tag.remove("cursor");

        BlockJournal loaded = BlockJournal.load(tag);

        assertTrue(loaded.isTruncated());
        assertFalse(loaded.canRollback());
        assertThrows(IllegalStateException.class, () -> loaded.rollbackNext(this.level, 1));
    }

    @Test
    void rejectsLegacyPendingRollbackWithoutCursors() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        CompoundTag tag = journal.save();
        tag.remove("journal_schema");
        tag.remove("cursor");

        BlockJournal loaded = BlockJournal.load(tag);

        assertTrue(loaded.isTruncated());
        assertFalse(loaded.canRollback());
    }

    @Test
    void rejectsOutOfRangeRollbackCursors() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        CompoundTag tag = journal.save();
        tag.putInt("cursor", journal.size() + 1);

        BlockJournal loaded = BlockJournal.load(tag);

        assertTrue(loaded.isTruncated());
        assertFalse(loaded.canRollback());
    }

    @Test
    void rejectsNewerJournalSchemas() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        CompoundTag tag = journal.save();
        tag.putInt("journal_schema", BlockJournal.SCHEMA_VERSION + 1);

        BlockJournal loaded = BlockJournal.load(tag);

        assertTrue(loaded.isTruncated());
        assertTrue(loaded.isRollbackPending());
        assertFalse(loaded.canRollback());
        assertEquals(0, loaded.size());
    }

    @Test
    void rejectsNegativeJournalSchemas() {
        BlockJournal journal = journalWithEntry();
        CompoundTag tag = journal.save();
        tag.putInt("journal_schema", -1);

        BlockJournal loaded = BlockJournal.load(tag);

        assertTrue(loaded.isTruncated());
        assertFalse(loaded.canRollback());
    }

    @Test
    void migratesLegacyJournalsThatStillHaveCursors() {
        BlockJournal journal = journalWithEntry();
        journal.beginRollback();
        CompoundTag tag = journal.save();
        tag.remove("journal_schema");

        BlockJournal loaded = BlockJournal.load(tag);

        assertFalse(loaded.isTruncated());
        assertTrue(loaded.isRollbackPending());
        assertTrue(loaded.canRollback());
    }

    @Test
    void keepsUnreadableEntriesTruncatedWhenTheTagClaimsOtherwise() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("journal_schema", BlockJournal.SCHEMA_VERSION);
        tag.putBoolean("rollback_pending", true);
        tag.putBoolean("truncated", false);
        net.minecraft.nbt.ListTag sections = new net.minecraft.nbt.ListTag();
        CompoundTag section = new CompoundTag();
        section.putInt("sy", 4_000);
        sections.add(section);
        tag.put("sections", sections);

        BlockJournal loaded = BlockJournal.load(tag);

        assertTrue(loaded.isTruncated());
        assertFalse(loaded.canRollback());
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

    @SuppressWarnings("unchecked")
    private static LevelChunkSection sectionWithMockedBiomes(PalettedContainer<BlockState> states) {
        PalettedContainerRO<Holder<Biome>> biomes = mock(PalettedContainerRO.class);
        when(biomes.recreate()).thenReturn(mock(PalettedContainer.class));
        return new LevelChunkSection(states, biomes);
    }

    private BlockJournal journalWithEntry() {
        BlockJournal journal = new BlockJournal();
        journal.record(
            this.level,
            new BlockChange(POSITION, Blocks.AIR.defaultBlockState()),
            Blocks.STONE.defaultBlockState()
        );
        return journal;
    }
}
