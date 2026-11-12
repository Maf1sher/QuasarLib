package mafisher.quasar.effect;

/** Explicit outcome shared by effect services that can cull a start against their soft budget. */
public enum EffectStartStatus {
    /** The effect was admitted and is active. */
    ACTIVE,
    /** The effect was admitted but immediately culled by the soft budget. */
    CULLED_ON_START,
    /** A persisted effect was restored and is active. */
    RESTORED_ACTIVE,
    /** A persisted effect was restored but culled by the soft budget. */
    RESTORED_INACTIVE
}
