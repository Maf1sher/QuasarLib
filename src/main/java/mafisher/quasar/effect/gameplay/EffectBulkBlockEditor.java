package mafisher.quasar.effect.gameplay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.effect.EffectBounds;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.block.BlockProtectionContext;
import mafisher.quasar.effect.block.BlockProtectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Budgeted, resumable bulk terrain editor for one gameplay session. Whole 16x16x16 sections are
 * cleared through the chunk-section palette instead of per-block level writes; partial boundary
 * sections fall back to per-block edits so the surface stays smooth. Heightmaps, lighting, block
 * entities, chunk tickets, and one full-chunk client resend per changed column are handled by the
 * engine.
 *
 * <p>Server authority is unchanged: protection is queried before each change and the block journal
 * records a compact section snapshot for rollback. Clients only present the final chunk.
 */
public final class EffectBulkBlockEditor {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    private static final int MAX_CLEARED_STATES = 12;
    private static final int MAX_TICKET_CHUNKS = 1_024;
    private static final int MAX_SECTIONS = 8_192;
    private static final int MAX_SECTIONS_PER_TICK = 8;
    private static final int MAX_STALLED_TICKS = 40;
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
        MANUAL
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
    private final List<SectionKey> sections;
    private final Set<ChunkPos> ticketChunks;
    private final Set<ChunkPos> dirtyChunks = new LinkedHashSet<>();
    private final Set<BlockState> clearedStates = new LinkedHashSet<>();
    private int sectionIndex;
    private int stalledTicks;
    private long clearedBlocks;
    private boolean syncReleased;
    private boolean finished;
    private boolean closed;

    private EffectBulkBlockEditor(GameplayEffectContext context, Spec spec) {
        this.context = Objects.requireNonNull(context, "context");
        this.level = context.level();
        this.spec = Objects.requireNonNull(spec, "spec");
        this.sections = buildSections(spec.volume(), this.level);
        this.ticketChunks = ticketChunks(spec.volume());
        TerrainEditService.acquireTickets(this.level, this.ticketChunks);
    }

    public static EffectBulkBlockEditor create(GameplayEffectContext context, Spec spec) {
        return new EffectBulkBlockEditor(context, spec);
    }

    /** Restores one editor from its persisted cursor tag. */
    public static EffectBulkBlockEditor restore(GameplayEffectContext context, CompoundTag tag) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tag, "tag");
        Spec spec = readSpec(tag);
        EffectBulkBlockEditor editor = new EffectBulkBlockEditor(context, spec);
        editor.sectionIndex = Math.max(0, Math.min(editor.sections.size(), tag.getInt("section_index")));
        editor.clearedBlocks = Math.max(0L, tag.getLong("cleared_blocks"));
        editor.syncReleased = tag.getBoolean("sync_released");
        editor.finished = tag.getBoolean("finished") || editor.sectionIndex >= editor.sections.size();
        editor.rebuildDirtyState();
        return editor;
    }

    /** Advances the edit within the session and level-wide section budgets. */
    public Progress tick() {
        if (this.closed || this.finished) {
            return this.progress();
        }
        int edited = 0;
        while (this.sectionIndex < this.sections.size() && edited < MAX_SECTIONS_PER_TICK) {
            SectionKey key = this.sections.get(this.sectionIndex);
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(key.sectionX(), key.sectionZ());
            if (chunk == null) {
                this.stalledTicks++;
                if (this.stalledTicks > MAX_STALLED_TICKS) {
                    LOGGER.warn(
                        "Skipping bulk edit section {} {} {} because its chunk never loaded",
                        key.sectionX(), key.sectionY(), key.sectionZ()
                    );
                    this.sectionIndex++;
                    this.stalledTicks = 0;
                    continue;
                }
                break;
            }
            int sectionArrayIndex = this.level.getSectionIndexFromSectionY(key.sectionY());
            LevelChunkSection section = chunk.getSection(sectionArrayIndex);
            if (section == null
                || section.hasOnlyAir()
                || !section.maybeHas(state -> !state.isAir()
                    && !INDESTRUCTIBLE.contains(state.getBlock()))) {
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
            edited++;
        }
        if (this.sectionIndex >= this.sections.size()) {
            this.finished = true;
            if (this.spec.syncPolicy() == SyncPolicy.ON_COMPLETE) {
                this.releaseSync();
            }
        }
        return this.progress();
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
        TerrainEditService.releaseTickets(this.level, this.ticketChunks);
        this.closed = true;
    }

    public Progress progress() {
        return new Progress(
            this.sections.size(), Math.min(this.sectionIndex, this.sections.size()), this.clearedBlocks, this.finished
        );
    }

    /** Up to twelve distinct non-air states sampled from the cleared volume, for debris palettes. */
    public List<BlockState> clearedStates() {
        return List.copyOf(this.clearedStates);
    }

    /** Persists the cursor and shape for session restore. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        writeVolume(tag, this.spec.volume());
        tag.put("replacement", NbtUtils.writeBlockState(this.spec.replacement()));
        tag.putBoolean("remove_block_entities", this.spec.removeBlockEntities());
        tag.putString("sync_policy", this.spec.syncPolicy().name());
        tag.putDouble("immediate_sync_radius", this.spec.immediateSyncRadius());
        tag.putBoolean("journal", this.spec.journal());
        tag.putInt("section_index", this.sectionIndex);
        tag.putLong("cleared_blocks", this.clearedBlocks);
        tag.putBoolean("sync_released", this.syncReleased);
        tag.putBoolean("finished", this.finished);
        return tag;
    }

    private void editSection(LevelChunk chunk, LevelChunkSection section, SectionKey key) {
        if (this.spec.journal()) {
            this.context.blockJournal().recordSection(this.level, chunk, key.sectionY(), section);
        }

        boolean fullyInside = this.isFullyInside(key);
        boolean protectedWorld = BlockProtectionService.hasProtections();
        int cleared = 0;
        int vetoed = 0;
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
            if (!fullyInside && !this.spec.volume().contains(
                worldX + 0.5, worldY + 0.5, worldZ + 0.5
            )) {
                continue;
            }
            BlockState current = section.getBlockState(localX, localY, localZ);
            if (current.isAir()
                || current.equals(this.spec.replacement())
                || INDESTRUCTIBLE.contains(current.getBlock())) {
                continue;
            }
            if (protectedWorld && !this.allows(worldX, worldY, worldZ, current)) {
                vetoed++;
                continue;
            }
            if (this.clearedStates.size() < MAX_CLEARED_STATES) {
                this.clearedStates.add(current);
            }
            section.setBlockState(localX, localY, localZ, this.spec.replacement(), false);
            cleared++;
        }

        if (cleared == 0 && vetoed == 0) {
            return;
        }
        this.context.recordBulkSection(cleared, vetoed);
        this.clearedBlocks += cleared;
        if (cleared == 0) {
            return;
        }

        if (this.spec.removeBlockEntities()) {
            removeBlockEntities(chunk, section, key);
        }
        if (this.level.getChunkSource().getLightEngine() != null) {
            this.level.getChunkSource().getLightEngine().updateSectionStatus(
                SectionPos.of(key.sectionX(), key.sectionY(), key.sectionZ()),
                section.hasOnlyAir()
            );
        }
        chunk.setUnsaved(true);
        ChunkPos chunkPos = chunk.getPos();
        this.dirtyChunks.add(chunkPos);
        TerrainEditService.queueRelight(this.level, chunkPos);
        if (this.shouldSyncNow(chunkPos)) {
            TerrainEditService.requestSync(this.level, chunkPos, true);
        }
    }

    private boolean allows(int x, int y, int z, BlockState current) {
        BlockPos position = new BlockPos(x, y, z);
        return BlockProtectionService.allows(new BlockProtectionContext(
            this.level,
            new BlockChange(position, this.spec.replacement(), false),
            current,
            this.context.spec().typeId(),
            this.context.sessionId(),
            this.context.spec().ownerId()
        ));
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

    private boolean shouldSyncNow(ChunkPos chunk) {
        if (this.syncReleased || this.spec.syncPolicy() == SyncPolicy.IMMEDIATE) {
            return true;
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
            SectionKey key = this.sections.get(index);
            this.dirtyChunks.add(new ChunkPos(key.sectionX(), key.sectionZ()));
        }
        for (ChunkPos chunk : this.dirtyChunks) {
            TerrainEditService.queueRelight(this.level, chunk);
            if (this.syncReleased || this.shouldSyncNow(chunk)) {
                TerrainEditService.requestSync(this.level, chunk, true);
            }
        }
    }

    private static List<SectionKey> buildSections(
        EffectBlockVolume volume,
        LevelHeightAccessor level
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
        List<SectionKey> sections = new ArrayList<>();
        outer:
        for (int sectionY = maxSectionY; sectionY >= minSectionY; sectionY--) {
            for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    if (volume.intersectsSection(sectionX, sectionY, sectionZ)) {
                        sections.add(new SectionKey(sectionX, sectionY, sectionZ));
                        if (sections.size() >= MAX_SECTIONS) {
                            LOGGER.warn(
                                "Bulk edit exceeds {} sections; the remaining volume is skipped", MAX_SECTIONS
                            );
                            break outer;
                        }
                    }
                }
            }
        }
        return sections;
    }

    private static Set<ChunkPos> ticketChunks(EffectBlockVolume volume) {
        EffectBounds bounds = volume.bounds();
        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX()));
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX()));
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ()));
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ()));
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        outer:
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
                if (chunks.size() >= MAX_TICKET_CHUNKS) {
                    LOGGER.warn("Bulk edit area exceeds {} chunk tickets; some chunks may stay unloaded", MAX_TICKET_CHUNKS);
                    break outer;
                }
            }
        }
        return chunks;
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

    private static void writePoint(CompoundTag tag, mafisher.quasar.effect.EffectPoint point) {
        tag.putDouble("x", point.x());
        tag.putDouble("y", point.y());
        tag.putDouble("z", point.z());
    }

    private static mafisher.quasar.effect.EffectPoint readPoint(CompoundTag tag) {
        return new mafisher.quasar.effect.EffectPoint(
            tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")
        );
    }

    private record SectionKey(int sectionX, int sectionY, int sectionZ) {
    }
}
