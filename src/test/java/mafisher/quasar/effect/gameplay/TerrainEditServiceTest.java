package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import mafisher.quasar.QuasarConfig;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerrainEditServiceTest {
    private ServerLevel level;
    private ServerChunkCache chunkSource;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        this.chunkSource = mock(ServerChunkCache.class);
        when(this.level.getChunkSource()).thenReturn(this.chunkSource);
    }

    @AfterEach
    void tearDown() {
        TerrainEditService.clear(this.level);
    }

    @Test
    void rejectsAnOversizedReservationWithoutAcquiringAnyChunk() {
        List<ChunkPos> chunks = chunks(0, QuasarConfig.terrainTicketBudget() + 1);

        assertFalse(TerrainEditService.tryAcquireTickets(this.level, chunks));

        verify(this.chunkSource, org.mockito.Mockito.never())
            .addRegionTicket(any(), any(), anyInt(), any());
    }

    @Test
    void countsPendingReleasesAgainstTheAggregateBudget() {
        List<ChunkPos> first = chunks(0, 1_024);
        List<ChunkPos> second = chunks(1_024, 1_024);
        List<ChunkPos> third = chunks(2_048, 1_024);
        assertTrue(TerrainEditService.tryAcquireTickets(this.level, first));
        assertTrue(TerrainEditService.tryAcquireTickets(this.level, second));
        assertTrue(TerrainEditService.tryAcquireTickets(this.level, third));

        TerrainEditService.releaseTicketsWhenSettled(this.level, first);

        assertFalse(TerrainEditService.tryAcquireTickets(this.level, chunks(3_072, 1_025)));

        TerrainEditService.tick(this.level);

        assertTrue(TerrainEditService.tryAcquireTickets(this.level, chunks(3_072, 1_025)));
    }

    @Test
    void keepsAnUnloadedSyncQueuedUntilTheChunkLoads() {
        ChunkPos chunk = new ChunkPos(3, 4);
        when(this.chunkSource.getChunkNow(3, 4)).thenReturn(null);
        TerrainEditService.requestSync(this.level, chunk, false);

        assertEquals(1, TerrainEditService.pendingWorkCount(this.level));

        TerrainEditService.tick(this.level);

        assertEquals(1, TerrainEditService.pendingWorkCount(this.level));
    }

    @Test
    void keepsAFailedRelightQueued() {
        ChunkPos chunk = new ChunkPos(5, 6);
        LevelChunk levelChunk = mock(LevelChunk.class);
        when(this.chunkSource.getChunkNow(5, 6)).thenReturn(levelChunk);
        when(levelChunk.getHighestSectionPosition()).thenThrow(new RuntimeException("relight failed"));
        TerrainEditService.queuePriorityRelight(this.level, chunk);
        TerrainEditService.requestSync(this.level, chunk, true);

        TerrainEditService.tick(this.level);

        assertEquals(3, TerrainEditService.pendingWorkCount(this.level));
    }

    @Test
    void clearReleasesHeldAndPendingTickets() {
        List<ChunkPos> first = chunks(0, 3);
        List<ChunkPos> second = chunks(3, 3);
        assertTrue(TerrainEditService.tryAcquireTickets(this.level, first));
        assertTrue(TerrainEditService.tryAcquireTickets(this.level, second));
        TerrainEditService.releaseTicketsWhenSettled(this.level, first);

        TerrainEditService.clear(this.level);

        verify(this.chunkSource, org.mockito.Mockito.times(6))
            .removeRegionTicket(any(), any(), anyInt(), any());
    }

    private static List<ChunkPos> chunks(int start, int count) {
        List<ChunkPos> chunks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            chunks.add(new ChunkPos(start + index, 0));
        }
        return chunks;
    }
}
