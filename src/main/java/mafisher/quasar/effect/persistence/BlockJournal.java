package mafisher.quasar.effect.persistence;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.server.level.TicketType;
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
 * Rollback loads chunks through tickets, advances its cursors only after a successful restore, and
 * fails closed on invalid or newer journals instead of replaying restored snapshots.
 */
public final class BlockJournal {
    static final int SCHEMA_VERSION = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final int SECTION_BLOCK_COST = 4_096;
    private static final String POSITION_KEY = "quasar_pos";
    private static final String SCHEMA_KEY = "journal_schema";
    private static final TicketType<ChunkPos> ROLLBACK_TICKET = TicketType.create(
        "quasar_block_rollback", Comparator.comparingLong(ChunkPos::toLong)
    );

    private final List<Entry> entries = new ArrayList<>();
    private final List<SectionEntry> sections = new ArrayList<>();
    private final Set<Long> recordedSections = new HashSet<>();
    private final Set<ChunkPos> loadTickets = new HashSet<>();
    private final int maxEntriesOverride;
    private final int maxSectionsOverride;
    private int cursor;
    private int sectionCursor;
    private boolean rollbackPending;
    private boolean truncated;
    private boolean dirty;
    private boolean failureLogged;
    private boolean loadWaitLogged;

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

    /**
     * Restores up to the budget. Cursors move only after a successful restore, so transient failures
     * keep the same entry pending for the next tick.
     */
    RollbackStep rollbackNext(ServerLevel level, int maxChanges) {
        Objects.requireNonNull(level, "level");
        if (maxChanges < 0) {
            throw new IllegalArgumentException("Rollback budget cannot be negative");
        }
        if (this.truncated) {
            throw new IllegalStateException("Cannot roll back a truncated or invalid block journal");
        }
        if (!this.rollbackPending) {
            this.beginRollback();
        }
        if (maxChanges == 0) {
            boolean complete = this.cursor == 0 && this.sectionCursor == 0;
            if (complete) {
                releaseLoadTickets(level);
            }
            return new RollbackStep(0, 0, 0, 0, 0, complete, 0);
        }

        releaseTicketsExceptCurrentCursor(level);
        int processed = 0;
        int advanced = 0;
        int restored = 0;
        int conflicts = 0;
        while (this.sectionCursor > 0) {
            SectionEntry entry = this.sections.get(this.sectionCursor - 1);
            // A section restore is one bulk operation. Charging the full block count could exceed
            // a small block budget and stall rollback forever, so one section always fits a tick.
            int cost = sectionCost(maxChanges);
            if (processed + cost > maxChanges) {
                if (processed == 0) {
                    return new RollbackStep(0, 0, 0, 0, 0, false, 0);
                }
                break;
            }
            LevelChunk chunk;
            try {
                chunk = level.getChunkSource().getChunkNow(entry.pos().x, entry.pos().z);
            } catch (RuntimeException exception) {
                logRollbackFailure("Could not inspect a chunk for section rollback at {}", entry.pos(), exception);
                return failedStep(processed, advanced, restored, conflicts);
            }
            if (chunk == null) {
                requestChunkLoad(level, entry.pos());
                return waitingStep(processed, advanced, restored, conflicts);
            }
            processed += cost;
            boolean sectionRestored;
            try {
                sectionRestored = restoreSection(level, chunk, entry);
            } catch (RuntimeException exception) {
                logRollbackFailure("Could not restore a section snapshot at {}", entry.pos(), exception);
                return failedStep(processed, advanced, restored, conflicts);
            }
            if (!sectionRestored) {
                return failedStep(processed, advanced, restored, conflicts);
            }
            this.sectionCursor--;
            advanced++;
            restored += entry.blockCount();
            this.failureLogged = false;
        }
        while (processed < maxChanges && this.cursor > 0) {
            Entry entry = this.entries.get(this.cursor - 1);
            boolean chunkLoaded;
            try {
                chunkLoaded = level.hasChunkAt(entry.position);
            } catch (RuntimeException exception) {
                logRollbackFailure("Could not inspect a chunk for block rollback at {}", entry.position, exception);
                return failedStep(processed, advanced, restored, conflicts);
            }
            if (!chunkLoaded) {
                requestChunkLoad(level, new ChunkPos(entry.position));
                return waitingStep(processed, advanced, restored, conflicts);
            }
            processed++;

            BlockState currentState;
            try {
                currentState = level.getBlockState(entry.position);
            } catch (RuntimeException exception) {
                logRollbackFailure("Could not read a block for rollback at {}", entry.position, exception);
                return failedStep(processed, advanced, restored, conflicts);
            }
            if (!currentState.equals(entry.newState)) {
                this.cursor--;
                advanced++;
                conflicts++;
                this.failureLogged = false;
                continue;
            }

            try {
                if (!level.setBlockAndUpdate(entry.position, entry.oldState)) {
                    logRollbackFailure("Could not roll back the block at {}", entry.position);
                    return failedStep(processed, advanced, restored, conflicts);
                }
            } catch (RuntimeException exception) {
                logRollbackFailure("Could not roll back the block at {}", entry.position, exception);
                return failedStep(processed, advanced, restored, conflicts);
            }
            this.cursor--;
            advanced++;
            restored++;
            this.failureLogged = false;
            restoreBlockEntity(level, entry);
        }

        this.dirty = this.dirty || advanced > 0;
        boolean complete = this.cursor == 0 && this.sectionCursor == 0;
        if (complete) {
            releaseLoadTickets(level);
        }
        return new RollbackStep(processed, advanced, restored, conflicts, 0, complete, 0);
    }

    /** One section still fits any positive block budget; a zero budget does no work. */
    static int sectionCost(int maxChanges) {
        return Math.min(SECTION_BLOCK_COST, Math.max(1, maxChanges));
    }

    private RollbackStep failedStep(int processed, int advanced, int restored, int conflicts) {
        this.dirty = this.dirty || advanced > 0;
        return new RollbackStep(processed, advanced, restored, conflicts, 1, false, 0);
    }

    private RollbackStep waitingStep(int processed, int advanced, int restored, int conflicts) {
        this.dirty = this.dirty || advanced > 0;
        return new RollbackStep(processed, advanced, restored, conflicts, 0, false, 1);
    }

    private void requestChunkLoad(ServerLevel level, ChunkPos chunk) {
        if (!this.loadTickets.add(chunk)) {
            return;
        }
        try {
            level.getChunkSource().addRegionTicket(ROLLBACK_TICKET, chunk, 0, chunk);
        } catch (RuntimeException exception) {
            this.loadTickets.remove(chunk);
            LOGGER.warn("Could not request a chunk load for block rollback at {}", chunk, exception);
            return;
        }
        if (!this.loadWaitLogged) {
            this.loadWaitLogged = true;
            LOGGER.warn("Block rollback is waiting for chunk {} to load", chunk);
        }
    }

    private void releaseTicketsExceptCurrentCursor(ServerLevel level) {
        ChunkPos keep = currentCursorChunk();
        if (this.loadTickets.isEmpty()) {
            return;
        }
        for (ChunkPos chunk : new ArrayList<>(this.loadTickets)) {
            if (!chunk.equals(keep)) {
                releaseChunkLoad(level, chunk);
            }
        }
    }

    private ChunkPos currentCursorChunk() {
        if (this.sectionCursor > 0) {
            return this.sections.get(this.sectionCursor - 1).pos();
        }
        if (this.cursor > 0) {
            return new ChunkPos(this.entries.get(this.cursor - 1).position);
        }
        return null;
    }

    /** Releases tickets held while rollback waited for chunk loads. */
    void releaseLoadTickets(ServerLevel level) {
        for (ChunkPos chunk : new ArrayList<>(this.loadTickets)) {
            releaseChunkLoad(level, chunk);
        }
        this.loadWaitLogged = false;
    }

    private void releaseChunkLoad(ServerLevel level, ChunkPos chunk) {
        this.loadTickets.remove(chunk);
        try {
            level.getChunkSource().removeRegionTicket(ROLLBACK_TICKET, chunk, 0, chunk);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not release a rollback chunk ticket for {}", chunk, exception);
        }
    }

    private void logRollbackFailure(String message, Object... arguments) {
        if (this.failureLogged) {
            LOGGER.debug(message, arguments);
        } else {
            LOGGER.warn(message, arguments);
            this.failureLogged = true;
        }
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

    private boolean restoreSection(ServerLevel level, LevelChunk chunk, SectionEntry entry) {
        int index = level.getSectionIndexFromSectionY(entry.sectionY());
        LevelChunkSection[] target = chunk.getSections();
        if (index < 0 || index >= target.length) {
            logRollbackFailure(
                "Could not find section " + entry.sectionY() + " for block rollback in " + entry.pos()
            );
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
            logRollbackFailure("Could not read a section snapshot for rollback at {}", entry.pos(), exception);
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
        tag.putInt(SCHEMA_KEY, SCHEMA_VERSION);
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
        int version = tag.contains(SCHEMA_KEY) ? tag.getInt(SCHEMA_KEY) : 0;
        if (version < 0 || version > SCHEMA_VERSION) {
            journal.rollbackPending = tag.getBoolean("rollback_pending");
            journal.truncated = true;
            LOGGER.warn(
                "Block journal schema {} is not supported (expected 0..{}); rollback is disabled",
                version,
                SCHEMA_VERSION
            );
            return journal;
        }
        journal.loadEntries(tag);
        journal.loadSections(tag);
        journal.truncated = journal.truncated || tag.getBoolean("truncated");
        journal.rollbackPending = tag.getBoolean("rollback_pending");
        journal.loadCursors(tag);
        journal.enforceCaps();
        return journal;
    }

    private void loadEntries(CompoundTag tag) {
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
                this.entries.add(new Entry(position, oldState, newState, blockEntity));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping an unreadable block journal entry; rollback will be disabled", exception);
                this.truncated = true;
            }
        }
    }

    private void loadSections(CompoundTag tag) {
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
                ChunkPos pos = new ChunkPos(entryTag.getInt("x"), entryTag.getInt("z"));
                int sectionY = entryTag.getInt("sy");
                this.sections.add(new SectionEntry(
                    pos,
                    sectionY,
                    data,
                    blockEntities,
                    entryTag.getInt("blocks")
                ));
                this.recordedSections.add(SectionPos.asLong(pos.x, sectionY, pos.z));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping an unreadable section journal entry; rollback will be disabled", exception);
                this.truncated = true;
            }
        }
    }

    private void loadCursors(CompoundTag tag) {
        if (!this.rollbackPending) {
            this.cursor = this.entries.size();
            this.sectionCursor = this.sections.size();
            return;
        }
        if (!cursorIsValid(tag, "cursor", this.entries.size())
            || !cursorIsValid(tag, "section_cursor", this.sections.size())) {
            this.cursor = 0;
            this.sectionCursor = 0;
            this.truncated = true;
            LOGGER.warn(
                "Block journal has missing or invalid rollback cursors; "
                    + "rollback is disabled instead of replaying restored snapshots"
            );
            return;
        }
        this.cursor = this.entries.isEmpty() ? 0 : tag.getInt("cursor");
        this.sectionCursor = this.sections.isEmpty() ? 0 : tag.getInt("section_cursor");
    }

    private static boolean cursorIsValid(CompoundTag tag, String key, int size) {
        if (!tag.contains(key)) {
            return size == 0;
        }
        int cursor = tag.getInt(key);
        return cursor >= 0 && cursor <= size;
    }

    private void enforceCaps() {
        int maxEntries = QuasarConfig.blockJournalMaxEntries();
        if (this.entries.size() > maxEntries) {
            this.entries.clear();
            this.cursor = 0;
            this.truncated = true;
        }
        int maxSections = QuasarConfig.blockJournalMaxSections();
        if (this.sections.size() > maxSections) {
            this.sections.clear();
            this.recordedSections.clear();
            this.sectionCursor = 0;
            this.truncated = true;
        }
    }

    /**
     * One budgeted rollback step; {@code advanced} retires work, {@code failed} asks for a retry,
     * and {@code waiting} reports a chunk load that has not finished yet.
     */
    record RollbackStep(
        int processed,
        int advanced,
        int restored,
        int conflicts,
        int failed,
        boolean complete,
        int waiting
    ) {
        RollbackStep {
            if (processed < 0 || advanced < 0 || restored < 0) {
                throw new IllegalArgumentException("Invalid block rollback step");
            }
            if (conflicts < 0 || failed < 0 || waiting < 0) {
                throw new IllegalArgumentException("Invalid block rollback step");
            }
        }
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
