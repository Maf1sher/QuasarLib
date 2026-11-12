package mafisher.quasar.effect.gameplay;

import java.util.Collection;
import java.util.Objects;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread facade for the per-level terrain edit runtime. Scheduling, tickets, relight, and
 * client resend state live in {@link LevelEffectRuntime#terrain()}.
 */
@ApiStatus.Internal
public final class TerrainEditService {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private TerrainEditService() {
    }

    /** Resets the per-tick section-edit admission counter. */
    public static void beginTick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectRuntimeRegistry.get(level).terrain().beginTick();
    }

    /** Reserves one bulk section edit for this level tick, or returns false at the soft cap. */
    public static boolean tryAcquireSection(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return EffectRuntimeRegistry.get(level).terrain().tryAcquireSection();
    }

    /** Reserves chunk columns for one bulk edit, all or nothing. */
    public static boolean tryAcquireTickets(ServerLevel level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        return EffectRuntimeRegistry.get(level).terrain().tryAcquireTickets(chunks);
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
        EffectRuntimeRegistry.get(level).terrain().releaseTickets(chunks);
    }

    /** Releases tickets once the chunk's queued relight and client resend finish. */
    public static void releaseTicketsWhenSettled(ServerLevel level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        EffectRuntimeRegistry.get(level).terrain().releaseTicketsWhenSettled(chunks);
    }

    /** Queues a chunk for heightmap and light recomputation. */
    public static void queueRelight(ServerLevel level, ChunkPos chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        EffectRuntimeRegistry.get(level).terrain().queueRelight(chunk);
    }

    /** Queues a chunk ahead of ordinary relights so a progressive reveal is not delayed. */
    public static void queuePriorityRelight(ServerLevel level, ChunkPos chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        EffectRuntimeRegistry.get(level).terrain().queuePriorityRelight(chunk);
    }

    /** Queues one full-chunk client resend, optionally after its relight completes. */
    public static void requestSync(ServerLevel level, ChunkPos chunk, boolean afterRelight) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        EffectRuntimeRegistry.get(level).terrain().requestSync(chunk, afterRelight);
    }

    /** Queues both a relight and a post-relight client resend for one chunk. */
    public static void scheduleRelightAndSync(ServerLevel level, ChunkPos chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        EffectRuntimeRegistry.get(level).terrain().scheduleRelightAndSync(chunk);
    }

    /** Advances the relight and client-sync queues for one level by one tick. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.terrain().tick();
        }
    }

    /** Releases tickets and queues for a level that is unloading. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.terrain().clear();
        }
    }
}
