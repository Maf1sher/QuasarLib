package mafisher.quasar.api.client;

/**
 * Engine-selected level of detail for one render call. The engine derives the level from the renderer's
 * {@link EffectLodProfile} and the camera distance to the effect. Selection is client-only and cosmetic; it never
 * changes authority, persistence, or the wire format.
 *
 * <p>{@code gapInRadii} is the distance from the camera to the surface of the renderer's conservative bounding sphere,
 * divided by the sphere radius. A camera inside the sphere observes a gap of zero.</p>
 *
 * <p>{@code previousLevel} and {@code blend} describe an optional transition. {@code blend} runs from {@code 0}
 * (previous representation only) to {@code 1} (selected level only); at rest {@code previousLevel == level} and
 * {@code blend == 1}. The engine does not duplicate or remove passes automatically, so a renderer that wants a
 * cross-fade draws both representations from these values.</p>
 */
public record EffectLod(
    int level,
    int previousLevel,
    double blend,
    double cameraDistance,
    double gapInRadii
) {
    /** Full detail with no camera-relative information; used when no profile is declared. */
    public static final EffectLod FULL = new EffectLod(0, 0, 1.0, 0.0, 0.0);

    public EffectLod {
        if (level < 0 || previousLevel < 0
            || !Double.isFinite(blend) || blend < 0.0 || blend > 1.0
            || !Double.isFinite(cameraDistance) || cameraDistance < 0.0
            || !Double.isFinite(gapInRadii) || gapInRadii < 0.0) {
            throw new IllegalArgumentException("Invalid effect LOD");
        }
    }

    /** Compatibility constructor for callers that only describe a steady-state selection. */
    public EffectLod(int level, double cameraDistance, double gapInRadii) {
        this(level, level, 1.0, cameraDistance, gapInRadii);
    }

    /** Returns whether the engine is currently blending away from a different level. */
    public boolean isTransitioning() {
        return this.previousLevel != this.level && this.blend < 1.0;
    }
}
