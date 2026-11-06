package mafisher.quasar.effect.gameplay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import mafisher.quasar.effect.block.BlockChange;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-session FIFO of block changes that are waiting for the server tick's application budget.
 * Overflow stays queued across ticks instead of being applied immediately.
 */
final class PendingBlockChanges {
    /** Provisional safety cap; it bounds memory until configurable quality budgets arrive. */
    static final int DEFAULT_MAX_PENDING = 16_384;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final Deque<BlockChange> changes = new ArrayDeque<>();
    private final int maxPending;

    PendingBlockChanges() {
        this(DEFAULT_MAX_PENDING);
    }

    PendingBlockChanges(int maxPending) {
        if (maxPending < 0) {
            throw new IllegalArgumentException("Pending block change capacity cannot be negative");
        }
        this.maxPending = maxPending;
    }

    /** Queues one change, or returns {@code false} when the safety cap is reached. */
    boolean enqueue(BlockChange change) {
        Objects.requireNonNull(change, "change");
        if (this.changes.size() >= this.maxPending) {
            return false;
        }
        this.changes.addLast(change);
        return true;
    }

    int size() {
        return this.changes.size();
    }

    void clear() {
        this.changes.clear();
    }

    /**
     * Applies at most {@code maxChanges} queued changes in insertion order. Positions outside the
     * level or in unloaded chunks are skipped; individual block-update failures are logged and do
     * not stop the queue. The result reports both processed entries (which consume the budget) and
     * changes actually handed to the level.
     */
    ApplyResult apply(ServerLevel level, int maxChanges) {
        Objects.requireNonNull(level, "level");
        if (maxChanges < 0) {
            throw new IllegalArgumentException("Block change application budget cannot be negative");
        }

        int processed = 0;
        int applied = 0;
        while (processed < maxChanges && !this.changes.isEmpty()) {
            BlockChange change = this.changes.removeFirst();
            processed++;
            if (!level.isInWorldBounds(change.position())) {
                LOGGER.debug("Skipped out-of-bounds block change at {}", change.position());
                continue;
            }
            if (!level.hasChunkAt(change.position())) {
                LOGGER.debug("Skipped block change in an unloaded chunk at {}", change.position());
                continue;
            }
            try {
                if (level.setBlockAndUpdate(change.position(), change.state())) {
                    applied++;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to apply block change at {}", change.position(), exception);
            }
        }
        return new ApplyResult(processed, applied);
    }

    record ApplyResult(int processed, int applied) {
        ApplyResult {
            if (processed < 0 || applied < 0 || applied > processed) {
                throw new IllegalArgumentException("Invalid block change application result");
            }
        }
    }
}
