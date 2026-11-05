package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import java.util.UUID;

/** Opaque handle to one active gameplay effect session. */
public record GameplayEffectHandle(UUID sessionId) {
    public GameplayEffectHandle {
        Objects.requireNonNull(sessionId, "sessionId");
    }
}
