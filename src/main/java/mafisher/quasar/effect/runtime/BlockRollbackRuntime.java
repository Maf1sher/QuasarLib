package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level budgeted, resumable rollback of block journals. Tasks survive in memory across ticks;
 * journal progress is persisted so a restart can continue from the saved cursor.
 */
@ApiStatus.Internal
public final class BlockRollbackRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockRollbackRuntime.class);

    private final ServerLevel level;
    private final EffectStore store;
    private final Map<UUID, BlockJournal> tasks = new LinkedHashMap<>();

    public BlockRollbackRuntime(ServerLevel level, EffectStore store) {
        this.level = Objects.requireNonNull(level, "level");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Schedules one journal for rollback; returns false when it was completed or skipped. */
    public boolean schedule(UUID effectId, BlockJournal journal) {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(journal, "journal");
        if (journal.isTruncated()) {
            LOGGER.warn("Skipping block rollback for {}: the journal is invalid or truncated", effectId);
            this.store.recordStop(effectId);
            return false;
        }
        if (journal.isEmpty()) {
            this.store.recordStop(effectId);
            return false;
        }

        if (!journal.isRollbackPending()) {
            journal.beginRollback();
        }
        this.store.recordJournal(effectId, journal);
        this.tasks.put(effectId, journal);
        return true;
    }

    /** Applies rollback work up to the shared block budget and returns the remaining budget. */
    public int tick(int blockChangeBudget) {
        if (blockChangeBudget < 0) {
            throw new IllegalArgumentException("Rollback budget cannot be negative");
        }
        if (this.tasks.isEmpty()) {
            return blockChangeBudget;
        }

        int remaining = blockChangeBudget;
        for (UUID effectId : new ArrayList<>(this.tasks.keySet())) {
            if (remaining <= 0) {
                break;
            }
            BlockJournal journal = this.tasks.get(effectId);
            if (journal != null) {
                remaining = rollbackTask(effectId, journal, remaining);
            }
        }
        return remaining;
    }

    private int rollbackTask(UUID effectId, BlockJournal journal, int remaining) {
        BlockJournal.RollbackStep step;
        try {
            step = journal.rollbackNext(this.level, remaining);
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Block rollback for {} failed unexpectedly; the journal stays saved for a later resume",
                effectId,
                exception
            );
            journal.releaseLoadTickets(this.level);
            this.tasks.remove(effectId);
            return remaining;
        }
        if (step.advanced() > 0 || step.complete()) {
            this.store.recordJournal(effectId, journal);
        }
        if (step.complete()) {
            this.store.recordStop(effectId);
            this.tasks.remove(effectId);
            LOGGER.info(
                "Completed block rollback for {}: restored {}, conflicts {}",
                effectId,
                step.restored(),
                step.conflicts()
            );
        } else if (step.waiting() > 0) {
            LOGGER.debug("Block rollback for {} is waiting for a chunk load; it will retry", effectId);
        } else if (step.failed() > 0) {
            LOGGER.debug("Block rollback for {} did not advance; it will retry", effectId);
        }
        return remaining - step.processed();
    }

    /** Drops in-memory tasks for a level; journals remain in save data for the next load. */
    public void clear() {
        for (BlockJournal journal : this.tasks.values()) {
            journal.releaseLoadTickets(this.level);
        }
        this.tasks.clear();
    }

    public int activeCount() {
        return this.tasks.size();
    }
}
