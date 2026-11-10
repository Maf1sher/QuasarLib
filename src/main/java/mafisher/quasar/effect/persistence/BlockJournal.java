package mafisher.quasar.effect.persistence;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.QuasarConfig;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.gameplay.TerrainEditService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine-managed journal of applied block changes for one gameplay effect. Entries are recorded
 * before the world write, rolled back in reverse order, and persisted with the level effect store.
 */
public final class BlockJournal {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final int SECTION_BLOCK_COST = 4_096;
    private static final String POSITION_KEY = "quasar_pos";

    private final List<Entry> entries = new ArrayList<>();
    private final List<SectionEntry> sections = new ArrayList<>();
    private final Set<Long> recordedSections = new HashSet<>();
    private final int maxEntriesOverride;
    private final int maxSectionsOverride;
    private int cursor;
    private int sectionCursor;
    private boolean rollbackPending;
    private boolean truncated;
    private boolean dirty;

    public BlockJournal() {
        this(-1, -1);
    }

    BlockJournal(int maxEntries) {
        this(maxEntries, -1);
    }

    BlockJournal(int maxEntries, int maxSections) {
        if (maxEntries == 0 || maxEntries < -1) {
            throw new IllegalArgumentException("Block journal capacity must be positive or -1");
        }
        if (maxSections == 0 || maxSections < -1) {
            throw new IllegalArgumentException("Block journal section capacity must be positive or -1");
        }
        this.maxEntriesOverride = maxEntries;
        this.maxSectionsOverride = maxSections;
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

    /**
     * Records a compact snapshot of one chunk section before a bulk edit clears it. The snapshot
     * includes block states, biomes, and block entities in that section. Re-recording a section
     * that already has a snapshot is a no-op that reports success, so scattered edits can share one
     * original-state snapshot per section.
     *
     * @return {@code false} when rollback is already pending or the section cap is exhausted
     */
    public boolean recordSection(
        ServerLevel level,
        LevelChunk chunk,
        int sectionY,
        LevelChunkSection section
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(section, "section");
        if (this.rollbackPending) {
            return false;
        }
        long sectionKey = SectionPos.asLong(chunk.getPos().x, sectionY, chunk.getPos().z);
        if (this.recordedSections.contains(sectionKey)) {
            return true;
        }
        int maxSections = this.maxSectionsOverride > 0
            ? this.maxSectionsOverride
            : QuasarConfig.blockJournalMaxSections();
        if (this.sections.size() >= maxSections) {
            if (!this.truncated) {
                this.truncated = true;
                this.dirty = true;
                LOGGER.warn(
                    "Block journal reached {} section snapshots; rollback is disabled for the affected effect",
                    maxSections
                );
            }
            return false;
        }

        byte[] data = writeSection(section);
        List<CompoundTag> blockEntities = captureBlockEntities(level, chunk, sectionY);
        this.sections.add(new SectionEntry(
            chunk.getPos(),
            sectionY,
            data,
            blockEntities,
            sectionBlockCount(data)
        ));
        this.recordedSections.add(sectionKey);
        if (!this.rollbackPending) {
            this.sectionCursor = this.sections.size();
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
        this.sectionCursor = this.sections.size();
        this.dirty = true;
    }

    public boolean isRollbackPending() {
        return this.rollbackPending;
    }

    public boolean isTruncated() {
        return this.truncated;
    }

    public int size() {
        return this.entries.size() + this.sections.size();
    }

    /** Number of bulk section snapshots currently recorded. */
    public int sectionCount() {
        return this.sections.size();
    }

    /** Whether this journal has any recorded changes or section snapshots. */
    public boolean isEmpty() {
        return this.entries.isEmpty() && this.sections.isEmpty();
    }

    public boolean canRollback() {
        return !this.truncated && !this.isEmpty();
    }

    public boolean isDirty() {
        return this.dirty;
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
        while (this.sectionCursor > 0) {
            SectionEntry entry = this.sections.get(this.sectionCursor - 1);
            int cost = SECTION_BLOCK_COST;
            if (processed + cost > maxChanges) {
                if (processed == 0) {
                    return new RollbackStep(0, 0, 0, false);
                }
                break;
            }
            this.sectionCursor--;
            processed += cost;
            if (restoreSection(level, entry)) {
                restored += entry.blockCount();
            } else {
                conflicts++;
            }
        }
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
        boolean complete = this.cursor == 0 && this.sectionCursor == 0;
        return new RollbackStep(processed, restored, conflicts, complete);
    }

    private static byte[] writeSection(LevelChunkSection section) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            section.write(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            buffer.release();
        }
    }

    private static List<CompoundTag> captureBlockEntities(
        ServerLevel level,
        LevelChunk chunk,
        int sectionY
    ) {
        List<CompoundTag> tags = new ArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY()) != sectionY) {
                continue;
            }
            try {
                CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                tag.putLong(POSITION_KEY, blockEntity.getBlockPos().asLong());
                tags.add(tag);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not capture a block entity for a section snapshot", exception);
            }
        }
        return tags;
    }

    private static int sectionBlockCount(byte[] data) {
        if (data.length < 2) {
            return 0;
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return Math.max(0, buffer.readShort());
        } catch (RuntimeException exception) {
            return 0;
        } finally {
            buffer.release();
        }
    }

    private static boolean restoreSection(ServerLevel level, SectionEntry entry) {
        LevelChunk chunk;
        try {
            chunk = level.getChunk(entry.pos().x, entry.pos().z);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not load a chunk for section rollback at {}", entry.pos(), exception);
            return false;
        }
        int index = level.getSectionIndexFromSectionY(entry.sectionY());
        LevelChunkSection[] target = chunk.getSections();
        if (index < 0 || index >= target.length) {
            return false;
        }

        LevelChunkSection restored;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(entry.data()));
        try {
            restored = new LevelChunkSection(
                level.registryAccess().registryOrThrow(Registries.BIOME)
            );
            restored.read(buffer);
            restored.recalcBlockCounts();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read a section snapshot for rollback at {}", entry.pos(), exception);
            return false;
        } finally {
            buffer.release();
        }

        for (BlockEntity existing : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (SectionPos.blockToSectionCoord(existing.getBlockPos().getY()) == entry.sectionY()) {
                chunk.removeBlockEntity(existing.getBlockPos());
            }
        }
        target[index] = restored;
        chunk.setUnsaved(true);
        for (CompoundTag tag : entry.blockEntities()) {
            try {
                BlockPos position = BlockPos.of(tag.getLong(POSITION_KEY));
                BlockEntity blockEntity = chunk.getBlockEntity(
                    position, LevelChunk.EntityCreationType.IMMEDIATE
                );
                if (blockEntity != null) {
                    blockEntity.loadWithComponents(tag.copy(), level.registryAccess());
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not restore a section block entity at {}", entry.pos(), exception);
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.initializeLightSources();
        TerrainEditService.scheduleRelightAndSync(level, entry.pos());
        return true;
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

        ListTag sectionList = new ListTag();
        for (SectionEntry entry : this.sections) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("x", entry.pos().x);
            entryTag.putInt("z", entry.pos().z);
            entryTag.putInt("sy", entry.sectionY());
            entryTag.putByteArray("data", entry.data().clone());
            entryTag.putInt("blocks", entry.blockCount());
            ListTag blockEntities = new ListTag();
            for (CompoundTag blockEntity : entry.blockEntities()) {
                blockEntities.add(blockEntity.copy());
            }
            entryTag.put("block_entities", blockEntities);
            sectionList.add(entryTag);
        }
        tag.put("sections", sectionList);

        tag.putInt("cursor", this.cursor);
        tag.putInt("section_cursor", this.sectionCursor);
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

        ListTag sectionList = tag.getList("sections", Tag.TAG_COMPOUND);
        for (int index = 0; index < sectionList.size(); index++) {
            CompoundTag entryTag = sectionList.getCompound(index);
            try {
                byte[] data = entryTag.getByteArray("data").clone();
                ListTag blockEntityList = entryTag.getList("block_entities", Tag.TAG_COMPOUND);
                List<CompoundTag> blockEntities = new ArrayList<>(blockEntityList.size());
                for (int entityIndex = 0; entityIndex < blockEntityList.size(); entityIndex++) {
                    blockEntities.add(blockEntityList.getCompound(entityIndex).copy());
                }
                journal.sections.add(new SectionEntry(
                    new ChunkPos(entryTag.getInt("x"), entryTag.getInt("z")),
                    entryTag.getInt("sy"),
                    data,
                    blockEntities,
                    entryTag.getInt("blocks")
                ));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping an unreadable section journal entry; rollback will be disabled", exception);
                journal.truncated = true;
            }
        }

        journal.rollbackPending = tag.getBoolean("rollback_pending");
        journal.truncated = tag.getBoolean("truncated");
        for (SectionEntry entry : journal.sections) {
            journal.recordedSections.add(
                SectionPos.asLong(entry.pos().x, entry.sectionY(), entry.pos().z)
            );
        }
        journal.cursor = tag.contains("cursor")
            ? Math.min(journal.entries.size(), Math.max(0, tag.getInt("cursor")))
            : journal.entries.size();
        journal.sectionCursor = tag.contains("section_cursor")
            ? Math.min(journal.sections.size(), Math.max(0, tag.getInt("section_cursor")))
            : journal.sections.size();
        if (!journal.rollbackPending) {
            journal.cursor = journal.entries.size();
            journal.sectionCursor = journal.sections.size();
        }

        int maxEntries = QuasarConfig.blockJournalMaxEntries();
        if (journal.entries.size() > maxEntries) {
            journal.entries.clear();
            journal.cursor = 0;
            journal.truncated = true;
        }
        int maxSections = QuasarConfig.blockJournalMaxSections();
        if (journal.sections.size() > maxSections) {
            journal.sections.clear();
            journal.recordedSections.clear();
            journal.sectionCursor = 0;
            journal.truncated = true;
        }
        return journal;
    }

    record RollbackStep(int processed, int restored, int conflicts, boolean complete) {
    }

    private record SectionEntry(
        ChunkPos pos,
        int sectionY,
        byte[] data,
        List<CompoundTag> blockEntities,
        int blockCount
    ) {
        private SectionEntry {
            Objects.requireNonNull(pos, "pos");
            if (sectionY < -2_048 || sectionY > 2_048) {
                throw new IllegalArgumentException("Invalid section Y in block journal snapshot");
            }
            data = Objects.requireNonNull(data, "data").clone();
            List<CompoundTag> copied = new ArrayList<>(blockEntities.size());
            for (CompoundTag tag : blockEntities) {
                copied.add(tag.copy());
            }
            blockEntities = List.copyOf(copied);
            blockCount = Math.max(0, blockCount);
        }
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
