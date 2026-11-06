package mafisher.quasar.effect.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.QuasarConfig;
import mafisher.quasar.effect.block.BlockChange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine-managed journal of applied block changes for one gameplay effect. Entries are recorded
 * before the world write, rolled back in reverse order, and persisted with the level effect store.
 */
public final class BlockJournal {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final List<Entry> entries = new ArrayList<>();
    private final int maxEntriesOverride;
    private int cursor;
    private boolean rollbackPending;
    private boolean truncated;
    private boolean dirty;

    public BlockJournal() {
        this(-1);
    }

    BlockJournal(int maxEntries) {
        if (maxEntries == 0 || maxEntries < -1) {
            throw new IllegalArgumentException("Block journal capacity must be positive or -1");
        }
        this.maxEntriesOverride = maxEntries;
    }

    /** Records one change before it is applied; returns false when the journal is full. */
    public boolean record(ServerLevel level, BlockChange change, BlockState previousState) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(previousState, "previousState");
        if (this.rollbackPending) {
            return false;
        }
        int maxEntries = this.maxEntriesOverride > 0
            ? this.maxEntriesOverride
            : QuasarConfig.blockJournalMaxEntries();
        if (this.entries.size() >= maxEntries) {
            if (!this.truncated) {
                this.truncated = true;
                this.dirty = true;
                LOGGER.warn(
                    "Block journal reached {} entries; rollback is disabled for the affected effect",
                    maxEntries
                );
            }
            return false;
        }

        CompoundTag blockEntity = null;
        BlockEntity existing = level.getBlockEntity(change.position());
        if (existing != null) {
            try {
                blockEntity = existing.saveWithFullMetadata(level.registryAccess());
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not capture a block entity for the block journal", exception);
            }
        }

        this.entries.add(new Entry(
            change.position(),
            previousState,
            change.state(),
            blockEntity == null ? null : blockEntity.copy()
        ));
        if (!this.rollbackPending) {
            this.cursor = this.entries.size();
        }
        this.dirty = true;
        return true;
    }

    /** Discards the most recently recorded entry after its world write failed. */
    public void discardLast() {
        if (!this.entries.isEmpty()) {
            this.entries.remove(this.entries.size() - 1);
            this.cursor = this.entries.size();
            this.dirty = true;
        }
    }

    /** Starts reverse-order rollback; subsequent records are rejected. */
    public void beginRollback() {
        this.rollbackPending = true;
        this.cursor = this.entries.size();
        this.dirty = true;
    }

    public boolean isRollbackPending() {
        return this.rollbackPending;
    }

    public boolean isTruncated() {
        return this.truncated;
    }

    public int size() {
        return this.entries.size();
    }

    public boolean canRollback() {
        return !this.truncated && !this.entries.isEmpty();
    }

    public boolean consumeDirty() {
        boolean wasDirty = this.dirty;
        this.dirty = false;
        return wasDirty;
    }

    RollbackStep rollbackNext(ServerLevel level, int maxChanges) {
        Objects.requireNonNull(level, "level");
        if (maxChanges < 0) {
            throw new IllegalArgumentException("Rollback budget cannot be negative");
        }
        if (!this.rollbackPending) {
            this.beginRollback();
        }

        int processed = 0;
        int restored = 0;
        int conflicts = 0;
        while (processed < maxChanges && this.cursor > 0) {
            Entry entry = this.entries.get(this.cursor - 1);
            this.cursor--;
            processed++;

            try {
                if (!level.hasChunkAt(entry.position)) {
                    level.getChunkAt(entry.position);
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not load a chunk for block rollback at {}", entry.position, exception);
                continue;
            }

            BlockState currentState;
            try {
                currentState = level.getBlockState(entry.position);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not read a block for rollback at {}", entry.position, exception);
                continue;
            }
            if (!currentState.equals(entry.newState)) {
                conflicts++;
                continue;
            }

            try {
                if (level.setBlockAndUpdate(entry.position, entry.oldState)) {
                    restored++;
                    restoreBlockEntity(level, entry);
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not roll back the block at {}", entry.position, exception);
            }
        }

        this.dirty = this.dirty || processed > 0;
        boolean complete = this.cursor == 0;
        return new RollbackStep(processed, restored, conflicts, complete);
    }

    private static void restoreBlockEntity(ServerLevel level, Entry entry) {
        if (entry.blockEntity == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(entry.position);
        if (blockEntity == null) {
            return;
        }
        try {
            blockEntity.loadWithComponents(entry.blockEntity.copy(), level.registryAccess());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not restore a block entity at {}", entry.position, exception);
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry entry : this.entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", entry.position.asLong());
            entryTag.put("old", NbtUtils.writeBlockState(entry.oldState));
            entryTag.put("new", NbtUtils.writeBlockState(entry.newState));
            if (entry.blockEntity != null) {
                entryTag.put("block_entity", entry.blockEntity.copy());
            }
            list.add(entryTag);
        }
        tag.put("entries", list);
        tag.putInt("cursor", this.cursor);
        tag.putBoolean("rollback_pending", this.rollbackPending);
        tag.putBoolean("truncated", this.truncated);
        return tag;
    }

    static BlockJournal load(CompoundTag tag) {
        BlockJournal journal = new BlockJournal();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entryTag = list.getCompound(index);
            try {
                BlockPos position = BlockPos.of(entryTag.getLong("pos"));
                BlockState oldState = NbtUtils.readBlockState(
                    BuiltInRegistries.BLOCK.asLookup(),
                    entryTag.getCompound("old")
                );
                BlockState newState = NbtUtils.readBlockState(
                    BuiltInRegistries.BLOCK.asLookup(),
                    entryTag.getCompound("new")
                );
                CompoundTag blockEntity = entryTag.contains("block_entity")
                    ? entryTag.getCompound("block_entity").copy()
                    : null;
                journal.entries.add(new Entry(position, oldState, newState, blockEntity));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping an unreadable block journal entry; rollback will be disabled", exception);
                journal.truncated = true;
            }
        }

        journal.rollbackPending = tag.getBoolean("rollback_pending");
        journal.truncated = tag.getBoolean("truncated");
        journal.cursor = tag.contains("cursor")
            ? Math.min(journal.entries.size(), Math.max(0, tag.getInt("cursor")))
            : journal.entries.size();
        if (!journal.rollbackPending) {
            journal.cursor = journal.entries.size();
        }

        int maxEntries = QuasarConfig.blockJournalMaxEntries();
        if (journal.entries.size() > maxEntries) {
            journal.entries.clear();
            journal.cursor = 0;
            journal.truncated = true;
        }
        return journal;
    }

    record RollbackStep(int processed, int restored, int conflicts, boolean complete) {
    }

    private record Entry(
        BlockPos position,
        BlockState oldState,
        BlockState newState,
        CompoundTag blockEntity
    ) {
        private Entry {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(oldState, "oldState");
            Objects.requireNonNull(newState, "newState");
            blockEntity = blockEntity == null ? null : blockEntity.copy();
        }
    }
}
