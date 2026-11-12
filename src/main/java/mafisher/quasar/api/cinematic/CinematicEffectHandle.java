package mafisher.quasar.api.cinematic;

import java.util.Objects;
import java.util.UUID;

/** Opaque handle for one active cinematic session. */
public record CinematicEffectHandle(UUID sessionId) {
    public CinematicEffectHandle {
        Objects.requireNonNull(sessionId, "sessionId");
    }
}
