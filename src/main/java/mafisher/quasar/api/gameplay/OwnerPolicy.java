package mafisher.quasar.api.gameplay;

/** What happens when the optional owner entity is removed or changes level. */
public enum OwnerPolicy {
    /** End the session when the owner dies, is removed, or changes level. */
    STOP_WHEN_UNAVAILABLE,
    /** Keep the session running after the owner is no longer available. */
    CONTINUE
}
