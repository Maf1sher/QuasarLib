package mafisher.quasar.effect.cinematic;

import java.util.Objects;
import mafisher.quasar.effect.EffectStartStatus;

/** Non-null handle plus the explicit outcome of a cinematic start or restore. */
public record CinematicEffectStartResult(CinematicEffectHandle handle, EffectStartStatus status) {
    public CinematicEffectStartResult {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(status, "status");
    }

    /** True when the cinematic session is currently active. */
    public boolean active() {
        return this.status == EffectStartStatus.ACTIVE
            || this.status == EffectStartStatus.RESTORED_ACTIVE;
    }
}
