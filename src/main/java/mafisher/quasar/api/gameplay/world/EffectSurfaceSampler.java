package mafisher.quasar.api.gameplay.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Session-scoped surface sampling with status codes, a bounded per-tick cache, and edit
 * invalidation.
 *
 * <p>The sampler reads the heightmap and the block below it through {@link EffectWorldQuery};
 * it never loads or generates a chunk. A miss costs one height sample and one block sample; a
 * cache hit costs none. Cached columns are dropped when the session tick changes or after any
 * applied block edit, so a carve or tear cannot leave debris reading a stale surface.</p>
 *
 * <p>One sampler belongs to one gameplay session. It is server-thread only and never persisted;
 * a resumed session builds a fresh sampler.</p>
 */
public final class EffectSurfaceSampler {
    /** Hard cap for one sampler's column cache. */
    public static final int MAX_CACHED_COLUMNS = 2_048;
    /** Hard cap for one area request. */
    public static final int MAX_AREA_COLUMNS = 1_024;
    /** Hard cap for the area radius; larger requests are authoring errors. */
    public static final int MAX_AREA_RADIUS = 128;

    /** Why a column has or has not been sampled. */
    public enum Status {
        /** The column is loaded and the height, surface, and fluid fields are valid. */
        LOADED,
        /** The chunk is not loaded; no samples were consumed and no chunk was loaded. */
        UNLOADED,
        /** The per-tick world-sample budget ran out before the column was complete. */
        BUDGET_EXHAUSTED
    }

    /**
     * One sampled column.
     *
     * <p>{@code height} is the first free Y, the same value {@code Level.getHeight} returns.
     * {@code surface} is the block at {@code height - 1}, and {@code fluid} is that block's fluid
     * state. Both are null for an empty column and for any status other than {@link Status#LOADED};
     * check {@link #loaded()} first.</p>
     */
    public record Column(
        int x,
        int z,
        Status status,
        int height,
        @Nullable BlockState surface,
        @Nullable FluidState fluid
    ) {
        public Column {
            Objects.requireNonNull(status, "status");
            if (status == Status.LOADED && (surface == null) != (fluid == null)) {
                throw new IllegalArgumentException(
                    "A loaded column must provide both surface and fluid, or neither"
                );
            }
            if (status != Status.LOADED && (surface != null || fluid != null)) {
                throw new IllegalArgumentException(
                    "An unavailable column cannot carry surface or fluid"
                );
            }
        }

        public boolean loaded() {
            return this.status == Status.LOADED;
        }

        /** The block Y immediately below {@link #height()}. */
        public int surfaceY() {
            return this.height - 1;
        }
    }

    /** A bounded, deterministic area sample. */
    public record Area(List<Column> columns, boolean truncated) {
        public Area {
            columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        }
    }

    private final GameplayEffectContext context;
    private final Map<ColumnKey, Column> cache = new ColumnCache();
    private int cachedTick;
    private long cachedEditVersion;
    private boolean primed;

    public EffectSurfaceSampler(GameplayEffectContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /**
     * Samples one column. Returns {@link Status#UNLOADED} without consuming a sample when the
     * chunk is absent, and {@link Status#BUDGET_EXHAUSTED} when the world-sample budget is gone.
     */
    public Column sampleColumn(int x, int z, Heightmap.Types type) {
        Objects.requireNonNull(type, "type");
        this.refresh();
        ColumnKey key = new ColumnKey(x, z, type);
        Column cached = this.cache.get(key);
        if (cached != null) {
            return cached;
        }
        Column column = this.readColumn(x, z, type);
        if (column.loaded()) {
            this.cache.put(key, column);
        }
        return column;
    }

    /**
     * Samples a square area from the centre outwards. Columns are visited once, in a deterministic
     * order; the result stops at {@code maxColumns} or when the world-sample budget is exhausted.
     * {@link Area#truncated()} is true when either limit ended the scan early.
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    public Area sampleArea(
        int centerX,
        int centerZ,
        int radius,
        Heightmap.Types type,
        int maxColumns
    ) {
        Objects.requireNonNull(type, "type");
        if (radius < 0 || radius > MAX_AREA_RADIUS) {
            throw new IllegalArgumentException(
                "Area radius must be between 0 and " + MAX_AREA_RADIUS
            );
        }
        if (maxColumns < 1 || maxColumns > MAX_AREA_COLUMNS) {
            throw new IllegalArgumentException(
                "maxColumns must be between 1 and " + MAX_AREA_COLUMNS
            );
        }

        int capacity = Math.min(maxColumns, (2 * radius + 1) * (2 * radius + 1));
        List<Column> columns = new ArrayList<>(capacity);
        boolean truncated = false;
        for (int ring = 0; ring <= radius && !truncated; ring++) {
            for (int dx = -ring; dx <= ring && !truncated; dx++) {
                truncated = !this.addAreaColumn(
                    columns, centerX + dx, centerZ - ring, type, maxColumns
                );
                if (!truncated && ring > 0) {
                    truncated = !this.addAreaColumn(
                        columns, centerX + dx, centerZ + ring, type, maxColumns
                    );
                }
            }
            for (int dz = -ring + 1; dz < ring && !truncated; dz++) {
                truncated = !this.addAreaColumn(
                    columns, centerX - ring, centerZ + dz, type, maxColumns
                );
                if (!truncated) {
                    truncated = !this.addAreaColumn(
                        columns, centerX + ring, centerZ + dz, type, maxColumns
                    );
                }
            }
        }
        return new Area(columns, truncated);
    }

    /** Drops cached columns whose X/Z fall inside the closed range. */
    public void invalidate(int minX, int minZ, int maxX, int maxZ) {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Invalidation bounds must be ordered min <= max");
        }
        this.cache.entrySet().removeIf(
            entry -> entry.getKey().inside(minX, minZ, maxX, maxZ)
        );
    }

    /** Drops cached columns touched by the bounds. */
    public void invalidate(EffectBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        this.invalidate(
            Mth.floor(bounds.minX()),
            Mth.floor(bounds.minZ()),
            Mth.floor(bounds.maxX()),
            Mth.floor(bounds.maxZ())
        );
    }

    /** Drops every cached column. */
    public void invalidateAll() {
        this.cache.clear();
    }

    /** Current number of cached columns; bounded by {@link #MAX_CACHED_COLUMNS}. */
    public int cachedColumns() {
        return this.cache.size();
    }

    private boolean addAreaColumn(
        List<Column> columns,
        int x,
        int z,
        Heightmap.Types type,
        int maxColumns
    ) {
        if (columns.size() >= maxColumns) {
            return false;
        }
        Column column = this.sampleColumn(x, z, type);
        if (column.status() == Status.BUDGET_EXHAUSTED) {
            return false;
        }
        columns.add(column);
        return true;
    }

    private Column readColumn(int x, int z, Heightmap.Types type) {
        OptionalInt height = this.context.world().height(type, x, z);
        int minY = this.context.level().getMinBuildHeight();
        if (height.isEmpty()) {
            Status status = this.context.level().hasChunkAt(x, z)
                ? Status.BUDGET_EXHAUSTED
                : Status.UNLOADED;
            return new Column(x, z, status, minY, null, null);
        }

        int top = height.getAsInt();
        int surfaceY = top - 1;
        if (surfaceY < minY) {
            return new Column(x, z, Status.LOADED, top, null, null);
        }
        Optional<BlockState> surface = this.context.world().blockState(new BlockPos(x, surfaceY, z));
        if (surface.isEmpty()) {
            return new Column(x, z, Status.BUDGET_EXHAUSTED, minY, null, null);
        }
        BlockState state = surface.get();
        return new Column(x, z, Status.LOADED, top, state, state.getFluidState());
    }

    private void refresh() {
        int tick = this.context.ageTicks();
        long editVersion = this.context.blockEditVersion();
        if (!this.primed || tick != this.cachedTick || editVersion != this.cachedEditVersion) {
            this.cache.clear();
            this.cachedTick = tick;
            this.cachedEditVersion = editVersion;
            this.primed = true;
        }
    }

    private record ColumnKey(int x, int z, Heightmap.Types type) {
        private boolean inside(int minX, int minZ, int maxX, int maxZ) {
            return this.x >= minX && this.x <= maxX && this.z >= minZ && this.z <= maxZ;
        }
    }

    private static final class ColumnCache extends LinkedHashMap<ColumnKey, Column> {
        ColumnCache() {
            super(64, 0.75F, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<ColumnKey, Column> eldest) {
            return size() > MAX_CACHED_COLUMNS;
        }
    }
}
