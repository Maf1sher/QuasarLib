package mafisher.quasar.api.effect.damage;

/** What a hitbox does to a living target. */
public enum EffectHitboxOutcome {
    /** Apply the configured damage. */
    DAMAGE,
    /** Remove non-player mobs without drops and kill players with void damage. */
    REMOVE
}
