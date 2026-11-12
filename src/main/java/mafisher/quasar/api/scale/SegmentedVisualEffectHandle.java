package mafisher.quasar.api.scale;

import java.util.Objects;
import java.util.UUID;

/** Opaque handle for one active segmented visual effect. */
public record SegmentedVisualEffectHandle(UUID coordinatorId) {
    public SegmentedVisualEffectHandle {
        Objects.requireNonNull(coordinatorId, "coordinatorId");
    }
}
