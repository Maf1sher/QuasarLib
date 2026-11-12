package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.phys.Vec3;

/**
 * Internal uniform grid broad phase for nearby audience queries. The grid is rebuilt for one level tick or one start
 * call; it is not shared across threads and does not retain players after the caller drops it.
 */
final class SpatialGrid<T> {
    static final double CELL_SIZE = 64.0;

    private final Map<Long, List<T>> cells = new HashMap<>();
    private final Function<T, Vec3> position;

    SpatialGrid(Function<T, Vec3> position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    void add(T value) {
        Objects.requireNonNull(value, "value");
        Vec3 point = this.position.apply(value);
        this.cells.computeIfAbsent(key(cell(point.x), cell(point.z)), ignored -> new ArrayList<>()).add(value);
    }

    /**
     * Visits every value whose position lies within the inclusive radius of {@code center}. Cells are queried in a
     * stable x/z order, and each value belongs to exactly one cell, so no value is visited twice.
     */
    void forEachNear(Vec3 center, double radius, Consumer<T> consumer) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(consumer, "consumer");
        double safeRadius = Math.max(0.0, radius);
        int minX = cell(center.x - safeRadius);
        int maxX = cell(center.x + safeRadius);
        int minZ = cell(center.z - safeRadius);
        int maxZ = cell(center.z + safeRadius);
        double radiusSqr = safeRadius * safeRadius;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                List<T> bucket = this.cells.get(key(x, z));
                if (bucket == null) {
                    continue;
                }
                for (T value : bucket) {
                    Vec3 point = this.position.apply(value);
                    if (point.distanceToSqr(center) <= radiusSqr) {
                        consumer.accept(value);
                    }
                }
            }
        }
    }

    int size() {
        int size = 0;
        for (List<T> bucket : this.cells.values()) {
            size += bucket.size();
        }
        return size;
    }

    static int cell(double coordinate) {
        return (int)Math.floor(coordinate / CELL_SIZE);
    }

    private static long key(int cellX, int cellZ) {
        return ((long)cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }
}
