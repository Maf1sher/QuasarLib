package mafisher.quasar.client.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.world.phys.AABB;

/**
 * Client-thread uniform grid broad phase for active visual instances and segmented coordinators. Entries carry
 * conservative world-space bounds; exact frustum and renderer culling still decides what is drawn.
 *
 * <p>The grid is maintained incrementally as instances start, move, and stop, and queried once per render stage. It is
 * not shared across threads and drops every entry when the client level changes.
 */
final class ClientEffectSpatialIndex {
    static final double CELL_SIZE = 64.0;
    /** Footprints above this radius are not indexed; they keep exact per-instance culling instead. */
    static final double MAX_INDEX_RADIUS = 2048.0;
    /** Bounds spanning more cells than this per axis are not indexed; a teleport must not stall the grid. */
    static final int MAX_CELLS_PER_AXIS = 64;
    private static final int MAX_CELL_COORDINATE = 1_000_000;

    private final Map<Long, List<Entry>> cells = new HashMap<>();
    private final Map<Key, Entry> entries = new LinkedHashMap<>();
    private long nextStamp = 1L;

    static boolean isIndexableRadius(double radius) {
        return Double.isFinite(radius) && radius >= 0.0 && radius <= MAX_INDEX_RADIUS;
    }

    static boolean isIndexableBounds(AABB bounds) {
        if (bounds == null || !areFiniteAndOrdered(bounds)) {
            return false;
        }
        long spanX = (long)cell(bounds.maxX) - cell(bounds.minX);
        long spanZ = (long)cell(bounds.maxZ) - cell(bounds.minZ);
        return spanX < MAX_CELLS_PER_AXIS && spanZ < MAX_CELLS_PER_AXIS;
    }

    void put(Key key, AABB bounds) {
        Objects.requireNonNull(key, "key");
        requireBounds(bounds);
        if (!isIndexableBounds(bounds)) {
            throw new IllegalArgumentException("Spatial index bounds span too many cells");
        }
        Entry existing = this.entries.get(key);
        if (existing != null) {
            this.removeFromCells(existing);
        }
        Entry entry = existing == null ? new Entry(key) : existing;
        entry.bounds = bounds;
        this.entries.put(key, entry);
        this.addToCells(entry);
    }

    void remove(Key key) {
        Objects.requireNonNull(key, "key");
        Entry entry = this.entries.remove(key);
        if (entry != null) {
            this.removeFromCells(entry);
        }
    }

    /** Returns every entry whose bounds intersect {@code region}; each entry is visited at most once. */
    void query(AABB region, Consumer<Entry> consumer) {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(consumer, "consumer");
        long stamp = this.nextStamp++;
        if (this.nextStamp == 0L) {
            this.nextStamp = 1L;
        }
        if (!Double.isFinite(region.minX) || !Double.isFinite(region.maxX)
            || !Double.isFinite(region.minZ) || !Double.isFinite(region.maxZ)) {
            for (Entry entry : this.entries.values()) {
                if (entry.stamp != stamp) {
                    entry.stamp = stamp;
                    if (entry.bounds.intersects(region)) {
                        consumer.accept(entry);
                    }
                }
            }
            return;
        }
        int minX = cell(region.minX);
        int maxX = cell(region.maxX);
        int minZ = cell(region.minZ);
        int maxZ = cell(region.maxZ);
        if ((long)maxX - minX >= MAX_CELLS_PER_AXIS || (long)maxZ - minZ >= MAX_CELLS_PER_AXIS) {
            for (Entry entry : this.entries.values()) {
                if (entry.stamp != stamp) {
                    entry.stamp = stamp;
                    if (entry.bounds.intersects(region)) {
                        consumer.accept(entry);
                    }
                }
            }
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                List<Entry> bucket = this.cells.get(key(x, z));
                if (bucket == null) {
                    continue;
                }
                for (int index = 0; index < bucket.size(); index++) {
                    Entry entry = bucket.get(index);
                    if (entry.stamp == stamp) {
                        continue;
                    }
                    entry.stamp = stamp;
                    if (entry.bounds.intersects(region)) {
                        consumer.accept(entry);
                    }
                }
            }
        }
    }

    void clear() {
        this.cells.clear();
        this.entries.clear();
    }

    int size() {
        return this.entries.size();
    }

    private void addToCells(Entry entry) {
        int minX = cell(entry.bounds.minX);
        int maxX = cell(entry.bounds.maxX);
        int minZ = cell(entry.bounds.minZ);
        int maxZ = cell(entry.bounds.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                this.cells.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(entry);
            }
        }
    }

    private void removeFromCells(Entry entry) {
        int minX = cell(entry.bounds.minX);
        int maxX = cell(entry.bounds.maxX);
        int minZ = cell(entry.bounds.minZ);
        int maxZ = cell(entry.bounds.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long key = key(x, z);
                List<Entry> bucket = this.cells.get(key);
                if (bucket == null) {
                    continue;
                }
                bucket.remove(entry);
                if (bucket.isEmpty()) {
                    this.cells.remove(key);
                }
            }
        }
    }

    static int cell(double coordinate) {
        int cell = (int)Math.floor(coordinate / CELL_SIZE);
        return Math.max(-MAX_CELL_COORDINATE, Math.min(MAX_CELL_COORDINATE, cell));
    }

    private static long key(int cellX, int cellZ) {
        return ((long)cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    private static void requireBounds(AABB bounds) {
        Objects.requireNonNull(bounds, "bounds");
        if (!areFiniteAndOrdered(bounds)) {
            throw new IllegalArgumentException("Spatial index bounds must be finite and ordered");
        }
    }

    private static boolean areFiniteAndOrdered(AABB bounds) {
        return !bounds.hasNaN()
            && Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
            && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ)
            && bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY && bounds.minZ <= bounds.maxZ;
    }

    enum Kind {
        INSTANCE,
        GROUP
    }

    record Key(Kind kind, UUID id) {
        Key {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }

        static Key instance(UUID id) {
            return new Key(Kind.INSTANCE, id);
        }

        static Key group(UUID id) {
            return new Key(Kind.GROUP, id);
        }
    }

    static final class Entry {
        private final Key key;
        private AABB bounds;
        private long stamp;

        private Entry(Key key) {
            this.key = key;
        }

        Key key() {
            return this.key;
        }

        AABB bounds() {
            return this.bounds;
        }
    }
}
