package mafisher.quasar.effect.scale;

import java.util.Objects;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry of a horizontal segment grid. The layout is shared by the server coordinator and the client, so both sides
 * derive segment placement from the same math and one aggregated base transform is enough to move a whole grid.
 */
public record SegmentGridLayout(int columns, int rows, double spacing) {
    /** Maximum number of cells in one grid; independent of the standalone visual-effect cap. */
    public static final int MAX_SEGMENTS = SegmentedEffectLimits.MAX_SEGMENTS_PER_GRID;

    public SegmentGridLayout {
        long segmentCount = (long)columns * rows;
        if (columns < 1 || rows < 1 || segmentCount > MAX_SEGMENTS
            || !Double.isFinite(spacing) || spacing < 0.0
            || (segmentCount > 1 && spacing <= 0.0)) {
            throw new IllegalArgumentException("Invalid segment grid layout");
        }
    }

    public int segmentCount() {
        return this.columns * this.rows;
    }

    /** Maximum horizontal distance from the grid center to any cell, including rotation. */
    public double halfDiagonal() {
        double halfX = (this.columns - 1) * this.spacing / 2.0;
        double halfZ = (this.rows - 1) * this.spacing / 2.0;
        return Math.sqrt(halfX * halfX + halfZ * halfZ);
    }

    /** Horizontal offset from the grid center to one cell, rotated by {@code yaw}. */
    public Vec3 offset(int column, int row, float yaw) {
        this.requireCell(column, row);
        double localX = (column - (this.columns - 1) / 2.0) * this.spacing;
        double localZ = (row - (this.rows - 1) / 2.0) * this.spacing;
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(localX * cos - localZ * sin, 0.0, localX * sin + localZ * cos);
    }

    /** Absolute transform of one cell around {@code base}; pitch and roll are inherited and do not tilt the grid. */
    public EffectTransform transform(EffectTransform base, int column, int row) {
        Objects.requireNonNull(base, "base");
        Vec3 offset = offset(column, row, base.yaw());
        return new EffectTransform(
            new EffectPoint(
                base.position().x() + offset.x,
                base.position().y() + offset.y,
                base.position().z() + offset.z
            ),
            base.yaw(),
            base.pitch(),
            base.roll()
        );
    }

    private void requireCell(int column, int row) {
        if (column < 0 || column >= this.columns || row < 0 || row >= this.rows) {
            throw new IndexOutOfBoundsException("Segment cell is outside the configured grid");
        }
    }
}
