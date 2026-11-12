package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.QuasarConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level scheduling for bulk terrain edits: section-edit admission, deferred chunk relighting,
 * and one batched client resend per changed chunk. All work runs on the server thread.
 */
@ApiStatus.Internal
public final class TerrainEditRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final int LIGHT_CHUNKS_PER_TICK = 2;
    /** Priority relights are the visible destruction front; let them keep up with section edits. */
    private static final int PRIORITY_LIGHT_CHUNKS_PER_TICK = 6;
    private static final int SYNC_CHUNKS_PER_TICK = 32;
    private static final TicketType<ChunkPos> TERRAIN_TICKET = TicketType.create(
        "quasar_terrain_edit", Comparator.comparingLong(ChunkPos::toLong)
    );

    private final ServerLevel level;
    private final EffectBudgetSource budgets;
    private int sectionEditsUsed;
    private int heldTickets;
    private final Set<ChunkPos> relightQueue = new LinkedHashSet<>();
    private final Set<ChunkPos> priorityRelightQueue = new LinkedHashSet<>();
    private final Set<ChunkPos> syncAfterRelight = new LinkedHashSet<>();
    private final Set<ChunkPos> syncQueue = new LinkedHashSet<>();
    private final Set<ChunkPos> failedRelights = new LinkedHashSet<>();
    private final Map<ChunkPos, Integer> pendingTicketRelease = new HashMap<>();
    private final Map<ChunkPos, Integer> tickets = new HashMap<>();

    public TerrainEditRuntime(ServerLevel level, EffectBudgetSource budgets) {
        this.level = Objects.requireNonNull(level, "level");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
    }

    /** Resets the per-tick section-edit admission counter. */
    public void beginTick() {
        this.sectionEditsUsed = 0;
    }

    /** Reserves one bulk section edit for this level tick, or returns false at the soft cap. */
    public boolean tryAcquireSection() {
        int budget = this.budgets.sectionEditsPerLevelTick();
        if (this.sectionEditsUsed >= budget) {
            return false;
        }
        this.sectionEditsUsed++;
        return true;
    }

    /**
     * Reserves chunk columns for one bulk edit, all or nothing. The aggregate ticket budget keeps
     * concurrent edits from pinning an unbounded area; a rejected reservation waits for a retry
     * instead of acquiring a partial set that could stall the edit front.
     */
    public boolean tryAcquireTickets(Collection<ChunkPos> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        Set<ChunkPos> unique = new LinkedHashSet<>(chunks);
        if (unique.isEmpty()) {
            return true;
        }
        if (this.heldTickets + unique.size() > QuasarConfig.terrainTicketBudget()) {
            return false;
        }

        List<ChunkPos> acquired = new ArrayList<>(unique.size());
        try {
            for (ChunkPos chunk : unique) {
                int count = this.tickets.getOrDefault(chunk, 0);
                if (count == 0) {
                    this.level.getChunkSource().addRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
                }
                this.tickets.put(chunk, count + 1);
                this.heldTickets++;
                acquired.add(chunk);
            }
            return true;
        } catch (RuntimeException exception) {
            for (ChunkPos chunk : acquired) {
                releaseOneTicket(chunk);
            }
            throw exception;
        }
    }

    /** Releases chunk tickets acquired by one bulk edit. */
    public void releaseTickets(Collection<ChunkPos> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        for (ChunkPos chunk : new LinkedHashSet<>(chunks)) {
            releaseOneTicket(chunk);
        }
    }

    /**
     * Releases tickets after the chunk's queued relight and client resend finish. The region
     * ticket stays held until then so a closing editor cannot unload a chunk it just revealed.
     */
    public void releaseTicketsWhenSettled(Collection<ChunkPos> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        for (ChunkPos chunk : chunks) {
            int count = this.tickets.getOrDefault(chunk, 0);
            if (count <= 0) {
                continue;
            }
            if (count <= 1) {
                // Keep the region ticket; the tick loop removes it once the queues settle.
                this.tickets.remove(chunk);
                this.pendingTicketRelease.merge(chunk, 1, Integer::sum);
            } else {
                this.tickets.put(chunk, count - 1);
                this.heldTickets--;
            }
        }
    }

    private void releaseOneTicket(ChunkPos chunk) {
        int count = this.tickets.getOrDefault(chunk, 0);
        if (count <= 0) {
            return;
        }
        if (count == 1) {
            this.tickets.remove(chunk);
            try {
                this.level.getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            } catch (RuntimeException exception) {
                LOGGER.debug("Could not release a terrain-edit ticket for {}", chunk, exception);
            }
        } else {
            this.tickets.put(chunk, count - 1);
        }
        this.heldTickets = Math.max(0, this.heldTickets - 1);
    }

    /** Queues a chunk for heightmap and light recomputation. */
    public void queueRelight(ChunkPos chunk) {
        Objects.requireNonNull(chunk, "chunk");
        this.relightQueue.add(chunk);
    }

    /** Queues a chunk ahead of ordinary relights so a progressive reveal is not delayed. */
    public void queuePriorityRelight(ChunkPos chunk) {
        Objects.requireNonNull(chunk, "chunk");
        this.relightQueue.add(chunk);
        this.priorityRelightQueue.add(chunk);
    }

    /**
     * Queues one full-chunk client resend. When {@code afterRelight} is true the resend waits until
     * the chunk's light pass has completed, so clients never receive stale light.
     */
    public void requestSync(ChunkPos chunk, boolean afterRelight) {
        Objects.requireNonNull(chunk, "chunk");
        if (afterRelight && this.relightQueue.contains(chunk)) {
            this.syncAfterRelight.add(chunk);
        } else {
            this.syncQueue.add(chunk);
        }
    }

    /** Queues both a relight and a post-relight client resend for one chunk. */
    public void scheduleRelightAndSync(ChunkPos chunk) {
        queueRelight(chunk);
        requestSync(chunk, true);
    }

    /** Advances the relight and client-sync queues for one level by one tick. */
    public void tick() {
        int priorityProcessed = 0;
        int priorityScanned = 0;
        Iterator<ChunkPos> priority = this.priorityRelightQueue.iterator();
        while (priority.hasNext()
            && priorityProcessed < PRIORITY_LIGHT_CHUNKS_PER_TICK
            && priorityScanned < 64) {
            priorityScanned++;
            ChunkPos chunk = priority.next();
            if (!relight(chunk)) {
                // Leave an unloaded chunk queued; keep draining the rest of the front.
                continue;
            }
            priority.remove();
            this.relightQueue.remove(chunk);
            priorityProcessed++;
        }

        int processed = 0;
        int scanned = 0;
        Iterator<ChunkPos> ordinary = this.relightQueue.iterator();
        while (ordinary.hasNext() && processed < LIGHT_CHUNKS_PER_TICK && scanned < 64) {
            scanned++;
            ChunkPos chunk = ordinary.next();
            if (this.priorityRelightQueue.contains(chunk)) {
                continue;
            }
            if (!relight(chunk)) {
                continue;
            }
            ordinary.remove();
            processed++;
        }

        int synced = 0;
        int syncScanned = 0;
        Iterator<ChunkPos> syncs = this.syncQueue.iterator();
        while (syncs.hasNext() && synced < SYNC_CHUNKS_PER_TICK && syncScanned < 64) {
            syncScanned++;
            ChunkPos chunk = syncs.next();
            if (!markForSync(chunk)) {
                // Keep an unloaded chunk queued; it may become available next tick.
                continue;
            }
            syncs.remove();
            synced++;
        }

        Iterator<Map.Entry<ChunkPos, Integer>> pendingRelease =
            this.pendingTicketRelease.entrySet().iterator();
        while (pendingRelease.hasNext()) {
            Map.Entry<ChunkPos, Integer> entry = pendingRelease.next();
            ChunkPos chunk = entry.getKey();
            if (this.relightQueue.contains(chunk)
                || this.priorityRelightQueue.contains(chunk)
                || this.syncAfterRelight.contains(chunk)
                || this.syncQueue.contains(chunk)) {
                continue;
            }
            pendingRelease.remove();
            int held = Math.max(1, entry.getValue());
            for (int index = 0; index < held; index++) {
                this.level.getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            }
            this.heldTickets = Math.max(0, this.heldTickets - held);
        }
    }

    /** Releases tickets and queues for a level that is unloading. */
    public void clear() {
        for (ChunkPos chunk : this.tickets.keySet()) {
            releaseRegionTickets(chunk, 1);
        }
        for (ChunkPos chunk : this.pendingTicketRelease.keySet()) {
            releaseRegionTickets(chunk, 1);
        }
        this.heldTickets = 0;
        this.relightQueue.clear();
        this.priorityRelightQueue.clear();
        this.syncAfterRelight.clear();
        this.syncQueue.clear();
        this.failedRelights.clear();
        this.pendingTicketRelease.clear();
        this.tickets.clear();
    }

    private void releaseRegionTickets(ChunkPos chunk, int count) {
        for (int index = 0; index < count; index++) {
            try {
                this.level.getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 0, chunk);
            } catch (RuntimeException exception) {
                LOGGER.debug("Could not release terrain-edit ticket for {}", chunk, exception);
            }
        }
    }

    /**
     * Rebuilds one chunk's heightmaps and light. Unloaded chunks stay queued so a slowly loading
     * chunk never stalls the rest of the destruction front.
     */
    private boolean relight(ChunkPos chunk) {
        LevelChunk levelChunk = this.level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (levelChunk == null) {
            return false;
        }
        try {
            Heightmap.primeHeightmaps(levelChunk, EnumSet.allOf(Heightmap.Types.class));
            levelChunk.initializeLightSources();
            this.level.getChunkSource().getLightEngine().propagateLightSources(chunk);
            levelChunk.setUnsaved(true);
        } catch (RuntimeException exception) {
            if (this.failedRelights.add(chunk)) {
                LOGGER.warn("Could not relight bulk-edited chunk {}; it stays queued", chunk, exception);
            }
            // Leave syncAfterRelight untouched so no client receives a chunk with stale light.
            return false;
        }
        this.failedRelights.remove(chunk);
        if (this.syncAfterRelight.remove(chunk)) {
            this.syncQueue.add(chunk);
        }
        return true;
    }

    private boolean markForSync(ChunkPos chunk) {
        LevelChunk levelChunk = this.level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (levelChunk == null) {
            return false;
        }
        for (ServerPlayer player : this.level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
            player.connection.chunkSender.markChunkPendingToSend(levelChunk);
        }
        return true;
    }

    /** Test-facing count of queued relight and client-sync work. */
    public int pendingWorkCount() {
        return this.relightQueue.size()
            + this.priorityRelightQueue.size()
            + this.syncQueue.size()
            + this.syncAfterRelight.size();
    }
}
