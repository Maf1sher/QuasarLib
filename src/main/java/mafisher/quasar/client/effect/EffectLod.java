package mafisher.quasar.client.effect;

/**
 * Engine-selected level of detail for one render call. The engine derives the level from the renderer's
 * {@link EffectLodProfile} and the camera distance to the effect. Selection is client-only and cosmetic; it never
 * changes authority, persistence, or the wire format.
 *
 * <p>{@code gapInRadii} is the distance from the camera to the surface of the renderer's conservative bounding sphere,
 * divided by the sphere radius. A camera inside the sphere observes a gap of zero.
 */
public record EffectLod(int level, double cameraDistance, double gapInRadii) {
    /** Full detail with no camera-relative information; used when no profile is declared. */
    public static final EffectLod FULL = new EffectLod(0, 0.0, 0.0);

    public EffectLod {
        if (level < 0 || !Double.isFinite(cameraDistance) || cameraDistance < 0.0
            || !Double.isFinite(gapInRadii) || gapInRadii < 0.0) {
            throw new IllegalArgumentException("Invalid effect LOD");
        }
    }
}
