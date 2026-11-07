package mafisher.quasar.effect.scale;

import java.util.Arrays;

/**
 * Immutable per-column height override for a segmented grid. Values are absolute block Y coordinates indexed by
 * {@code column + row * columns}; an empty profile keeps the coordinator base Y.
 *
 * <p>The profile is baked on the server (for example from a heightmap) and travels with the aggregated coordinator
 * start, so the client reproduces the same cell placement without loading chunks or sampling terrain itself.
 */
public final class SegmentHeightProfile {
    /** No override; cells use the coordinator base Y. */
    public static final SegmentHeightProfile NONE = new SegmentHeightProfile(0, 0, new double[0]);

    private final int columns;
    private final int rows;
    private final double[] values;

    private SegmentHeightProfile(int columns, int rows, double[] values) {
        this.columns = columns;
        this.rows = rows;
        this.values = values;
    }

    /** Wraps per-column absolute Y values in column-major order. The array is copied. */
    public static SegmentHeightProfile of(int columns, int rows, double[] values) {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Height profile dimensions must be positive");
        }
        if (values.length != columns * rows) {
            throw new IllegalArgumentException("Height profile must contain one value per grid column");
        }
        double[] copy = values.clone();
        for (double value : copy) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Height profile values must be finite");
            }
        }
        return new SegmentHeightProfile(columns, rows, copy);
    }

    public boolean isEmpty() {
        return this.values.length == 0;
    }

    public int columns() {
        return this.columns;
    }

    public int rows() {
        return this.rows;
    }

    public int size() {
        return this.values.length;
    }

    /** Absolute Y for one grid column. */
    public double y(int column, int row) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Empty height profile has no values");
        }
        if (column < 0 || column >= this.columns || row < 0 || row >= this.rows) {
            throw new IndexOutOfBoundsException("Height profile cell is outside the configured grid");
        }
        return this.values[column + row * this.columns];
    }

    /** Difference between the highest and lowest column, or zero for an empty profile. */
    public double spread() {
        if (this.isEmpty()) {
            return 0.0;
        }
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double value : this.values) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        return maximum - minimum;
    }

    /** Largest absolute distance between a column value and {@code referenceY}. */
    public double maxAbsDeviation(double referenceY) {
        if (!Double.isFinite(referenceY)) {
            throw new IllegalArgumentException("Reference Y must be finite");
        }
        double maximum = 0.0;
        for (double value : this.values) {
            maximum = Math.max(maximum, Math.abs(value - referenceY));
        }
        return maximum;
    }

    /** Copy of the raw absolute Y values, in column-major order. */
    public double[] values() {
        return this.values.clone();
    }

    public double minimumY() {
        double minimum = Double.POSITIVE_INFINITY;
        for (double value : this.values) {
            minimum = Math.min(minimum, value);
        }
        return this.isEmpty() ? 0.0 : minimum;
    }

    public double maximumY() {
        double maximum = Double.NEGATIVE_INFINITY;
        for (double value : this.values) {
            maximum = Math.max(maximum, value);
        }
        return this.isEmpty() ? 0.0 : maximum;
    }

    public boolean matches(int columns, int rows) {
        return !this.isEmpty() && this.columns == columns && this.rows == rows;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SegmentHeightProfile profile)) {
            return false;
        }
        return this.columns == profile.columns
            && this.rows == profile.rows
            && Arrays.equals(this.values, profile.values);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * this.columns + this.rows) + Arrays.hashCode(this.values);
    }

    @Override
    public String toString() {
        return this.isEmpty()
            ? "SegmentHeightProfile[NONE]"
            : "SegmentHeightProfile[" + this.columns + "x" + this.rows + "]";
    }
}
