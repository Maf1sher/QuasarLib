package mafisher.quasar.effect;

import java.util.UUID;

/** Server-side handle used to update or stop a transient client visual before its natural expiry. */
public record VisualEffectHandle(UUID instanceId) {
}
