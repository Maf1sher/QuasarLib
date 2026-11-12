package mafisher.quasar.api.client;

import java.util.List;
import java.util.Objects;

/**
 * Ordered distance bands for one visual effect renderer. Each threshold is the inclusive upper bound of a level's
 * surface gap, measured in multiples of the renderer's conservative spatial radius. Level {@code 0} is full detail;
 * levels beyond the last threshold share the last index.
 *
 * <p>A renderer without a finite {@link VisualEffectRenderer#spatialRadius(EffectRenderContext)} always renders at
 * level {@code 0}. The default {@link #FULL} profile keeps existing renderers at full detail.</p>
 */
public final class EffectLodProfile {
    /** Maximum number of thresholds; the last level is unbounded. */
    public static final int MAX_THRESHOLDS = 8;

    /** One level with no degradation. */
    public static final EffectLodProfile FULL = new EffectLodProfile(List.of());

    private final List<Double> maximumGapInRadii;
    private final double[] thresholds;

    /**
     * Creates a profile from ordered thresholds.
     *
     * @param maximumGapInRadii strictly increasing, finite, non-negative gap bounds
     */
    public EffectLodProfile(List<Double> maximumGapInRadii) {
        Objects.requireNonNull(maximumGapInRadii, "maximumGapInRadii");
        this.maximumGapInRadii = List.copyOf(maximumGapInRadii);
        if (this.maximumGapInRadii.size() > MAX_THRESHOLDS) {
            throw new IllegalArgumentException(
                "An effect LOD profile supports at most " + MAX_THRESHOLDS + " thresholds"
            );
        }
        this.thresholds = new double[this.maximumGapInRadii.size()];
        double previous = -1.0;
        for (int index = 0; index < this.maximumGapInRadii.size(); index++) {
            Double value = this.maximumGapInRadii.get(index);
            if (value == null) {
                throw new NullPointerException("maximumGapInRadii[" + index + "]");
            }
            double threshold = value;
            if (!Double.isFinite(threshold) || threshold < 0.0 || threshold <= previous) {
                throw new IllegalArgumentException(
                    "Effect LOD thresholds must be finite, non-negative, and strictly increasing"
                );
            }
            this.thresholds[index] = threshold;
            previous = threshold;
        }
    }

    /** Returns the validated thresholds in ascending order. */
    public List<Double> maximumGapInRadii() {
        return this.maximumGapInRadii;
    }

    /** Builds a profile from ordered thresholds; an empty array returns {@link #FULL}. */
    public static EffectLodProfile of(double... maximumGapInRadii) {
        Objects.requireNonNull(maximumGapInRadii, "maximumGapInRadii");
        if (maximumGapInRadii.length == 0) {
            return FULL;
        }
        Double[] boxed = new Double[maximumGapInRadii.length];
        for (int index = 0; index < maximumGapInRadii.length; index++) {
            boxed[index] = maximumGapInRadii[index];
        }
        return new EffectLodProfile(List.of(boxed));
    }

    /** Number of levels, including the unbounded final level. */
    public int levelCount() {
        return this.thresholds.length + 1;
    }

    /** Returns one threshold without boxing it. */
    public double threshold(int index) {
        return this.thresholds[index];
    }

    /**
     * Selects a level for a surface gap. A gap equal to a threshold stays at the lower level; values beyond the last
     * threshold use the final level.
     */
    public int select(double gapInRadii) {
        if (!Double.isFinite(gapInRadii) || gapInRadii < 0.0) {
            throw new IllegalArgumentException("Effect LOD gap must be finite and non-negative");
        }
        for (int index = 0; index < this.thresholds.length; index++) {
            if (gapInRadii <= this.thresholds[index]) {
                return index;
            }
        }
        return this.thresholds.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EffectLodProfile profile
            && this.maximumGapInRadii.equals(profile.maximumGapInRadii);
    }

    @Override
    public int hashCode() {
        return this.maximumGapInRadii.hashCode();
    }

    @Override
    public String toString() {
        return "EffectLodProfile" + this.maximumGapInRadii;
    }
}
