package mafisher.quasar.api.compat;

/** State of an external shaderpack pipeline for the current frame. */
public enum ShaderPackState {
    /** No shaderpack is active; vanilla rendering owns the frame. */
    INACTIVE,
    /** A shaderpack renders the final frame. */
    ACTIVE,
    /** A shaderpack shadow pass is running; screen composition must be skipped. */
    SHADOW_PASS
}
