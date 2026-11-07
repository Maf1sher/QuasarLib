package mafisher.quasar.effect.scale;

/** Capacity limits shared by segmented effect specs, services, payloads, and the client. */
public final class SegmentedEffectLimits {
    /** Maximum number of cells in one coordinator grid. */
    public static final int MAX_SEGMENTS_PER_GRID = 128;
    /**
     * Provisional dedicated per-level segment budget. Segmented cells do not consume the standalone visual cap.
     * This remains a safety bound until priority-based soft degradation replaces it.
     */
    public static final int MAX_SEGMENTS_PER_LEVEL = 1_024;

    private SegmentedEffectLimits() {
    }
}
