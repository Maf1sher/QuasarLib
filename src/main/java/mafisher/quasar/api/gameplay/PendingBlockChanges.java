package mafisher.quasar.api.gameplay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-session FIFO of block changes that are waiting for the server tick's application budget.
 * Overflow stays queued across ticks instead of being applied immediately.
 */
final class PendingBlockChanges {
    /** Provisional safety cap; it bounds memory until configurable quality budgets arrive. */
    static final int DEFAULT_MAX_PENDING = 16_384;

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingBlockChanges.class);

    private final Deque<QueuedChange> changes = new ArrayDeque<>();
    private final int maxPending;
    private boolean deferredLogged;

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
        return this.enqueue(change, null);
    }

    /**
     * Queues one change with an optional callback invoked only after the engine applies it. The
     * callback receives the old block state, so effects can emit debris or secondary effects
     * without showing work that protection vetoed.
     */
    boolean enqueue(BlockChange change, Consumer<EffectBlockSample> applied) {
        return this.enqueue(change, applied, BlockJournalMode.PER_BLOCK);
    }

    /** Queues one change with an explicit rollback journal mode. */
    boolean enqueue(
        BlockChange change,
        Consumer<EffectBlockSample> applied,
        BlockJournalMode journalMode
    ) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(journalMode, "journalMode");
        if (this.changes.size() >= this.maxPending) {
            return false;
        }
        this.changes.addLast(new QueuedChange(change, applied, journalMode));
        return true;
    }

    int size() {
        return this.changes.size();
    }

    void clear() {
        this.changes.clear();
    }

    /**
     * Applies at most {@code maxChanges} queued changes without protection callbacks or a journal.
     */
    ApplyResult apply(ServerLevel level, int maxChanges) {
        return this.apply(level, maxChanges, (change, currentState) -> true, null);
    }

    /**
     * Applies at most {@code maxChanges} queued changes without a journal.
     */
    ApplyResult apply(ServerLevel level, int maxChanges, BiPredicate<BlockChange, BlockState> gate) {
        return this.apply(level, maxChanges, gate, null);
    }

    /**
     * Applies at most {@code maxChanges} queued changes in insertion order. Out-of-bounds positions
     * are skipped; unloaded chunks are requeued without consuming the budget; no-op changes do not
     * invoke the protection gate; individual block-update failures are logged and do not stop the
     * queue. The result reports processed entries, applied changes, vetoes, and deferred entries.
     */
    ApplyResult apply(
        ServerLevel level,
        int maxChanges,
        BiPredicate<BlockChange, BlockState> gate,
        BlockJournal journal
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(gate, "gate");
        if (maxChanges < 0) {
            throw new IllegalArgumentException("Block change application budget cannot be negative");
        }

        int processed = 0;
        int applied = 0;
        int vetoed = 0;
        int deferred = 0;
        int attempts = 0;
        int maxAttempts = this.changes.size();
        while (processed < maxChanges && !this.changes.isEmpty() && attempts < maxAttempts) {
            QueuedChange queued = this.changes.removeFirst();
            BlockChange change = queued.change();
            attempts++;
            if (!level.isInWorldBounds(change.position())) {
                processed++;
                LOGGER.debug("Skipped out-of-bounds block change at {}", change.position());
                continue;
            }
            if (!level.hasChunkAt(change.position())) {
                this.changes.addLast(queued);
                deferred++;
                if (!this.deferredLogged) {
                    this.deferredLogged = true;
                    LOGGER.warn(
                        "{} block changes are waiting for unloaded chunks; they stay queued",
                        this.changes.size()
                    );
                }
                continue;
            }

            processed++;
            BlockState currentState;
            try {
                currentState = level.getBlockState(change.position());
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not read the block state at {}", change.position(), exception);
                continue;
            }
            if (currentState.equals(change.state())) {
                LOGGER.debug("Skipped unchanged block at {}", change.position());
                continue;
            }

            try {
                if (!gate.test(change, currentState)) {
                    vetoed++;
                    continue;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Block protection failed at {}; vetoing the change", change.position(), exception);
                vetoed++;
                continue;
            }

            boolean recorded = journal == null;
            boolean recordedPerBlock = false;
            if (journal != null) {
                if (queued.journalMode() == BlockJournalMode.PER_BLOCK) {
                    recordedPerBlock = journal.record(level, change, currentState);
                    recorded = recordedPerBlock;
                } else {
                    recorded = this.recordSection(level, journal, change.position());
                }
                if (!recorded && !journal.isTruncated()) {
                    vetoed++;
                    continue;
                }
            }
            try {
                boolean changed = change.updateNeighbors()
                    ? level.setBlockAndUpdate(change.position(), change.state())
                    : level.setBlock(
                        change.position(),
                        change.state(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
                    );
                if (changed) {
                    applied++;
                    this.notifyApplied(queued, currentState);
                } else if (recordedPerBlock) {
                    journal.discardLast();
                }
            } catch (RuntimeException exception) {
                if (recordedPerBlock) {
                    journal.discardLast();
                }
                LOGGER.warn("Failed to apply block change at {}", change.position(), exception);
            }
        }
        if (deferred == 0) {
            this.deferredLogged = false;
        }
        return new ApplyResult(processed, applied, vetoed, deferred);
    }

    private boolean recordSection(ServerLevel level, BlockJournal journal, BlockPos position) {
        int sectionX = SectionPos.blockToSectionCoord(position.getX());
        int sectionY = SectionPos.blockToSectionCoord(position.getY());
        int sectionZ = SectionPos.blockToSectionCoord(position.getZ());
        LevelChunk chunk = level.getChunkSource().getChunkNow(sectionX, sectionZ);
        if (chunk == null) {
            return false;
        }
        int sectionIndex = level.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection section = chunk.getSection(sectionIndex);
        if (section == null) {
            return false;
        }
        return journal.recordSection(level, chunk, sectionY, section);
    }

    private void notifyApplied(QueuedChange queued, BlockState oldState) {
        Consumer<EffectBlockSample> callback = queued.applied();
        if (callback == null) {
            return;
        }
        try {
            callback.accept(new EffectBlockSample(queued.change().position(), oldState));
        } catch (RuntimeException exception) {
            LOGGER.warn("Block-change callback failed at {}", queued.change().position(), exception);
        }
    }

    private record QueuedChange(
        BlockChange change,
        Consumer<EffectBlockSample> applied,
        BlockJournalMode journalMode
    ) {
        private QueuedChange {
            Objects.requireNonNull(change, "change");
            Objects.requireNonNull(journalMode, "journalMode");
        }
    }

    record ApplyResult(int processed, int applied, int vetoed, int deferred) {
        ApplyResult {
            if (processed < 0 || applied < 0 || vetoed < 0 || deferred < 0
                || applied + vetoed > processed) {
                throw new IllegalArgumentException("Invalid block change application result");
            }
        }
    }
}
