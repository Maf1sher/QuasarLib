package mafisher.quasar.api.gameplay;

/** Why a gameplay effect session ended. */
public enum GameplayEffectEndReason {
    EXPIRED,
    CANCELLED,
    OWNER_UNAVAILABLE,
    LEVEL_UNLOAD,
    SERVER_STOPPING,
    FAILED
}
