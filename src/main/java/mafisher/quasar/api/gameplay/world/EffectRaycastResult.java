package mafisher.quasar.api.gameplay.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of one raycast. {@code hit} is present exactly when {@link #status()} is
 * {@link EffectRaycastStatus#HIT}; every other status means no block was hit and is safe to treat
 * as a soft failure.
 */
public record EffectRaycastResult(EffectRaycastStatus status, Optional<EffectBlockHit> hit) {
    public EffectRaycastResult {
        Objects.requireNonNull(status, "status");
        hit = Objects.requireNonNull(hit, "hit");
        if ((status == EffectRaycastStatus.HIT) != hit.isPresent()) {
            throw new IllegalArgumentException("Only a HIT result carries a block hit");
        }
    }

    public boolean isHit() {
        return this.status == EffectRaycastStatus.HIT;
    }
}
