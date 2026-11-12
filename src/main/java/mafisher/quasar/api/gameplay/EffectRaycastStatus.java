package mafisher.quasar.api.gameplay;

/** Outcome of one {@link EffectWorldQuery#raycast} call. */
public enum EffectRaycastStatus {
    /** The ray hit a block accepted by the stop filter. */
    HIT,
    /** The ray reached its end without hitting an accepted block. */
    MISS,
    /** The ray entered a chunk that is not loaded; no further blocks were read. */
    UNLOADED,
    /** The per-tick world-sample budget ran out before the ray finished. */
    BUDGET_EXHAUSTED,
    /** The requested distance exceeds {@link EffectWorldQuery#MAX_RAYCAST_DISTANCE}. */
    TOO_LONG
}
