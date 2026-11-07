package mafisher.quasar.effect.scale;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Deterministic segment identity and seed derivation shared by the server coordinator and the client. */
public final class SegmentIds {
    private SegmentIds() {
    }

    /** Stable segment identity derived from its coordinator and flat cell index. */
    public static UUID segmentId(UUID coordinatorId, int index) {
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        if (index < 0) {
            throw new IllegalArgumentException("Segment index cannot be negative");
        }
        return UUID.nameUUIDFromBytes(
            ("quasar:segment:" + coordinatorId + ":" + index).getBytes(StandardCharsets.UTF_8)
        );
    }

    /** Deterministic per-segment seed derived from the root seed and the segment cell in the bottom layer. */
    public static long segmentSeed(long rootSeed, int column, int row) {
        return segmentSeed(rootSeed, column, row, 0);
    }

    /**
     * Deterministic per-segment seed derived from the root seed and the 3D segment cell. The layer term is zero at
     * layer zero, so existing flat-grid seeds are unchanged.
     */
    public static long segmentSeed(long rootSeed, int column, int row, int layer) {
        if (column < 0 || row < 0 || layer < 0) {
            throw new IllegalArgumentException("Segment cell cannot be negative");
        }
        long value = rootSeed
            + 0x9E3779B97F4A7C15L * (column + 1L)
            + 0xC2B2AE3D27D4EB4FL * (row + 1L)
            + 0xD6E8FEB86659FD93L * layer;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
