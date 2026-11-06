package mafisher.quasar.effect.persistence;

/**
 * Persistence policy recorded in a descriptor. {@link #RESUMABLE} effects survive level
 * unload and server restart; {@link #COSMETIC} effects are explicitly transient and cheap
 * to recreate.
 */
public enum EffectPersistence {
    RESUMABLE,
    COSMETIC
}
