package mafisher.quasar.effect.scale;

import java.util.Objects;
import java.util.UUID;

/**
 * Client-visible link from one segment instance to its coordinator and grid cell. The link carries the whole layout, so
 * a client can rebuild absolute segment transforms from one aggregated update.
 */
public record SegmentLink(UUID coordinatorId, int segmentIndex, SegmentGridLayout layout) {
    public SegmentLink {
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        Objects.requireNonNull(layout, "layout");
        if (segmentIndex < 0 || segmentIndex >= layout.segmentCount()) {
            throw new IllegalArgumentException("Segment index is outside the configured grid");
        }
    }

    public int column() {
        return this.layout.column(this.segmentIndex);
    }

    public int row() {
        return this.layout.row(this.segmentIndex);
    }

    public int layer() {
        return this.layout.layer(this.segmentIndex);
    }
}
