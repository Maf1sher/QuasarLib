package mafisher.quasar.effect.scale;

import org.jetbrains.annotations.ApiStatus;

/** Capacity limits shared by segmented effect specs, services, payloads, and the client. */
@ApiStatus.Internal
public final class SegmentedEffectLimits {
    /** Maximum number of cells in one coordinator grid. */
    public static final int MAX_SEGMENTS_PER_GRID = 128;
    private SegmentedEffectLimits() {
    }
}
