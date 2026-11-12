package mafisher.quasar.api.gameplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Budgeted, server-thread world sampling for one gameplay effect session. Queries never load or
 * generate chunks: unloaded or out-of-bounds positions produce an empty result, and a ray that
 * enters an unloaded chunk stops with {@link EffectRaycastStatus#UNLOADED}.
 *
 * <p>Every height lookup, block read, scanned block, and raycast voxel consumes one sample from
 * {@link EffectTickBudget#maxWorldSamples()}. Budget exhaustion is a soft failure: point queries
 * return empty, a ray stops with {@link EffectRaycastStatus#BUDGET_EXHAUSTED}, and a scan reports
 * {@code truncated}. Obtain the query through {@link GameplayEffectContext#world()}; it is only
 * valid while the session is open.
 */
public final class EffectWorldQuery {
    /** Hard safety ceiling for one raycast; longer requests return {@link EffectRaycastStatus#TOO_LONG}. */
    public static final double MAX_RAYCAST_DISTANCE = 1024.0;

    /** Hard safety ceiling for one scan region; larger regions are rejected as authoring errors. */
    public static final int MAX_SCAN_BLOCKS = 4096;

    private final ServerLevel level;
    private final EffectTickBudgetState budget;
    private final Runnable openGuard;

    EffectWorldQuery(ServerLevel level, EffectTickBudgetState budget, Runnable openGuard) {
        this.level = Objects.requireNonNull(level, "level");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.openGuard = Objects.requireNonNull(openGuard, "openGuard");
    }

    /**
     * Returns the heightmap value at the column, or empty when the chunk is unloaded or the sample
     * budget is exhausted. Does not load the chunk.
     */
    public OptionalInt height(Heightmap.Types type, int x, int z) {
        this.openGuard.run();
        Objects.requireNonNull(type, "type");
        if (!this.level.hasChunkAt(x, z) || !this.budget.trySampleWorld()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(this.level.getHeight(type, x, z));
    }

    /**
     * Returns the block state at the position, or empty when the position is outside the level,
     * its chunk is unloaded, or the sample budget is exhausted. Does not load the chunk.
     */
    public Optional<BlockState> blockState(BlockPos position) {
        this.openGuard.run();
        Objects.requireNonNull(position, "position");
        if (!this.level.isInWorldBounds(position)
            || !this.level.hasChunkAt(position)
            || !this.budget.trySampleWorld()) {
            return Optional.empty();
        }
        return Optional.of(this.level.getBlockState(position));
    }

    /**
     * Creates a resumable block cursor for a volume. The cursor never loads chunks, skips
     * all-air sections, and charges the same world-sample budget as point queries.
     */
    public EffectBlockCursor cursor(EffectBlockVolume volume) {
        this.openGuard.run();
        Objects.requireNonNull(volume, "volume");
        return new EffectBlockCursor(this.level, this.budget, this.openGuard, volume);
    }

    EffectBlockCursor newCursor(EffectBlockVolume volume) {
        return new EffectBlockCursor(this.level, this.budget, this.openGuard, volume);
    }

    /** Casts a ray that stops at the first non-air block with a colliding shape. */
    public EffectRaycastResult raycast(Vec3 from, Vec3 to) {
        return this.raycast(from, to, state -> !state.isAir());
    }

    /**
     * Casts a ray that stops at the first block accepted by {@code stopFilter} and intersected by
     * its collision shape. The filter is not called for blocks beyond an unloaded chunk boundary or
     * the sample budget.
     */
    public EffectRaycastResult raycast(Vec3 from, Vec3 to, Predicate<BlockState> stopFilter) {
        this.openGuard.run();
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(stopFilter, "stopFilter");
        requireFinite(from, "from");
        requireFinite(to, "to");
        if (from.distanceToSqr(to) > MAX_RAYCAST_DISTANCE * MAX_RAYCAST_DISTANCE) {
            return new EffectRaycastResult(EffectRaycastStatus.TOO_LONG, Optional.empty());
        }

        TraceOutcome outcome = BlockGetter.traverseBlocks(
            from,
            to,
            this.level,
            (ignored, position) -> this.traceVoxel(from, to, position, stopFilter),
            ignored -> new TraceOutcome(EffectRaycastStatus.MISS, null)
        );
        return new EffectRaycastResult(
            outcome.status(),
            outcome.hit() == null ? Optional.empty() : Optional.of(outcome.hit())
        );
    }

    /** Scans a closed block range for non-air matches, returning at most {@code maxResults}. */
    public EffectScanResult scan(BlockPos min, BlockPos max, int maxResults) {
        return this.scan(min, max, state -> !state.isAir(), maxResults);
    }

    /**
     * Scans a closed block range for blocks accepted by {@code filter}. Each visited block consumes
     * one world sample. Unloaded chunks are skipped. The scan stops with {@code truncated} when the
     * sample budget runs out or {@code maxResults} matches have been collected.
     *
     * @throws IllegalArgumentException when the range is inverted, larger than
     *     {@link #MAX_SCAN_BLOCKS}, or {@code maxResults} is below one
     */
    public EffectScanResult scan(BlockPos min, BlockPos max, Predicate<BlockState> filter, int maxResults) {
        this.openGuard.run();
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        Objects.requireNonNull(filter, "filter");
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be at least one");
        }
        long sizeX = (long) max.getX() - min.getX() + 1L;
        long sizeY = (long) max.getY() - min.getY() + 1L;
        long sizeZ = (long) max.getZ() - min.getZ() + 1L;
        if (sizeX <= 0L || sizeY <= 0L || sizeZ <= 0L) {
            throw new IllegalArgumentException("Scan bounds must be ordered min <= max");
        }
        if (sizeX > MAX_SCAN_BLOCKS || sizeY > MAX_SCAN_BLOCKS || sizeZ > MAX_SCAN_BLOCKS) {
            throw new IllegalArgumentException(
                "Scan range exceeds the limit of " + MAX_SCAN_BLOCKS + " blocks"
            );
        }
        long volume = sizeX * sizeY * sizeZ;
        if (volume > MAX_SCAN_BLOCKS) {
            throw new IllegalArgumentException(
                "Scan range covers " + volume + " blocks; the limit is " + MAX_SCAN_BLOCKS
            );
        }

        List<EffectBlockSample> samples = new ArrayList<>();
        int examined = 0;
        boolean truncated = false;
        for (BlockPos position : BlockPos.betweenClosed(min, max)) {
            if (!this.budget.trySampleWorld()) {
                truncated = true;
                break;
            }
            examined++;
            if (!this.level.isInWorldBounds(position) || !this.level.hasChunkAt(position)) {
                continue;
            }
            BlockState state = this.level.getBlockState(position);
            if (!filter.test(state)) {
                continue;
            }
            samples.add(new EffectBlockSample(position, state));
            if (samples.size() >= maxResults) {
                truncated = true;
                break;
            }
        }
        return new EffectScanResult(samples, examined, truncated);
    }

    private TraceOutcome traceVoxel(
        Vec3 from,
        Vec3 to,
        BlockPos position,
        Predicate<BlockState> stopFilter
    ) {
        if (!this.budget.trySampleWorld()) {
            return new TraceOutcome(EffectRaycastStatus.BUDGET_EXHAUSTED, null);
        }
        BlockPos immutable = position.immutable();
        if (!this.level.isInWorldBounds(immutable) || !this.level.hasChunkAt(immutable)) {
            return new TraceOutcome(EffectRaycastStatus.UNLOADED, null);
        }
        BlockState state = this.level.getBlockState(immutable);
        if (!stopFilter.test(state)) {
            return null;
        }
        BlockHitResult clipped = state.getShape(this.level, immutable).clip(from, to, immutable);
        if (clipped == null || clipped.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        EffectBlockHit hit = new EffectBlockHit(
            clipped.getBlockPos(),
            this.level.getBlockState(clipped.getBlockPos()),
            clipped.getLocation(),
            clipped.getDirection(),
            clipped.getLocation().distanceTo(from)
        );
        return new TraceOutcome(EffectRaycastStatus.HIT, hit);
    }

    private static void requireFinite(Vec3 value, String name) {
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private record TraceOutcome(EffectRaycastStatus status, EffectBlockHit hit) {
    }
}
