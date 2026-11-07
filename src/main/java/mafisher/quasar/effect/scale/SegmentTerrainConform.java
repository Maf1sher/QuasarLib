package mafisher.quasar.effect.scale;

import java.util.Objects;

/**
 * Server-side terrain alignment policy for a segmented grid. When enabled, every grid column is sampled once at
 * coordinator start and the resulting absolute Y values travel with the aggregated start payload.
 *
 * <p>Sampling never loads chunks: a column whose chunk is not loaded falls back to the coordinator center Y.
 */
public record SegmentTerrainConform(SegmentHeightSource source, double verticalOffset) {
    /** No terrain alignment; cells use the coordinator base Y. */
    public static final SegmentTerrainConform NONE = new SegmentTerrainConform(SegmentHeightSource.NONE, 0.0);

    /** Safety bound for an authored vertical offset. */
    public static final double MAX_VERTICAL_OFFSET = 4096.0;

    public SegmentTerrainConform {
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(verticalOffset) || Math.abs(verticalOffset) > MAX_VERTICAL_OFFSET) {
            throw new IllegalArgumentException("Terrain vertical offset must be finite and within +/-"
                + MAX_VERTICAL_OFFSET);
        }
    }

    public SegmentTerrainConform(SegmentHeightSource source) {
        this(source, 0.0);
    }

    public boolean isNone() {
        return this.source == SegmentHeightSource.NONE;
    }
}
