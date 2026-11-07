package mafisher.quasar.effect.scale;

/** Capacity limits shared by segmented effect specs, services, payloads, and the client. */
public final class SegmentedEffectLimits {
    /** Maximum number of cells in one coordinator grid. */
    public static final int MAX_SEGMENTS_PER_GRID = 128;
    /**
     * Default dedicated per-level segment budget. Segmented cells do not consume the standalone visual
     * budget; the effective value is configured in {@code QuasarConfig} and enforced as a soft budget.
     */
    public static final int MAX_SEGMENTS_PER_LEVEL = 1_024;

    private SegmentedEffectLimits() {
    }
}
