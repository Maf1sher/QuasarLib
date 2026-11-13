package mafisher.quasar.api.gameplay.world;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.gameplay.tick.EffectTickBudgetState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Budgeted, resumable block iterator for one gameplay session. The cursor walks a block volume by
 * chunk section, skips all-air sections, never loads a chunk, and consumes the session's world
 * sample budget. It is deterministic and can be saved mid-section, so large volume operations such
 * as a black-hole core carve survive ticks and restarts without materializing every position.
 */
public final class EffectBlockCursor {
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    private static final int SECTION_MASK = 15;
    private static final String STATE_KEY = "block_cursor";

    private final ServerLevel level;
    private final EffectTickBudgetState budget;
    private final Runnable openGuard;
    private final EffectBlockVolume volume;
    private final int minSectionX;
    private final int minSectionY;
    private final int minSectionZ;
    private final int maxSectionX;
    private final int maxSectionY;
    private final int maxSectionZ;

    private int sectionX;
    private int sectionY;
    private int sectionZ;
    private int sectionIndex;
    private boolean finished;

    EffectBlockCursor(
        ServerLevel level,
        EffectTickBudgetState budget,
        Runnable openGuard,
        EffectBlockVolume volume
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.openGuard = Objects.requireNonNull(openGuard, "openGuard");
        this.volume = Objects.requireNonNull(volume, "volume");
        EffectBounds bounds = volume.bounds();
        int minX = (int)Math.floor(bounds.minX());
        int minY = (int)Math.floor(bounds.minY());
        int minZ = (int)Math.floor(bounds.minZ());
        int maxX = (int)Math.floor(bounds.maxX());
        int maxY = (int)Math.floor(bounds.maxY());
        int maxZ = (int)Math.floor(bounds.maxZ());
        this.minSectionX = SectionPos.blockToSectionCoord(minX);
        this.minSectionY = Math.max(
            SectionPos.blockToSectionCoord(minY),
            this.level.getMinSection()
        );
        this.minSectionZ = SectionPos.blockToSectionCoord(minZ);
        this.maxSectionX = SectionPos.blockToSectionCoord(maxX);
        this.maxSectionY = Math.min(
            SectionPos.blockToSectionCoord(maxY),
            this.level.getMaxSection() - 1
        );
        this.maxSectionZ = SectionPos.blockToSectionCoord(maxZ);
        this.resetToStart();
    }

    /** Returns the immutable volume this cursor walks. */
    public EffectBlockVolume volume() {
        return this.volume;
    }

    /** Whether the cursor has visited every candidate block. */
    public boolean finished() {
        return this.finished;
    }

    /**
     * Visits up to {@code maxBlocks} blocks inside the volume. Each examined block consumes one
     * world sample; probing a new section also consumes one sample. The visitor is called only for
     * blocks accepted by {@code filter}. The cursor pauses rather than loading an unloaded chunk.
     */
    public EffectBlockCursorResult visit(
        int maxBlocks,
        Predicate<BlockState> filter,
        BiConsumer<BlockPos, BlockState> visitor
    ) {
        this.openGuard.run();
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(visitor, "visitor");
        if (maxBlocks < 0) {
            throw new IllegalArgumentException("Block cursor batch size cannot be negative");
        }
        if (this.finished || maxBlocks == 0) {
            return new EffectBlockCursorResult(0, 0, maxBlocks == 0 && !this.finished, this.finished, false);
        }

        int examined = 0;
        int matched = 0;
        while (!this.finished) {
            if (!this.isSectionInBounds()) {
                this.advanceSection();
                continue;
            }
            if (!this.volume.intersectsSection(this.sectionX, this.sectionY, this.sectionZ)) {
                this.advanceSection();
                continue;
            }
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(this.sectionX, this.sectionZ);
            if (chunk == null) {
                return new EffectBlockCursorResult(examined, matched, true, false, true);
            }
            if (!this.budget.trySampleWorld()) {
                return new EffectBlockCursorResult(examined, matched, true, false, false);
            }
            int sectionYIndex = this.level.getSectionIndexFromSectionY(this.sectionY);
            LevelChunkSection section = chunk.getSection(sectionYIndex);
            if (section == null || section.hasOnlyAir()) {
                this.advanceSection();
                continue;
            }

            int sectionBaseX = this.sectionX << 4;
            int sectionBaseY = this.sectionY << 4;
            int sectionBaseZ = this.sectionZ << 4;
            while (this.sectionIndex < SECTION_BLOCKS) {
                if (examined >= maxBlocks) {
                    return new EffectBlockCursorResult(examined, matched, true, false, false);
                }
                int localX = this.sectionIndex & SECTION_MASK;
                int localY = (this.sectionIndex >> 4) & SECTION_MASK;
                int localZ = (this.sectionIndex >> 8) & SECTION_MASK;
                int x = sectionBaseX + localX;
                int y = sectionBaseY + localY;
                int z = sectionBaseZ + localZ;
                this.sectionIndex++;
                if (!this.volume.contains(x + 0.5, y + 0.5, z + 0.5)) {
                    continue;
                }
                if (!this.budget.trySampleWorld()) {
                    return new EffectBlockCursorResult(examined, matched, true, false, false);
                }
                examined++;
                BlockState state = section.getBlockState(localX, localY, localZ);
                if (!filter.test(state)) {
                    continue;
                }
                matched++;
                visitor.accept(new BlockPos(x, y, z), state);
            }
            this.advanceSection();
        }
        return new EffectBlockCursorResult(examined, matched, false, true, false);
    }

    /** Saves the exact section and local block position so a resumed session continues there. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("sx", this.sectionX);
        tag.putInt("sy", this.sectionY);
        tag.putInt("sz", this.sectionZ);
        tag.putInt("i", this.sectionIndex);
        tag.putBoolean("done", this.finished);
        return tag;
    }

    /** Restores a cursor previously saved by {@link #save()}. */
    public static EffectBlockCursor restore(
        EffectWorldQuery query,
        EffectBlockVolume volume,
        CompoundTag state
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(state, "state");
        EffectBlockCursor cursor = query.newCursor(volume);
        cursor.load(state);
        return cursor;
    }

    /** Returns the state payload key used by content when this cursor lives in a larger tag. */
    public static String stateKey() {
        return STATE_KEY;
    }

    private void load(CompoundTag tag) {
        if (tag.isEmpty()) {
            return;
        }
        this.sectionX = tag.getInt("sx");
        this.sectionY = tag.getInt("sy");
        this.sectionZ = tag.getInt("sz");
        this.sectionIndex = Math.max(0, Math.min(SECTION_BLOCKS, tag.getInt("i")));
        this.finished = tag.getBoolean("done");
        if (!this.finished && !this.isSectionInBounds()) {
            this.finished = true;
        }
    }

    private void resetToStart() {
        this.sectionX = this.minSectionX;
        this.sectionY = this.minSectionY;
        this.sectionZ = this.minSectionZ;
        this.sectionIndex = 0;
        this.finished = this.minSectionY > this.maxSectionY
            || this.minSectionX > this.maxSectionX
            || this.minSectionZ > this.maxSectionZ;
    }

    private boolean isSectionInBounds() {
        return this.sectionX >= this.minSectionX && this.sectionX <= this.maxSectionX
            && this.sectionY >= this.minSectionY && this.sectionY <= this.maxSectionY
            && this.sectionZ >= this.minSectionZ && this.sectionZ <= this.maxSectionZ;
    }

    private void advanceSection() {
        this.sectionIndex = 0;
        this.sectionX++;
        if (this.sectionX > this.maxSectionX) {
            this.sectionX = this.minSectionX;
            this.sectionZ++;
            if (this.sectionZ > this.maxSectionZ) {
                this.sectionZ = this.minSectionZ;
                this.sectionY++;
            }
        }
        if (!this.isSectionInBounds()) {
            this.finished = true;
        }
    }
}
