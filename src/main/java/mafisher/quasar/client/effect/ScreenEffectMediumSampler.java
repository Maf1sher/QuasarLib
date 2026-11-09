package mafisher.quasar.client.effect;

/**
 * Block-level medium probe used to trace the camera-to-effect path.
 *
 * <p>{@link #surfaceY(double, double, double)} is only called for crossing
 * points, so implementations may scan the fluid column there without paying for
 * every sample. Neither method may load chunks.</p>
 */
public interface ScreenEffectMediumSampler {
    /** Returns the medium at the given world position. */
    ScreenEffectMedium mediumAt(double x, double y, double z);

    /** Returns the top fluid surface at the position, or {@link Float#NaN}. */
    default float surfaceY(double x, double y, double z) {
        return Float.NaN;
    }
}
