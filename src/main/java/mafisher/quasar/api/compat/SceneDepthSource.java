package mafisher.quasar.api.compat;

/** Where the scene depth used for effect masks and composition comes from. */
public enum SceneDepthSource {
    /** Vanilla main render target; the fallback when no provider is active. */
    MAIN_TARGET,
    /** Depth produced by a horizon LOD renderer such as Distant Horizons or Voxy. */
    HORIZON_LOD,
    /** Depth owned by an external shaderpack pipeline such as Iris. */
    EXTERNAL_SHADERPACK
}
