package mafisher.quasar.compat;

/**
 * How an effect should interact with the active stack's fog. QuasarLib never
 * implements a fog model; these values only describe the environment default.
 */
public enum FogPolicy {
    /** Use the active stack's fog uniforms, if any. */
    RESPECT,
    /** Ignore fog and keep the effect emissive. */
    EMISSIVE,
    /** Draw without fog contribution. */
    NONE
}
