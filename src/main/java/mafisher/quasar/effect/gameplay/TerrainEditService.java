package mafisher.quasar.effect.gameplay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import mafisher.quasar.QuasarConfig;
import mafisher.quasar.effect.EffectBudgets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Level-wide scheduling for bulk terrain edits: section-edit admission, deferred chunk relighting,
 * and one batched client resend per changed chunk. All work runs on the server thread.
 */
public final class TerrainEditService {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final int LIGHT_CHUNKS_PER_TICK = 2;
    /** Priority relights are the visible destruction front; let them keep up with section edits. */
    private static final int PRIORITY_LIGHT_CHUNKS_PER_TICK = 6;
    private static final int SYNC_CHUNKS_PER_TICK = 32;
    private static final TicketType<ChunkPos> TERRAIN_TICKET = TicketType.create(
        "quasar_terrain_edit", Comparator.comparingLong(ChunkPos::toLong)
    );
    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();

    private TerrainEditService() {
    }

    /** Resets the per-tick section-edit admission counter. */
    static void beginTick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        state(level).sectionEditsUsed = 0;
    }

    /** Reserves one bulk section edit for this level tick, or returns false at the soft cap. */
    static boolean tryAcquireSection(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelState state = state(level);
        int budget = EffectBudgets.sectionEditsPerLevelTick();
        if (state.sectionEditsUsed >= budget) {
            return false;
        }
        state.sectionEditsUsed++;
        return true;
    }

    /**
     * Reserves chunk columns for one bulk edit, all or nothing. The aggregate ticket budget keeps
     * concurrent edits from pinning an unbounded area; a rejected reservation waits for a retry
     * instead of acquiring a partial set that could stall the edit front.
     */
    public static boolean tryAcquireTickets(ServerLevel level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        Set<ChunkPos> unique = new LinkedHashSet<>(chunks);
        if (unique.isEmpty()) {
            return true;
        }
        LevelState state = state(level);
        if (state.heldTickets + unique.size() > QuasarConfig.terrainTicketBudget()) {
            return false;
        }

        List<ChunkPos> acquired = new ArrayList<>(unique.size());
        try {
            for (ChunkPos chunk : unique) {
                int count = state.tickets.getOrDefault(chunk, 0);
                if (count == 0) {
                    level.getChunkSource().addRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
                }
                state.tickets.put(chunk, count + 1);
                state.heldTickets++;
                acquired.add(chunk);
            }
            return true;
        } catch (RuntimeException exception) {
            for (ChunkPos chunk : acquired) {
                releaseOneTicket(level, state, chunk);
            }
            throw exception;
        }
    }

    /** Best-effort compatibility wrapper; callers that can wait should use the returning method. */
    public static void acquireTickets(ServerLevel level, Collection<ChunkPos> chunks) {
        if (!tryAcquireTickets(level, chunks)) {
            LOGGER.warn("Could not reserve terrain-edit chunk tickets within the level budget");
        }
    }

    /** Releases chunk tickets acquired by one bulk edit. */
    public static void releaseTickets(ServerLevel level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        LevelState state = state(level);
        for (ChunkPos chunk : new LinkedHashSet<>(chunks)) {
            releaseOneTicket(level, state, chunk);
        }
    }

    /**
     * Releases tickets after the chunk's queued relight and client resend finish. The region
     * ticket stays held until then so a closing editor cannot unload a chunk it just revealed.
     */
    public static void releaseTicketsWhenSettled(ServerLevel level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        LevelState state = state(level);
        for (ChunkPos chunk : chunks) {
            int count = state.tickets.getOrDefault(chunk, 0);
            if (count <= 0) {
                continue;
            }
            if (count <= 1) {
                // Keep the region ticket; the tick loop removes it once the queues settle.
                state.tickets.remove(chunk);
                state.pendingTicketRelease.merge(chunk, 1, Integer::sum);
            } else {
                state.tickets.put(chunk, count - 1);
                state.heldTickets--;
            }
        }
    }

    private static void releaseOneTicket(ServerLevel level, LevelState state, ChunkPos chunk) {
        int count = state.tickets.getOrDefault(chunk, 0);
        if (count <= 0) {
            return;
        }
        if (count == 1) {
            state.tickets.remove(chunk);
            try {
                level.getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            } catch (RuntimeException exception) {
                LOGGER.debug("Could not release a terrain-edit ticket for {}", chunk, exception);
            }
        } else {
            state.tickets.put(chunk, count - 1);
        }
        state.heldTickets = Math.max(0, state.heldTickets - 1);
    }

    /** Queues a chunk for heightmap and light recomputation. */
    public static void queueRelight(ServerLevel level, ChunkPos chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        state(level).relightQueue.add(chunk);
    }

    /** Queues a chunk ahead of ordinary relights so a progressive reveal is not delayed. */
    public static void queuePriorityRelight(ServerLevel level, ChunkPos chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        LevelState state = state(level);
        state.relightQueue.add(chunk);
        state.priorityRelightQueue.add(chunk);
    }

    /**
     * Queues one full-chunk client resend. When {@code afterRelight} is true the resend waits until
     * the chunk's light pass has completed, so clients never receive stale light.
     */
    public static void requestSync(ServerLevel level, ChunkPos chunk, boolean afterRelight) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        LevelState state = state(level);
        if (afterRelight && state.relightQueue.contains(chunk)) {
            state.syncAfterRelight.add(chunk);
        } else {
            state.syncQueue.add(chunk);
        }
    }

    /** Queues both a relight and a post-relight client resend for one chunk. */
    public static void scheduleRelightAndSync(ServerLevel level, ChunkPos chunk) {
        queueRelight(level, chunk);
        requestSync(level, chunk, true);
    }

    /** Advances the relight and client-sync queues for one level by one tick. */
    static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelState state = STATES.get(level);
        if (state == null) {
            return;
        }

        int priorityProcessed = 0;
        int priorityScanned = 0;
        Iterator<ChunkPos> priority = state.priorityRelightQueue.iterator();
        while (priority.hasNext()
            && priorityProcessed < PRIORITY_LIGHT_CHUNKS_PER_TICK
            && priorityScanned < 64) {
            priorityScanned++;
            ChunkPos chunk = priority.next();
            if (!relight(level, state, chunk)) {
                // Leave an unloaded chunk queued; keep draining the rest of the front.
                continue;
            }
            priority.remove();
            state.relightQueue.remove(chunk);
            priorityProcessed++;
        }

        int processed = 0;
        int scanned = 0;
        Iterator<ChunkPos> ordinary = state.relightQueue.iterator();
        while (ordinary.hasNext() && processed < LIGHT_CHUNKS_PER_TICK && scanned < 64) {
            scanned++;
            ChunkPos chunk = ordinary.next();
            if (state.priorityRelightQueue.contains(chunk)) {
                continue;
            }
            if (!relight(level, state, chunk)) {
                continue;
            }
            ordinary.remove();
            processed++;
        }

        int synced = 0;
        int syncScanned = 0;
        Iterator<ChunkPos> syncs = state.syncQueue.iterator();
        while (syncs.hasNext() && synced < SYNC_CHUNKS_PER_TICK && syncScanned < 64) {
            syncScanned++;
            ChunkPos chunk = syncs.next();
            if (!markForSync(level, chunk)) {
                // Keep an unloaded chunk queued; it may become available next tick.
                continue;
            }
            syncs.remove();
            synced++;
        }

        Iterator<Map.Entry<ChunkPos, Integer>> pendingRelease =
            state.pendingTicketRelease.entrySet().iterator();
        while (pendingRelease.hasNext()) {
            Map.Entry<ChunkPos, Integer> entry = pendingRelease.next();
            ChunkPos chunk = entry.getKey();
            if (state.relightQueue.contains(chunk)
                || state.priorityRelightQueue.contains(chunk)
                || state.syncAfterRelight.contains(chunk)
                || state.syncQueue.contains(chunk)) {
                continue;
            }
            pendingRelease.remove();
            int held = Math.max(1, entry.getValue());
            for (int index = 0; index < held; index++) {
                level.getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            }
            state.heldTickets = Math.max(0, state.heldTickets - held);
        }

        if (state.relightQueue.isEmpty()
            && state.priorityRelightQueue.isEmpty()
            && state.syncAfterRelight.isEmpty()
            && state.syncQueue.isEmpty()
            && state.failedRelights.isEmpty()
            && state.pendingTicketRelease.isEmpty()
            && state.tickets.isEmpty()
            && state.heldTickets == 0) {
            STATES.remove(level);
        }
    }

    /** Releases tickets and queues for a level that is unloading. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelState state = STATES.remove(level);
        if (state == null) {
            return;
        }
        for (ChunkPos chunk : state.tickets.keySet()) {
            releaseRegionTickets(level, chunk, 1);
        }
        for (ChunkPos chunk : state.pendingTicketRelease.keySet()) {
            releaseRegionTickets(level, chunk, 1);
        }
        state.heldTickets = 0;
    }

    private static void releaseRegionTickets(ServerLevel level, ChunkPos chunk, int count) {
        for (int index = 0; index < count; index++) {
            try {
                level.getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            } catch (RuntimeException exception) {
                LOGGER.debug("Could not release terrain-edit ticket for {}", chunk, exception);
            }
        }
    }

    /**
     * Rebuilds one chunk's heightmaps and light. Unloaded chunks stay queued so a slowly loading
     * chunk never stalls the rest of the destruction front.
     */
    private static boolean relight(ServerLevel level, LevelState state, ChunkPos chunk) {
        LevelChunk levelChunk = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (levelChunk == null) {
            return false;
        }
        try {
            Heightmap.primeHeightmaps(levelChunk, EnumSet.allOf(Heightmap.Types.class));
            levelChunk.initializeLightSources();
            level.getChunkSource().getLightEngine().propagateLightSources(chunk);
            levelChunk.setUnsaved(true);
        } catch (RuntimeException exception) {
            if (state.failedRelights.add(chunk)) {
                LOGGER.warn("Could not relight bulk-edited chunk {}; it stays queued", chunk, exception);
            }
            // Leave syncAfterRelight untouched so no client receives a chunk with stale light.
            return false;
        }
        state.failedRelights.remove(chunk);
        if (state.syncAfterRelight.remove(chunk)) {
            state.syncQueue.add(chunk);
        }
        return true;
    }

    private static boolean markForSync(ServerLevel level, ChunkPos chunk) {
        LevelChunk levelChunk = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (levelChunk == null) {
            return false;
        }
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
            player.connection.chunkSender.markChunkPendingToSend(levelChunk);
        }
        return true;
    }

    private static LevelState state(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new LevelState());
    }

    /** Test-facing count of queued relight and client-sync work. */
    static int pendingWorkCount(ServerLevel level) {
        LevelState state = STATES.get(level);
        if (state == null) {
            return 0;
        }
        return state.relightQueue.size()
            + state.priorityRelightQueue.size()
            + state.syncQueue.size()
            + state.syncAfterRelight.size();
    }

    private static final class LevelState {
        private int sectionEditsUsed;
        private int heldTickets;
        private final Set<ChunkPos> relightQueue = new LinkedHashSet<>();
        private final Set<ChunkPos> priorityRelightQueue = new LinkedHashSet<>();
        private final Set<ChunkPos> syncAfterRelight = new LinkedHashSet<>();
        private final Set<ChunkPos> syncQueue = new LinkedHashSet<>();
        private final Set<ChunkPos> failedRelights = new LinkedHashSet<>();
        private final Map<ChunkPos, Integer> pendingTicketRelease = new java.util.HashMap<>();
        private final Map<ChunkPos, Integer> tickets = new java.util.HashMap<>();
    }
}
