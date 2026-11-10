package mafisher.quasar.effect.gameplay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
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

    /** Loads the chunk columns touched by a bulk edit for the duration of that edit. */
    public static void acquireTickets(ServerLevel level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        LevelState state = state(level);
        for (ChunkPos chunk : chunks) {
            int count = state.tickets.getOrDefault(chunk, 0);
            if (count == 0) {
                level.getChunkSource().addRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            }
            state.tickets.put(chunk, count + 1);
        }
    }

    /** Releases chunk tickets acquired by one bulk edit. */
    public static void releaseTickets(ServerLevel level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        LevelState state = state(level);
        for (ChunkPos chunk : chunks) {
            int count = state.tickets.getOrDefault(chunk, 0);
            if (count <= 1) {
                state.tickets.remove(chunk);
                level.getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            } else {
                state.tickets.put(chunk, count - 1);
            }
        }
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

        if (!state.syncAfterRelight.isEmpty()) {
            for (ChunkPos chunk : new ArrayList<>(state.syncAfterRelight)) {
                if (!state.relightQueue.contains(chunk)) {
                    state.syncAfterRelight.remove(chunk);
                    state.syncQueue.add(chunk);
                }
            }
        }

        int processed = 0;
        List<ChunkPos> pending = new ArrayList<>(
            state.priorityRelightQueue.size() + state.relightQueue.size()
        );
        pending.addAll(state.priorityRelightQueue);
        for (ChunkPos chunk : state.relightQueue) {
            if (!state.priorityRelightQueue.contains(chunk)) {
                pending.add(chunk);
            }
        }
        for (ChunkPos chunk : pending) {
            if (processed >= LIGHT_CHUNKS_PER_TICK) {
                break;
            }
            LevelChunk levelChunk = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
            if (levelChunk == null) {
                break;
            }
            try {
                Heightmap.primeHeightmaps(levelChunk, EnumSet.allOf(Heightmap.Types.class));
                levelChunk.initializeLightSources();
                level.getChunkSource().getLightEngine().propagateLightSources(chunk);
                levelChunk.setUnsaved(true);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not relight bulk-edited chunk {}", chunk, exception);
            }
            state.relightQueue.remove(chunk);
            state.priorityRelightQueue.remove(chunk);
            processed++;
            if (state.syncAfterRelight.remove(chunk)) {
                state.syncQueue.add(chunk);
            }
        }

        int synced = 0;
        while (synced < SYNC_CHUNKS_PER_TICK && !state.syncQueue.isEmpty()) {
            ChunkPos chunk = state.syncQueue.iterator().next();
            state.syncQueue.remove(chunk);
            markForSync(level, chunk);
            synced++;
        }

        if (state.relightQueue.isEmpty()
            && state.syncAfterRelight.isEmpty()
            && state.syncQueue.isEmpty()
            && state.tickets.isEmpty()) {
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
            try {
                level.getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            } catch (RuntimeException exception) {
                LOGGER.debug("Could not release terrain-edit ticket for {}", chunk, exception);
            }
        }
    }

    private static void markForSync(ServerLevel level, ChunkPos chunk) {
        LevelChunk levelChunk = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (levelChunk == null) {
            return;
        }
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
            player.connection.chunkSender.markChunkPendingToSend(levelChunk);
        }
    }

    private static LevelState state(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new LevelState());
    }

    private static final class LevelState {
        private int sectionEditsUsed;
        private final Set<ChunkPos> relightQueue = new LinkedHashSet<>();
        private final Set<ChunkPos> priorityRelightQueue = new LinkedHashSet<>();
        private final Set<ChunkPos> syncAfterRelight = new LinkedHashSet<>();
        private final Set<ChunkPos> syncQueue = new LinkedHashSet<>();
        private final Map<ChunkPos, Integer> tickets = new java.util.HashMap<>();
    }
}
