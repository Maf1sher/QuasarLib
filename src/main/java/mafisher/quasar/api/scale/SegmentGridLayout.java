package mafisher.quasar.api.scale;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.effect.scale.SegmentedEffectLimits;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry of a segmented grid. The layout is shared by the server coordinator and the client, so both sides derive
 * segment placement from the same math and one aggregated base transform is enough to move a whole grid.
 *
 * <p>Cells are placed on a {@code columns x rows x layers} grid centered on the base. {@code spacing} separates
 * horizontal cells and {@code layerSpacing} separates vertical layers. The grid rotates with the base yaw; pitch and
 * roll are inherited by every cell but do not tilt the grid. A cell's Y is the base Y plus its layer offset, unless a
 * {@link SegmentHeightProfile} supplies an absolute per-column surface Y.
 */
public record SegmentGridLayout(int columns, int rows, int layers, double spacing, double layerSpacing) {
    /** Maximum number of cells in one grid; independent of the standalone visual-effect cap. */
    public static final int MAX_SEGMENTS = SegmentedEffectLimits.MAX_SEGMENTS_PER_GRID;

    public SegmentGridLayout {
        long horizontalCells = (long)columns * rows;
        long segmentCount = horizontalCells * layers;
        if (columns < 1 || rows < 1 || layers < 1 || segmentCount > MAX_SEGMENTS
            || !Double.isFinite(spacing) || spacing < 0.0
            || (horizontalCells > 1 && spacing <= 0.0)
            || !Double.isFinite(layerSpacing) || layerSpacing < 0.0
            || (layers > 1 && layerSpacing <= 0.0)) {
            throw new IllegalArgumentException("Invalid segment grid layout");
        }
    }

    /** Flat horizontal grid with one layer. */
    public SegmentGridLayout(int columns, int rows, double spacing) {
        this(columns, rows, 1, spacing, 0.0);
    }

    public int segmentCount() {
        return this.columns * this.rows * this.layers;
    }

    /** Flat cell index in {@code (layer * rows + row) * columns + column} order. */
    public int index(int column, int row, int layer) {
        this.requireCell(column, row, layer);
        return (layer * this.rows + row) * this.columns + column;
    }

    public int column(int index) {
        this.requireIndex(index);
        return index % this.columns;
    }

    public int row(int index) {
        this.requireIndex(index);
        return (index / this.columns) % this.rows;
    }

    public int layer(int index) {
        this.requireIndex(index);
        return index / (this.columns * this.rows);
    }

    /** Maximum distance from the grid center to any cell, including rotation and vertical layers. */
    public double halfDiagonal() {
        double halfX = (this.columns - 1) * this.spacing / 2.0;
        double halfY = (this.layers - 1) * this.layerSpacing / 2.0;
        double halfZ = (this.rows - 1) * this.spacing / 2.0;
        return Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);
    }

    /** Offset from the grid center to one cell in the bottom layer, rotated by {@code yaw}. */
    public Vec3 offset(int column, int row, float yaw) {
        return this.offset(column, row, 0, yaw);
    }

    /** Offset from the grid center to one cell, including its layer height, rotated by {@code yaw}. */
    public Vec3 offset(int column, int row, int layer, float yaw) {
        this.requireCell(column, row, layer);
        double localX = (column - (this.columns - 1) / 2.0) * this.spacing;
        double localY = (layer - (this.layers - 1) / 2.0) * this.layerSpacing;
        double localZ = (row - (this.rows - 1) / 2.0) * this.spacing;
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(
            localX * cos - localZ * sin,
            localY,
            localX * sin + localZ * cos
        );
    }

    /** Absolute transform of one cell in the bottom layer around {@code base}. */
    public EffectTransform transform(EffectTransform base, int column, int row) {
        return this.transform(base, column, row, 0, SegmentHeightProfile.NONE);
    }

    /** Absolute transform of one cell around {@code base}; pitch and roll are inherited. */
    public EffectTransform transform(EffectTransform base, int column, int row, int layer) {
        return this.transform(base, column, row, layer, SegmentHeightProfile.NONE);
    }

    /**
     * Absolute transform of one cell around {@code base}. A non-empty height profile supplies an absolute per-column
     * Y, so the base Y no longer moves those cells vertically; the layer offset is still applied on top.
     */
    public EffectTransform transform(
        EffectTransform base,
        int column,
        int row,
        int layer,
        SegmentHeightProfile heights
    ) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(heights, "heights");
        if (!heights.isEmpty() && !heights.matches(this.columns, this.rows)) {
            throw new IllegalArgumentException("Height profile does not match the grid columns");
        }
        Vec3 offset = this.offset(column, row, layer, base.yaw());
        double y = (heights.isEmpty() ? base.position().y() : heights.y(column, row)) + offset.y;
        return new EffectTransform(
            new EffectPoint(
                base.position().x() + offset.x,
                y,
                base.position().z() + offset.z
            ),
            base.yaw(),
            base.pitch(),
            base.roll()
        );
    }

    private void requireCell(int column, int row, int layer) {
        if (column < 0 || column >= this.columns || row < 0 || row >= this.rows
            || layer < 0 || layer >= this.layers) {
            throw new IndexOutOfBoundsException("Segment cell is outside the configured grid");
        }
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= this.segmentCount()) {
            throw new IndexOutOfBoundsException("Segment index is outside the configured grid");
        }
    }
}
