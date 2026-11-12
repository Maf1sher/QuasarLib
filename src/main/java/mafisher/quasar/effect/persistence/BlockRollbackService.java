package mafisher.quasar.effect.persistence;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Facade for the per-level rollback runtime. Rollback tasks live in
 * {@link LevelEffectRuntime#rollbacks()}.
 */
@ApiStatus.Internal
public final class BlockRollbackService {
    private BlockRollbackService() {
    }

    /** Schedules one journal for rollback; returns false when it was completed or skipped. */
    public static boolean schedule(ServerLevel level, UUID effectId, BlockJournal journal) {
        Objects.requireNonNull(level, "level");
        return EffectRuntimeRegistry.get(level).rollbacks().schedule(effectId, journal);
    }

    /** Applies rollback work up to the shared block budget and returns the remaining budget. */
    public static int tick(ServerLevel level, int blockChangeBudget) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? blockChangeBudget : runtime.rollbacks().tick(blockChangeBudget);
    }

    /** Drops in-memory tasks for a level; journals remain in save data for the next load. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.rollbacks().clear();
        }
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? 0 : runtime.rollbacks().activeCount();
    }
}
