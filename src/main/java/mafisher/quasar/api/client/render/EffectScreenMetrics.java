package mafisher.quasar.api.client.render;

/**
 * Screen-space projection metrics for one visible effect.
 *
 * <p>{@code coverage} is the fraction of the viewport covered by the renderer's conservative bounding sphere,
 * clamped to {@code [0, 1]}. {@code centreDistance} is the normalized distance of the projected centre from the
 * viewport centre, where {@code 0} is the centre and {@code 1} is the corner. {@code score} combines both so effects
 * near the centre keep presentation quality longer.</p>
 *
 * <p>{@code projected} is {@code false} when no usable camera projection was available. In that case the engine
 * keeps the global quality tier and falls back to distance LOD; callers must not treat the zero metrics as a real
 * screen sample.</p>
 */
public record EffectScreenMetrics(
    double coverage,
    double centreDistance,
    double score,
    boolean projected
) {
    /** Neutral sample used when no camera projection is available. */
    public static final EffectScreenMetrics UNKNOWN = new EffectScreenMetrics(0.0, 0.0, 0.0, false);

    private static final double CENTRE_PENALTY = 0.5;

    public EffectScreenMetrics {
        requireUnit(coverage, "coverage");
        requireUnit(centreDistance, "centreDistance");
        requireUnit(score, "score");
    }

    /** Builds a projected sample and derives its score. Both inputs must be in {@code [0, 1]}. */
    public static EffectScreenMetrics of(double coverage, double centreDistance) {
        requireUnit(coverage, "coverage");
        requireUnit(centreDistance, "centreDistance");
        double score = coverage * (1.0 - CENTRE_PENALTY * centreDistance);
        return new EffectScreenMetrics(coverage, centreDistance, score, true);
    }

    private static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Effect screen " + name + " must be in [0, 1]");
        }
    }
}
