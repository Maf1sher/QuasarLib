package mafisher.quasar.api.gameplay.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.effect.gameplay.TerrainEditService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Budgeted, resumable bulk terrain editor for one gameplay session. Whole 16x16x16 sections are
 * cleared through the chunk-section palette instead of per-block level writes; partial boundary
 * sections fall back to per-block edits so the surface stays smooth. Heightmaps, lighting, block
 * entities, chunk tickets, and one full-chunk client resend per changed column are handled by the
 * engine. {@link SyncPolicy#PROGRESSIVE} additionally reveals a column once the current radial
 * front has passed all of its sections, so the client never receives a half-carved column; depth
 * reveals are spaced out and flushed at the end.
 *
 * <p>Server authority is unchanged: protection is queried before each change and the block journal
 * records a compact section snapshot for rollback. Clients only present the final chunk.
 */
public final class EffectBulkBlockEditor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectBulkBlockEditor.class);
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    private static final int MAX_CLEARED_STATES = 12;
    private static final int MAX_TICKET_CHUNKS = 1_024;
    private static final int MAX_SECTIONS = 16_384;
    private static final int MAX_STALLED_TICKS = 200;
    /** Minimum normalized front advance before a column is revealed again. */
    private static final double REVEAL_STEP = 0.15;
    private static final int[] NEIGHBOR_OFFSETS = {-1, 1, -16, 16, -256, 256};
    private static final Set<Block> INDESTRUCTIBLE = Set.of(
        Blocks.BEDROCK,
        Blocks.BARRIER,
        Blocks.COMMAND_BLOCK,
        Blocks.CHAIN_COMMAND_BLOCK,
        Blocks.REPEATING_COMMAND_BLOCK,
        Blocks.STRUCTURE_BLOCK,
        Blocks.JIGSAW,
        Blocks.END_PORTAL_FRAME,
        Blocks.END_PORTAL
    );

    public enum SyncPolicy {
        IMMEDIATE,
        ON_COMPLETE,
        MANUAL,
        /** Reveals each changed column at its first edit and once more after its last section. */
        PROGRESSIVE
    }

    /** Section cursor order. {@code CENTER_OUT} is the radial destruction-front mode. */
    public enum Ordering {
        TOP_DOWN,
        CENTER_OUT
    }

    public record Spec(
        EffectBlockVolume volume,
        BlockState replacement,
        boolean removeBlockEntities,
        SyncPolicy syncPolicy,
        double immediateSyncRadius,
        boolean journal
    ) {
        public Spec {
            Objects.requireNonNull(volume, "volume");
            Objects.requireNonNull(replacement, "replacement");
            syncPolicy = syncPolicy == null ? SyncPolicy.ON_COMPLETE : syncPolicy;
            if (!Double.isFinite(immediateSyncRadius) || immediateSyncRadius < 0.0) {
                throw new IllegalArgumentException("Immediate sync radius must be finite and non-negative");
            }
        }
    }

    public record Progress(int totalSections, int completedSections, long clearedBlocks, boolean finished) {
    }

    private final GameplayEffectContext context;
    private final ServerLevel level;
    private final Spec spec;
    private final Ordering ordering;
    private final List<SectionEntry> sections;
    /** Normalized front advance between depth reveals, tuned to about twelve blocks. */
    private final double revealStep;
    private final Set<ChunkPos> ticketChunks;
    private final Set<ChunkPos> heldTicketChunks = new LinkedHashSet<>();
    /** Index of the next unprocessed section in each chunk, or {@code sections.size()}. */
    private final Map<ChunkPos, Integer> nextSectionIndexByChunk = new HashMap<>();
    private final Set<ChunkPos> dirtyChunks = new LinkedHashSet<>();
    private final Map<ChunkPos, Double> lastReleaseScale = new HashMap<>();
    private final Set<BlockState> clearedStates = new LinkedHashSet<>();
    private int sectionIndex;
    private int stalledTicks;
    private int ticketWaitTicks;
    private long clearedBlocks;
    private int editedSections;
    private double frontScale;
    private boolean syncReleased;
    private boolean finished;
    private boolean closed;
    private boolean ticketsAcquired;

    private EffectBulkBlockEditor(GameplayEffectContext context, Spec spec, Ordering ordering) {
        this.context = Objects.requireNonNull(context, "context");
        this.level = context.level();
        this.spec = Objects.requireNonNull(spec, "spec");
        this.ordering = ordering == null ? Ordering.TOP_DOWN : ordering;
        this.sections = buildSections(spec.volume(), this.level, this.ordering);
        this.revealStep = computeRevealStep(spec.volume());
        this.ticketChunks = ticketChunks(this.sections);
        this.indexSectionsByChunk();
        this.context.onClose(this::releaseTickets);
    }

    public static EffectBulkBlockEditor create(GameplayEffectContext context, Spec spec) {
        return create(context, spec, Ordering.TOP_DOWN);
    }

    public static EffectBulkBlockEditor create(
        GameplayEffectContext context,
        Spec spec,
        Ordering ordering
    ) {
        return new EffectBulkBlockEditor(context, spec, ordering);
    }

    /** Restores one editor from its persisted cursor tag. */
    public static EffectBulkBlockEditor restore(GameplayEffectContext context, CompoundTag tag) {
        return restore(context, tag, Ordering.TOP_DOWN);
    }

    /** Restores one editor and uses {@code fallbackOrdering} for tags written before ordering. */
    public static EffectBulkBlockEditor restore(
        GameplayEffectContext context,
        CompoundTag tag,
        Ordering fallbackOrdering
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tag, "tag");
        Ordering ordering = fallbackOrdering == null ? Ordering.TOP_DOWN : fallbackOrdering;
        if (tag.contains("ordering")) {
            try {
                ordering = Ordering.valueOf(tag.getString("ordering"));
            } catch (IllegalArgumentException ignored) {
                // Keep the caller's fallback for unknown future values.
            }
        }
        Spec spec = readSpec(tag);
        EffectBulkBlockEditor editor = new EffectBulkBlockEditor(context, spec, ordering);
        editor.sectionIndex = Math.max(0, Math.min(editor.sections.size(), tag.getInt("section_index")));
        editor.clearedBlocks = Math.max(0L, tag.getLong("cleared_blocks"));
        editor.editedSections = tag.contains("edited_sections")
            ? Math.max(0, Math.min(editor.sectionIndex, tag.getInt("edited_sections")))
            : editor.sectionIndex;
        editor.syncReleased = tag.getBoolean("sync_released");
        editor.finished = tag.getBoolean("finished") || editor.sectionIndex >= editor.sections.size();
        if (tag.contains("front_scale")) {
            editor.frontScale = Math.max(0.0, Math.min(1.0, tag.getDouble("front_scale")));
        } else {
            editor.recomputeFrontScale();
        }
        editor.rebuildNextSectionIndices();
        editor.rebuildDirtyState();
        return editor;
    }

    /** Advances the edit within the session and level-wide section budgets. */
    public Progress tick() {
        return this.tick(Integer.MAX_VALUE);
    }

    /**
     * Advances the edit by at most {@code maxSections} edited sections. Skipped all-air or
     * already-clear sections do not consume the limit; the caller uses the limit to pace a
     * destruction front without changing the engine's hard budget checks.
     */
    public Progress tick(int maxSections) {
        return this.tickInternal(Double.POSITIVE_INFINITY, Math.max(0, maxSections));
    }

    /**
     * Advances a {@link Ordering#CENTER_OUT} edit up to a normalized radius. Sections beyond the
     * target stay queued, so the visible front follows the requested sphere instead of jumping
     * to the final volume. The limit still caps edited sections for the level tick budget.
     */
    public Progress tickToScale(double scale) {
        return this.tickToScale(scale, Integer.MAX_VALUE);
    }

    /** @see #tickToScale(double) */
    public Progress tickToScale(double scale, int maxSections) {
        if (this.ordering != Ordering.CENTER_OUT) {
            throw new IllegalStateException("tickToScale requires CENTER_OUT ordering");
        }
        if (!Double.isFinite(scale)) {
            throw new IllegalArgumentException("Front scale must be finite");
        }
        double clamped = Math.max(0.0, Math.min(1.0, scale));
        return this.tickInternal(clamped * clamped, Math.max(0, maxSections));
    }

    private Progress tickInternal(double targetSquared, int maxSections) {
        if (this.closed || this.finished) {
            return this.progress();
        }
        if (!this.ensureTickets()) {
            return this.progress();
        }
        int limit = Math.max(0, maxSections);
        int edited = 0;
        while (this.sectionIndex < this.sections.size() && edited < limit) {
            SectionEntry entry = this.sections.get(this.sectionIndex);
            if (entry.minDistanceSquared() > targetSquared) {
                break;
            }
            SectionKey key = entry.key();
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(key.sectionX(), key.sectionZ());
            if (chunk == null) {
                this.stalledTicks++;
                if (this.stalledTicks == MAX_STALLED_TICKS) {
                    LOGGER.warn(
                        "Bulk edit is waiting for chunk {} {}; the front will resume when it loads",
                        key.sectionX(), key.sectionZ()
                    );
                }
                break;
            }
            int sectionArrayIndex = this.level.getSectionIndexFromSectionY(key.sectionY());
            LevelChunkSection section = chunk.getSection(sectionArrayIndex);
            if (section == null
                || section.hasOnlyAir()
                || !section.maybeHas(state -> !state.isAir()
                    && !INDESTRUCTIBLE.contains(state.getBlock()))) {
                this.passSection(entry);
                this.sectionIndex++;
                this.stalledTicks = 0;
                continue;
            }
            if (!this.context.tryAcquireSectionEdit()) {
                break;
            }
            this.sectionIndex++;
            this.stalledTicks = 0;
            this.editSection(chunk, section, key);
            this.advanceFront(entry);
            this.passSection(entry);
            this.editedSections++;
            edited++;
        }
        this.releaseProgressiveFront(targetSquared);
        this.finishIfComplete();
        return this.progress();
    }

    private void releaseProgressiveFront(double targetSquared) {
        if (this.spec.syncPolicy() != SyncPolicy.PROGRESSIVE || this.syncReleased) {
            return;
        }
        double targetScale = Double.isInfinite(targetSquared)
            ? this.frontScale
            : Math.min(1.0, Math.sqrt(Math.max(0.0, targetSquared)));
        this.releaseFrontChunks(targetScale);
    }

    private void finishIfComplete() {
        if (this.sectionIndex < this.sections.size()) {
            return;
        }
        this.finished = true;
        if (this.spec.syncPolicy() == SyncPolicy.ON_COMPLETE
            || this.spec.syncPolicy() == SyncPolicy.PROGRESSIVE) {
            this.releaseSync();
        }
    }

    private void passSection(SectionEntry entry) {
        ChunkPos chunk = new ChunkPos(entry.key().sectionX(), entry.key().sectionZ());
        this.nextSectionIndexByChunk.put(chunk, entry.nextIndex());
    }

    /** Releases the deferred client resend for every chunk edited so far. */
    public void releaseSync() {
        if (this.closed || this.syncReleased) {
            return;
        }
        this.syncReleased = true;
        this.requestAllSync();
    }

    private void requestAllSync() {
        for (ChunkPos chunk : this.dirtyChunks) {
            if (this.spec.syncPolicy() == SyncPolicy.PROGRESSIVE) {
                TerrainEditService.queuePriorityRelight(this.level, chunk);
            }
            TerrainEditService.requestSync(this.level, chunk, true);
        }
    }

    /** Closes the editor and releases its chunk tickets. */
    public void close(boolean releaseSync) {
        if (this.closed) {
            return;
        }
        if (releaseSync) {
            this.releaseSync();
        }
        this.releaseTickets();
        this.closed = true;
    }

    private boolean ensureTickets() {
        if (this.ticketsAcquired) {
            return true;
        }
        if (!TerrainEditService.tryAcquireTickets(this.level, this.ticketChunks)) {
            this.ticketWaitTicks++;
            if (this.ticketWaitTicks == MAX_STALLED_TICKS) {
                LOGGER.warn(
                    "Bulk edit is waiting for {} chunk tickets; the aggregate ticket budget is full",
                    this.ticketChunks.size()
                );
            }
            return false;
        }
        this.heldTicketChunks.addAll(this.ticketChunks);
        this.ticketsAcquired = true;
        this.ticketWaitTicks = 0;
        return true;
    }

    private void releaseTickets() {
        if (!this.ticketsAcquired) {
            return;
        }
        TerrainEditService.releaseTicketsWhenSettled(this.level, this.heldTicketChunks);
        this.heldTicketChunks.clear();
        this.ticketsAcquired = false;
    }

    public Progress progress() {
        return new Progress(
            this.sections.size(), Math.min(this.sectionIndex, this.sections.size()), this.clearedBlocks, this.finished
        );
    }

    /** Normalized radius of the furthest edited section, for radial destruction fronts. */
    public double frontScale() {
        return this.frontScale;
    }

    /** Cursor ordering this editor was created or restored with. */
    public Ordering ordering() {
        return this.ordering;
    }

    /** Number of sections that actually reached an edit attempt (skipped air is excluded). */
    public int editedSections() {
        return this.editedSections;
    }

    /**
     * Whether any section left in the cursor still contains editable blocks. All-air and
     * indestructible-only sections do not count, so a completed terrain carve can be recognised
     * even when only empty sections remain in the ordered cursor.
     */
    public boolean hasPendingSolidSections() {
        if (this.closed || this.finished) {
            return false;
        }
        for (int index = this.sectionIndex; index < this.sections.size(); index++) {
            SectionKey key = this.sections.get(index).key();
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(
                key.sectionX(), key.sectionZ()
            );
            if (chunk == null) {
                return true;
            }
            int sectionArrayIndex = this.level.getSectionIndexFromSectionY(key.sectionY());
            LevelChunkSection section = chunk.getSection(sectionArrayIndex);
            if (section == null) {
                continue;
            }
            if (!section.hasOnlyAir() && section.maybeHas(state -> !state.isAir()
                && !INDESTRUCTIBLE.contains(state.getBlock()))) {
                return true;
            }
        }
        return false;
    }

    /** The volume this editor was created for. */
    public EffectBlockVolume volume() {
        return this.spec.volume();
    }

    /** Hard safety cap for one editor instance; callers can use it to size a carve. */
    public static int maxSectionsPerEdit() {
        return MAX_SECTIONS;
    }

    /** Hard safety cap for chunks held by one editor instance. */
    public static int maxTicketChunks() {
        return MAX_TICKET_CHUNKS;
    }

    /** Chunks whose client resend is still queued; package-private for lifecycle tests. */
    int dirtyChunkCount() {
        return this.dirtyChunks.size();
    }

    /** Chunks held by this edit whose columns are not currently loaded. */
    public int missingChunkCount() {
        int missing = 0;
        for (ChunkPos chunk : this.ticketChunks) {
            if (this.level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                missing++;
            }
        }
        return missing;
    }

    /** Whether every chunk held by this edit is loaded and ready for section edits. */
    public boolean allChunksLoaded() {
        return this.missingChunkCount() == 0;
    }

    /** Up to twelve distinct non-air states sampled from the cleared volume, for debris palettes. */
    public List<BlockState> clearedStates() {
        return List.copyOf(this.clearedStates);
    }

    private void advanceFront(SectionEntry entry) {
        if (this.ordering != Ordering.CENTER_OUT) {
            return;
        }
        this.frontScale = Math.max(
            this.frontScale,
            Math.min(1.0, Math.sqrt(entry.distanceSquared()))
        );
    }

    private void recomputeFrontScale() {
        this.frontScale = 0.0;
        if (this.ordering != Ordering.CENTER_OUT) {
            return;
        }
        int processed = Math.min(this.sectionIndex, this.sections.size());
        for (int index = 0; index < processed; index++) {
            SectionEntry entry = this.sections.get(index);
            this.frontScale = Math.max(
                this.frontScale,
                Math.min(1.0, Math.sqrt(entry.distanceSquared()))
            );
        }
    }

    private void indexSectionsByChunk() {
        this.rebuildNextSectionIndices();
    }

    /** Rebuilds the next-section cursor for every chunk from the current section index. */
    private void rebuildNextSectionIndices() {
        this.nextSectionIndexByChunk.clear();
        int start = Math.max(0, Math.min(this.sectionIndex, this.sections.size()));
        for (int index = this.sections.size() - 1; index >= start; index--) {
            SectionEntry entry = this.sections.get(index);
            this.nextSectionIndexByChunk.put(chunkOf(entry.key()), index);
        }
        for (SectionEntry entry : this.sections) {
            this.nextSectionIndexByChunk.putIfAbsent(chunkOf(entry.key()), this.sections.size());
        }
    }

    /**
     * Reveals a chunk only after the current front has passed every section in its column. This
     * keeps the client from receiving a half-carved column and spreads the relight work across
     * the whole destruction window instead of a final flush.
     */
    private void releaseFrontChunks(double targetScale) {
        if (this.syncReleased) {
            return;
        }
        double targetSquared = targetScale * targetScale;
        Iterator<ChunkPos> iterator = this.dirtyChunks.iterator();
        while (iterator.hasNext()) {
            ChunkPos chunk = iterator.next();
            int nextIndex = this.nextSectionIndexByChunk.getOrDefault(chunk, this.sections.size());
            if (nextIndex < this.sections.size()
                && this.sections.get(nextIndex).minDistanceSquared() <= targetSquared) {
                continue;
            }
            double previous = this.lastReleaseScale.getOrDefault(chunk, -1.0);
            if (previous >= 0.0 && targetScale - previous < this.revealStep) {
                continue;
            }
            this.lastReleaseScale.put(chunk, targetScale);
            TerrainEditService.queuePriorityRelight(this.level, chunk);
            TerrainEditService.requestSync(this.level, chunk, true);
            iterator.remove();
        }
    }

    private static ChunkPos chunkOf(SectionKey key) {
        return new ChunkPos(key.sectionX(), key.sectionZ());
    }

    private static double computeRevealStep(EffectBlockVolume volume) {
        EffectBounds bounds = volume.bounds();
        double span = Math.max(
            bounds.maxX() - bounds.minX(),
            bounds.maxZ() - bounds.minZ()
        ) * 0.5;
        return Math.min(REVEAL_STEP, 12.0 / Math.max(1.0, span));
    }

    /** Persists the cursor and shape for session restore. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        writeVolume(tag, this.spec.volume());
        tag.put("replacement", NbtUtils.writeBlockState(this.spec.replacement()));
        tag.putBoolean("remove_block_entities", this.spec.removeBlockEntities());
        tag.putString("sync_policy", this.spec.syncPolicy().name());
        tag.putString("ordering", this.ordering.name());
        tag.putDouble("immediate_sync_radius", this.spec.immediateSyncRadius());
        tag.putBoolean("journal", this.spec.journal());
        tag.putInt("section_index", this.sectionIndex);
        tag.putLong("cleared_blocks", this.clearedBlocks);
        tag.putInt("edited_sections", this.editedSections);
        tag.putDouble("front_scale", this.frontScale);
        tag.putBoolean("sync_released", this.syncReleased);
        tag.putBoolean("finished", this.finished);
        return tag;
    }

    private void editSection(LevelChunk chunk, LevelChunkSection section, SectionKey key) {
        BlockState[] previousStates = new BlockState[SECTION_BLOCKS];
        SectionEdit edit = this.applySectionEdit(chunk, section, key, previousStates);
        if (edit == null || (edit.cleared() == 0 && edit.vetoed() == 0)) {
            return;
        }
        this.context.recordBulkSection(edit.cleared(), edit.vetoed());
        this.clearedBlocks += edit.cleared();
        if (edit.cleared() == 0) {
            return;
        }

        if (this.spec.removeBlockEntities() && this.spec.replacement().isAir()) {
            if (edit.fullSection()) {
                removeAllBlockEntities(chunk, key.sectionY());
            } else {
                removeBlockEntities(chunk, section, key);
            }
        }
        this.notifySectionChanges(chunk, edit.current(), key, previousStates);
        this.markChunkEdited(chunk, key, edit.current().hasOnlyAir());
    }

    private SectionEdit applySectionEdit(
        LevelChunk chunk,
        LevelChunkSection section,
        SectionKey key,
        BlockState[] previousStates
    ) {
        boolean protectedWorld = this.context.hasBlockProtection();
        if (this.canClearWholeSection(section, key, protectedWorld)) {
            return this.clearWholeSection(
                chunk, section, key, previousStates
            );
        }
        return this.clearByBlock(
            chunk,
            section,
            key,
            this.isFullyInside(key),
            protectedWorld,
            previousStates,
            () -> this.recordSnapshot(chunk, section, key)
        );
    }

    private boolean canClearWholeSection(
        LevelChunkSection section,
        SectionKey key,
        boolean protectedWorld
    ) {
        return this.isFullyInside(key)
            && key.sectionY() > this.level.getMinSection()
            && this.spec.replacement().isAir()
            && !protectedWorld
            && !section.maybeHas(state -> INDESTRUCTIBLE.contains(state.getBlock()));
    }

    private SectionEdit clearWholeSection(
        LevelChunk chunk,
        LevelChunkSection section,
        SectionKey key,
        BlockState[] previousStates
    ) {
        int cleared = this.countClearable(section);
        if (cleared == 0) {
            return null;
        }
        if (!this.recordSnapshot(chunk, section, key)) {
            return new SectionEdit(0, cleared, section, false);
        }
        for (int index = 0; index < SECTION_BLOCKS; index++) {
            BlockState state = section.getBlockState(
                index & 15, (index >> 4) & 15, (index >> 8) & 15
            );
            if (!state.equals(this.spec.replacement())) {
                previousStates[index] = state;
            }
        }
        return new SectionEdit(cleared, 0, this.replaceWholeSection(chunk, section, key), true);
    }

    private boolean recordSnapshot(LevelChunk chunk, LevelChunkSection section, SectionKey key) {
        if (!this.spec.journal()) {
            return true;
        }
        // A full journal keeps applying edits without rollback; an unreadable block entity does not.
        return this.context.recordSectionSnapshot(this.level, chunk, key.sectionY(), section);
    }

    /** Counts blocks a whole-section replacement would clear without touching the world. */
    private int countClearable(LevelChunkSection section) {
        int[] cleared = {0};
        section.getStates().count((state, count) -> {
            if (!state.isAir() && !state.equals(this.spec.replacement())) {
                cleared[0] += count;
                this.addClearedState(state);
            }
        });
        return cleared[0];
    }

    /**
     * Replaces a fully covered section with one air-filled section in one operation. This keeps
     * large author scales practical without writing 4096 block states on the server thread.
     */
    private LevelChunkSection replaceWholeSection(
        LevelChunk chunk,
        LevelChunkSection section,
        SectionKey key
    ) {
        int sectionArrayIndex = this.level.getSectionIndexFromSectionY(key.sectionY());
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        LevelChunkSection replacement = new LevelChunkSection(states, section.getBiomes());
        chunk.getSections()[sectionArrayIndex] = replacement;
        return replacement;
    }

    private boolean shouldSkipBlock(
        BlockState current,
        int worldX,
        int worldY,
        int worldZ,
        boolean fullyInside
    ) {
        if (worldY <= this.level.getMinBuildHeight()) {
            // Keep the bottom layer so a large carve never opens a see-through void floor.
            return true;
        }
        if (!fullyInside && !this.spec.volume().contains(
            worldX + 0.5, worldY + 0.5, worldZ + 0.5
        )) {
            return true;
        }
        return current.isAir()
            || current.equals(this.spec.replacement())
            || INDESTRUCTIBLE.contains(current.getBlock());
    }

    private SectionEdit clearByBlock(
        LevelChunk chunk,
        LevelChunkSection section,
        SectionKey key,
        boolean fullyInside,
        boolean protectedWorld,
        BlockState[] previousStates,
        BooleanSupplier beforeFirstWrite
    ) {
        int cleared = 0;
        int[] vetoed = {0};
        boolean snapshotRecorded = false;
        boolean useWorldWrite = !this.spec.replacement().isAir();
        int baseX = key.sectionX() << 4;
        int baseY = key.sectionY() << 4;
        int baseZ = key.sectionZ() << 4;

        for (int index = 0; index < SECTION_BLOCKS; index++) {
            int localX = index & 15;
            int localY = (index >> 4) & 15;
            int localZ = (index >> 8) & 15;
            int worldX = baseX + localX;
            int worldY = baseY + localY;
            int worldZ = baseZ + localZ;
            BlockState current = section.getBlockState(localX, localY, localZ);
            if (this.shouldSkipBlock(current, worldX, worldY, worldZ, fullyInside)) {
                continue;
            }
            if (protectedWorld && !this.allows(worldX, worldY, worldZ, current)) {
                vetoed[0]++;
                continue;
            }
            if (!snapshotRecorded && !beforeFirstWrite.getAsBoolean()) {
                return new SectionEdit(cleared, this.countClearable(section), section, false);
            }
            snapshotRecorded = true;
            this.addClearedState(current);
            if (useWorldWrite && !this.replaceThroughWorld(worldX, worldY, worldZ, vetoed)) {
                continue;
            }
            if (!useWorldWrite) {
                section.setBlockState(localX, localY, localZ, this.spec.replacement(), false);
            }
            previousStates[index] = current;
            cleared++;
        }
        return new SectionEdit(cleared, vetoed[0], section, false);
    }

    private boolean replaceThroughWorld(int worldX, int worldY, int worldZ, int[] vetoed) {
        BlockPos position = new BlockPos(worldX, worldY, worldZ);
        try {
            if (this.level.setBlock(position, this.spec.replacement(), Block.UPDATE_ALL)) {
                return true;
            }
            if (this.spec.replacement().equals(this.level.getBlockState(position))) {
                return false;
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not replace the block at {} during a bulk edit", position, exception);
        }
        vetoed[0]++;
        return false;
    }

    /**
     * Replays the world-facing side effects that a palette fast path skips: stale block entities,
     * POI registration, and neighbor notifications for changes that touch an unchanged block.
     */
    private void notifySectionChanges(
        LevelChunk chunk,
        LevelChunkSection current,
        SectionKey key,
        BlockState[] previousStates
    ) {
        removeGhostBlockEntities(chunk, key.sectionY());
        Set<BlockState> poiStates = new HashSet<>();
        current.getStates().count((state, count) -> {
            if (PoiTypes.forState(state).isPresent()) {
                poiStates.add(state);
            }
        });
        for (BlockState previous : previousStates) {
            if (previous != null && PoiTypes.forState(previous).isPresent()) {
                poiStates.add(previous);
            }
        }

        int baseX = key.sectionX() << 4;
        int baseY = key.sectionY() << 4;
        int baseZ = key.sectionZ() << 4;
        for (int index = 0; index < SECTION_BLOCKS; index++) {
            BlockState previous = previousStates[index];
            if (previous == null) {
                continue;
            }
            int localX = index & 15;
            int localY = (index >> 4) & 15;
            int localZ = (index >> 8) & 15;
            BlockPos position = new BlockPos(baseX + localX, baseY + localY, baseZ + localZ);
            BlockState updated = current.getBlockState(localX, localY, localZ);
            if (poiStates.contains(previous) || poiStates.contains(updated)) {
                this.level.onBlockStateChange(position, previous, updated);
            }
            if (hasUnchangedNeighbor(previousStates, localX, localY, localZ)) {
                this.level.updateNeighborsAt(position, previous.getBlock());
            }
        }
    }

    private static boolean hasUnchangedNeighbor(
        BlockState[] previousStates,
        int localX,
        int localY,
        int localZ
    ) {
        if (localX == 0 || localX == 15) {
            return true;
        }
        if (localY == 0 || localY == 15) {
            return true;
        }
        if (localZ == 0 || localZ == 15) {
            return true;
        }
        int index = localX | (localY << 4) | (localZ << 8);
        for (int offset : NEIGHBOR_OFFSETS) {
            if (previousStates[index + offset] == null) {
                return true;
            }
        }
        return false;
    }

    private static void removeGhostBlockEntities(LevelChunk chunk, int sectionY) {
        for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            BlockPos position = blockEntity.getBlockPos();
            if (SectionPos.blockToSectionCoord(position.getY()) != sectionY) {
                continue;
            }
            if (!chunk.getBlockState(position).hasBlockEntity()) {
                chunk.removeBlockEntity(position);
            }
        }
    }

    private void addClearedState(BlockState state) {
        if (this.clearedStates.size() < MAX_CLEARED_STATES) {
            this.clearedStates.add(state);
        }
    }

    private void markChunkEdited(LevelChunk chunk, SectionKey key, boolean sectionAir) {
        if (this.level.getChunkSource().getLightEngine() != null) {
            this.level.getChunkSource().getLightEngine().updateSectionStatus(
                SectionPos.of(key.sectionX(), key.sectionY(), key.sectionZ()),
                sectionAir
            );
        }
        chunk.setUnsaved(true);
        ChunkPos chunkPos = chunk.getPos();
        this.dirtyChunks.add(chunkPos);
        if (this.spec.syncPolicy() != SyncPolicy.PROGRESSIVE) {
            TerrainEditService.queueRelight(this.level, chunkPos);
            if (this.shouldSyncNow(chunkPos)) {
                TerrainEditService.requestSync(this.level, chunkPos, true);
            }
        }
    }

    private boolean allows(int x, int y, int z, BlockState current) {
        BlockPos position = new BlockPos(x, y, z);
        return this.context.allowsBlockChange(
            new BlockChange(position, this.spec.replacement(), false),
            current
        );
    }

    private void removeBlockEntities(LevelChunk chunk, LevelChunkSection section, SectionKey key) {
        List<BlockPos> removals = new ArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockPos position = blockEntity.getBlockPos();
            if (SectionPos.blockToSectionCoord(position.getY()) != key.sectionY()) {
                continue;
            }
            int localX = position.getX() & 15;
            int localY = position.getY() & 15;
            int localZ = position.getZ() & 15;
            if (section.getBlockState(localX, localY, localZ).equals(this.spec.replacement())) {
                removals.add(position.immutable());
            }
        }
        for (BlockPos position : removals) {
            chunk.removeBlockEntity(position);
        }
    }

    private void removeAllBlockEntities(LevelChunk chunk, int sectionY) {
        List<BlockPos> removals = new ArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockPos position = blockEntity.getBlockPos();
            if (SectionPos.blockToSectionCoord(position.getY()) == sectionY) {
                removals.add(position.immutable());
            }
        }
        for (BlockPos position : removals) {
            chunk.removeBlockEntity(position);
        }
    }

    private boolean shouldSyncNow(ChunkPos chunk) {
        if (this.syncReleased || this.spec.syncPolicy() == SyncPolicy.IMMEDIATE) {
            return true;
        }
        if (this.spec.syncPolicy() == SyncPolicy.PROGRESSIVE) {
            return false;
        }
        if (this.spec.immediateSyncRadius() <= 0.0) {
            return false;
        }
        double x = chunk.getMiddleBlockX();
        double z = chunk.getMiddleBlockZ();
        double radiusSquared = this.spec.immediateSyncRadius() * this.spec.immediateSyncRadius();
        for (ServerPlayer player : this.level.players()) {
            if (player.distanceToSqr(x, player.getY(), z) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private boolean isFullyInside(SectionKey key) {
        int baseX = key.sectionX() << 4;
        int baseY = key.sectionY() << 4;
        int baseZ = key.sectionZ() << 4;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    double worldX = baseX + (x == 0 ? 0.5 : 15.5);
                    double worldY = baseY + (y == 0 ? 0.5 : 15.5);
                    double worldZ = baseZ + (z == 0 ? 0.5 : 15.5);
                    if (!this.spec.volume().contains(worldX, worldY, worldZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void rebuildDirtyState() {
        for (int index = 0; index < this.sectionIndex && index < this.sections.size(); index++) {
            SectionKey key = this.sections.get(index).key();
            this.dirtyChunks.add(new ChunkPos(key.sectionX(), key.sectionZ()));
        }
        for (ChunkPos chunk : this.dirtyChunks) {
            if (this.spec.syncPolicy() == SyncPolicy.PROGRESSIVE) {
                if (this.syncReleased) {
                    TerrainEditService.queuePriorityRelight(this.level, chunk);
                    TerrainEditService.requestSync(this.level, chunk, true);
                }
                continue;
            }
            TerrainEditService.queueRelight(this.level, chunk);
            if (this.syncReleased || this.shouldSyncNow(chunk)) {
                TerrainEditService.requestSync(this.level, chunk, true);
            }
        }
    }

    private static List<SectionEntry> buildSections(
        EffectBlockVolume volume,
        LevelHeightAccessor level,
        Ordering ordering
    ) {
        EffectBounds bounds = volume.bounds();
        int minSectionX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX()));
        int maxSectionX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX()));
        int minSectionY = Math.max(
            SectionPos.blockToSectionCoord(Mth.floor(bounds.minY())),
            level.getMinSection()
        );
        int maxSectionY = Math.min(
            SectionPos.blockToSectionCoord(Mth.floor(bounds.maxY())),
            level.getMaxSection() - 1
        );
        int minSectionZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ()));
        int maxSectionZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ()));

        if (ordering == Ordering.CENTER_OUT) {
            SectionShape shape = SectionShape.of(volume);
            PriorityQueue<SectionEntry> nearest = new PriorityQueue<>(
                Comparator.comparingDouble(SectionEntry::minDistanceSquared).reversed()
            );
            int total = 0;
            for (int sectionY = maxSectionY; sectionY >= minSectionY; sectionY--) {
                for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                    for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                        if (!volume.intersectsSection(sectionX, sectionY, sectionZ)) {
                            continue;
                        }
                        total++;
                        double dx = (sectionX * 16.0 + 8.0 - shape.centerX()) / shape.radiusX();
                        double dy = (sectionY * 16.0 + 8.0 - shape.centerY()) / shape.radiusY();
                        double dz = (sectionZ * 16.0 + 8.0 - shape.centerZ()) / shape.radiusZ();
                        nearest.add(new SectionEntry(
                            new SectionKey(sectionX, sectionY, sectionZ),
                            dx * dx + dy * dy + dz * dz,
                            minDistanceSquared(shape, sectionX, sectionY, sectionZ),
                            0
                        ));
                        if (nearest.size() > MAX_SECTIONS) {
                            nearest.poll();
                        }
                    }
                }
            }
            List<SectionEntry> sorted = new ArrayList<>(nearest);
            sorted.sort(Comparator.comparingDouble(SectionEntry::minDistanceSquared));
            if (total > MAX_SECTIONS) {
                LOGGER.warn(
                    "Bulk edit exceeds {} sections; keeping the {} nearest to the centre",
                    MAX_SECTIONS,
                    MAX_SECTIONS
                );
            }
            return withNextIndices(limitSectionsToTicketChunks(sorted));
        }

        List<SectionEntry> sections = new ArrayList<>();
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        outer:
        for (int sectionY = maxSectionY; sectionY >= minSectionY; sectionY--) {
            for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    if (!volume.intersectsSection(sectionX, sectionY, sectionZ)) {
                        continue;
                    }
                    ChunkPos chunk = new ChunkPos(sectionX, sectionZ);
                    if (!chunks.contains(chunk) && chunks.size() >= MAX_TICKET_CHUNKS) {
                        LOGGER.warn(
                            "Bulk edit exceeds {} chunk tickets; the remaining volume is skipped",
                            MAX_TICKET_CHUNKS
                        );
                        break outer;
                    }
                    chunks.add(chunk);
                    sections.add(new SectionEntry(
                        new SectionKey(sectionX, sectionY, sectionZ), 0.0, 0.0, 0
                    ));
                    if (sections.size() >= MAX_SECTIONS) {
                        LOGGER.warn(
                            "Bulk edit exceeds {} sections; the remaining volume is skipped", MAX_SECTIONS
                        );
                        break outer;
                    }
                }
            }
        }
        return withNextIndices(sections);
    }

    /**
     * Keeps the nearest sections whose chunks fit the per-edit ticket cap. A radial edit therefore
     * degrades to a smaller radius instead of leaving unticketed sections that can never be edited.
     */
    private static List<SectionEntry> limitSectionsToTicketChunks(List<SectionEntry> sorted) {
        List<SectionEntry> kept = new ArrayList<>();
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (SectionEntry entry : sorted) {
            ChunkPos chunk = chunkOf(entry.key());
            if (!chunks.contains(chunk) && chunks.size() >= MAX_TICKET_CHUNKS) {
                LOGGER.warn(
                    "Bulk edit exceeds {} chunk tickets; keeping the {} nearest sections",
                    MAX_TICKET_CHUNKS,
                    kept.size()
                );
                break;
            }
            chunks.add(chunk);
            kept.add(entry);
        }
        return kept;
    }

    /** Annotates each section with the next cursor index in the same chunk column. */
    private static List<SectionEntry> withNextIndices(List<SectionEntry> sections) {
        SectionEntry[] entries = sections.toArray(new SectionEntry[0]);
        Map<ChunkPos, Integer> nextByChunk = new HashMap<>();
        for (int index = entries.length - 1; index >= 0; index--) {
            SectionEntry entry = entries[index];
            ChunkPos chunk = chunkOf(entry.key());
            int next = nextByChunk.getOrDefault(chunk, entries.length);
            nextByChunk.put(chunk, index);
            entries[index] = new SectionEntry(
                entry.key(), entry.distanceSquared(), entry.minDistanceSquared(), next
            );
        }
        return List.of(entries);
    }

    /** Minimum normalized distance from the shape centre to a section box. */
    private static double minDistanceSquared(
        SectionShape shape,
        int sectionX,
        int sectionY,
        int sectionZ
    ) {
        double minX = sectionX * 16.0;
        double minY = sectionY * 16.0;
        double minZ = sectionZ * 16.0;
        double dx = axisMinDistance(
            (minX - shape.centerX()) / shape.radiusX(),
            (minX + 16.0 - shape.centerX()) / shape.radiusX()
        );
        double dy = axisMinDistance(
            (minY - shape.centerY()) / shape.radiusY(),
            (minY + 16.0 - shape.centerY()) / shape.radiusY()
        );
        double dz = axisMinDistance(
            (minZ - shape.centerZ()) / shape.radiusZ(),
            (minZ + 16.0 - shape.centerZ()) / shape.radiusZ()
        );
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisMinDistance(double min, double max) {
        if (max < 0.0) {
            return -max;
        }
        return min > 0.0 ? min : 0.0;
    }

    /** Test-friendly view of the ordered section cursor. */
    static List<SectionKey> orderedSections(
        EffectBlockVolume volume,
        LevelHeightAccessor level,
        Ordering ordering
    ) {
        return buildSections(volume, level, ordering).stream().map(SectionEntry::key).toList();
    }

    private static Set<ChunkPos> ticketChunks(List<SectionEntry> sections) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (SectionEntry entry : sections) {
            SectionKey key = entry.key();
            chunks.add(new ChunkPos(key.sectionX(), key.sectionZ()));
        }
        return chunks;
    }

    private record SectionEdit(
        int cleared,
        int vetoed,
        LevelChunkSection current,
        boolean fullSection
    ) {
    }

    private record SectionEntry(
        SectionKey key,
        double distanceSquared,
        double minDistanceSquared,
        int nextIndex
    ) {
    }

    private record SectionShape(
        double centerX,
        double centerY,
        double centerZ,
        double radiusX,
        double radiusY,
        double radiusZ
    ) {
        static SectionShape of(EffectBlockVolume volume) {
            if (volume instanceof EffectBlockVolume.Ellipsoid ellipsoid) {
                return new SectionShape(
                    ellipsoid.center().x(),
                    ellipsoid.center().y(),
                    ellipsoid.center().z(),
                    ellipsoid.radiusX(),
                    ellipsoid.radiusY(),
                    ellipsoid.radiusZ()
                );
            }
            if (volume instanceof EffectBlockVolume.Sphere sphere) {
                double radius = sphere.radius();
                return new SectionShape(
                    sphere.center().x(),
                    sphere.center().y(),
                    sphere.center().z(),
                    radius,
                    radius,
                    radius
                );
            }
            EffectBounds bounds = volume.bounds();
            return new SectionShape(
                (bounds.minX() + bounds.maxX()) * 0.5,
                (bounds.minY() + bounds.maxY()) * 0.5,
                (bounds.minZ() + bounds.maxZ()) * 0.5,
                Math.max(0.5, (bounds.maxX() - bounds.minX()) * 0.5),
                Math.max(0.5, (bounds.maxY() - bounds.minY()) * 0.5),
                Math.max(0.5, (bounds.maxZ() - bounds.minZ()) * 0.5)
            );
        }
    }

    private static void writeVolume(CompoundTag tag, EffectBlockVolume volume) {
        if (volume instanceof EffectBlockVolume.Ellipsoid ellipsoid) {
            tag.putString("volume", "ellipsoid");
            writePoint(tag, ellipsoid.center());
            tag.putDouble("rx", ellipsoid.radiusX());
            tag.putDouble("ry", ellipsoid.radiusY());
            tag.putDouble("rz", ellipsoid.radiusZ());
        } else if (volume instanceof EffectBlockVolume.Sphere sphere) {
            tag.putString("volume", "sphere");
            writePoint(tag, sphere.center());
            tag.putDouble("radius", sphere.radius());
        } else if (volume instanceof EffectBlockVolume.Cylinder cylinder) {
            tag.putString("volume", "cylinder");
            writePoint(tag, cylinder.center());
            tag.putDouble("radius", cylinder.radius());
            tag.putDouble("min_y", cylinder.minY());
            tag.putDouble("max_y", cylinder.maxY());
        } else if (volume instanceof EffectBlockVolume.Box box) {
            tag.putString("volume", "box");
            EffectBounds bounds = box.bounds();
            tag.putDouble("min_x", bounds.minX());
            tag.putDouble("min_y", bounds.minY());
            tag.putDouble("min_z", bounds.minZ());
            tag.putDouble("max_x", bounds.maxX());
            tag.putDouble("max_y", bounds.maxY());
            tag.putDouble("max_z", bounds.maxZ());
        } else {
            throw new IllegalArgumentException("Unsupported bulk edit volume: " + volume);
        }
    }

    private static Spec readSpec(CompoundTag tag) {
        String type = tag.getString("volume");
        EffectBlockVolume volume = switch (type) {
            case "ellipsoid" -> EffectBlockVolume.ellipsoid(
                readPoint(tag), tag.getDouble("rx"), tag.getDouble("ry"), tag.getDouble("rz")
            );
            case "sphere" -> EffectBlockVolume.sphere(readPoint(tag), tag.getDouble("radius"));
            case "cylinder" -> EffectBlockVolume.cylinder(
                readPoint(tag), tag.getDouble("radius"), tag.getDouble("min_y"), tag.getDouble("max_y")
            );
            case "box" -> EffectBlockVolume.box(new EffectBounds(
                tag.getDouble("min_x"),
                tag.getDouble("min_y"),
                tag.getDouble("min_z"),
                tag.getDouble("max_x"),
                tag.getDouble("max_y"),
                tag.getDouble("max_z")
            ));
            default -> throw new IllegalArgumentException("Unknown bulk edit volume type: " + type);
        };
        BlockState replacement = NbtUtils.readBlockState(
            BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("replacement")
        );
        return new Spec(
            volume,
            replacement,
            !tag.contains("remove_block_entities") || tag.getBoolean("remove_block_entities"),
            SyncPolicy.valueOf(tag.getString("sync_policy")),
            tag.getDouble("immediate_sync_radius"),
            tag.getBoolean("journal")
        );
    }

    private static void writePoint(CompoundTag tag, mafisher.quasar.api.effect.EffectPoint point) {
        tag.putDouble("x", point.x());
        tag.putDouble("y", point.y());
        tag.putDouble("z", point.z());
    }

    private static mafisher.quasar.api.effect.EffectPoint readPoint(CompoundTag tag) {
        return new mafisher.quasar.api.effect.EffectPoint(
            tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")
        );
    }

    record SectionKey(int sectionX, int sectionY, int sectionZ) {
    }
}
