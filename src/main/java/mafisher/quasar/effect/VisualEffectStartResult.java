package mafisher.quasar.effect;

import java.util.Objects;

/** Non-null handle plus the explicit outcome of a visual start or restore. */
public record VisualEffectStartResult(VisualEffectHandle handle, EffectStartStatus status) {
    public VisualEffectStartResult {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(status, "status");
    }

    /** True when the visual is currently active and visible to its audience. */
    public boolean active() {
        return this.status == EffectStartStatus.ACTIVE
            || this.status == EffectStartStatus.RESTORED_ACTIVE;
    }
}
