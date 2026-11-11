package mafisher.quasar.effect.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Budgeted, resumable rollback of block journals. Tasks survive in memory across ticks; journal
 * progress is persisted so a restart can continue from the saved cursor.
 */
public final class BlockRollbackService {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, Map<UUID, BlockJournal>> TASKS = new WeakHashMap<>();

    private BlockRollbackService() {
    }

    /** Schedules one journal for rollback; returns false when it was completed or skipped. */
    public static boolean schedule(ServerLevel level, UUID effectId, BlockJournal journal) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(journal, "journal");
        if (journal.isTruncated()) {
            LOGGER.warn("Skipping block rollback for {}: the journal is invalid or truncated", effectId);
            EffectPersistenceService.recordStop(level, effectId);
            return false;
        }
        if (journal.isEmpty()) {
            EffectPersistenceService.recordStop(level, effectId);
            return false;
        }

        if (!journal.isRollbackPending()) {
            journal.beginRollback();
        }
        EffectPersistenceService.recordJournal(level, effectId, journal);
        tasks(level).put(effectId, journal);
        return true;
    }

    /** Applies rollback work up to the shared block budget and returns the remaining budget. */
    public static int tick(ServerLevel level, int blockChangeBudget) {
        Objects.requireNonNull(level, "level");
        if (blockChangeBudget < 0) {
            throw new IllegalArgumentException("Rollback budget cannot be negative");
        }
        Map<UUID, BlockJournal> tasks = TASKS.get(level);
        if (tasks == null || tasks.isEmpty()) {
            TASKS.remove(level);
            return blockChangeBudget;
        }

        int remaining = blockChangeBudget;
        for (UUID effectId : new ArrayList<>(tasks.keySet())) {
            if (remaining <= 0) {
                break;
            }
            BlockJournal journal = tasks.get(effectId);
            if (journal != null) {
                remaining = rollbackTask(level, effectId, journal, tasks, remaining);
            }
        }
        if (tasks.isEmpty()) {
            TASKS.remove(level);
        }
        return remaining;
    }

    private static int rollbackTask(
        ServerLevel level,
        UUID effectId,
        BlockJournal journal,
        Map<UUID, BlockJournal> tasks,
        int remaining
    ) {
        BlockJournal.RollbackStep step;
        try {
            step = journal.rollbackNext(level, remaining);
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Block rollback for {} failed unexpectedly; the journal stays saved for a later resume",
                effectId,
                exception
            );
            tasks.remove(effectId);
            return remaining;
        }
        if (step.advanced() > 0 || step.complete()) {
            EffectPersistenceService.recordJournal(level, effectId, journal);
        }
        if (step.complete()) {
            EffectPersistenceService.recordStop(level, effectId);
            tasks.remove(effectId);
            LOGGER.info(
                "Completed block rollback for {}: restored {}, conflicts {}",
                effectId,
                step.restored(),
                step.conflicts()
            );
        } else if (step.failed() > 0) {
            LOGGER.debug("Block rollback for {} did not advance; it will retry", effectId);
        }
        return remaining - step.processed();
    }

    /** Drops in-memory tasks for a level; journals remain in save data for the next load. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        TASKS.remove(level);
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, BlockJournal> tasks = TASKS.get(level);
        return tasks == null ? 0 : tasks.size();
    }

    static void clearForTesting() {
        TASKS.clear();
    }

    private static Map<UUID, BlockJournal> tasks(ServerLevel level) {
        return TASKS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    }
}
