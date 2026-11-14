package mafisher.quasar.api.client.render;

import java.util.List;
import java.util.Objects;

/**
 * Ordered screen-space LOD bands for one visual effect renderer. Thresholds are minimum viewport coverage values:
 * level {@code 0} keeps full detail when the effect covers at least the first threshold, and the final level is
 * selected below the last threshold.
 *
 * <p>Internally the profile stores the complementary detail loss ({@code 1 - coverage}) in ascending order, so the
 * same hysteresis and blend code handles distance and screen profiles. A renderer without a screen profile keeps
 * the distance {@link EffectLodProfile}.</p>
 */
public final class EffectScreenLodProfile {
    /** Maximum number of thresholds; the last level is unbounded below. */
    public static final int MAX_THRESHOLDS = 8;

    /** No screen-space degradation; distance LOD is used instead. */
    public static final EffectScreenLodProfile FULL = new EffectScreenLodProfile(List.of());

    private final List<Double> minimumCoverage;
    private final double[] lossThresholds;

    /**
     * Creates a profile from ordered minimum-coverage thresholds.
     *
     * @param minimumCoverage strictly decreasing finite values in {@code (0, 1]}
     */
    public EffectScreenLodProfile(List<Double> minimumCoverage) {
        Objects.requireNonNull(minimumCoverage, "minimumCoverage");
        this.minimumCoverage = List.copyOf(minimumCoverage);
        if (this.minimumCoverage.size() > MAX_THRESHOLDS) {
            throw new IllegalArgumentException(
                "A screen LOD profile supports at most " + MAX_THRESHOLDS + " thresholds"
            );
        }
        this.lossThresholds = new double[this.minimumCoverage.size()];
        double previous = Double.POSITIVE_INFINITY;
        for (int index = 0; index < this.minimumCoverage.size(); index++) {
            Double value = this.minimumCoverage.get(index);
            if (value == null) {
                throw new NullPointerException("minimumCoverage[" + index + "]");
            }
            double coverage = value;
            if (!Double.isFinite(coverage) || coverage <= 0.0 || coverage > 1.0 || coverage >= previous) {
                throw new IllegalArgumentException(
                    "Effect screen LOD thresholds must be finite, in (0, 1], and strictly decreasing"
                );
            }
            this.lossThresholds[index] = 1.0 - coverage;
            previous = coverage;
        }
    }

    /** Returns the validated minimum-coverage thresholds in descending order. */
    public List<Double> minimumCoverage() {
        return this.minimumCoverage;
    }

    /** Builds a profile from minimum-coverage thresholds; an empty array returns {@link #FULL}. */
    public static EffectScreenLodProfile of(double... minimumCoverage) {
        Objects.requireNonNull(minimumCoverage, "minimumCoverage");
        if (minimumCoverage.length == 0) {
            return FULL;
        }
        Double[] boxed = new Double[minimumCoverage.length];
        for (int index = 0; index < minimumCoverage.length; index++) {
            boxed[index] = minimumCoverage[index];
        }
        return new EffectScreenLodProfile(List.of(boxed));
    }

    /** Number of levels, including the unbounded final level. */
    public int levelCount() {
        return this.lossThresholds.length + 1;
    }

    /** Returns the detail-loss threshold for one level without boxing it. */
    public double lossThreshold(int index) {
        return this.lossThresholds[index];
    }

    /** Returns the minimum coverage required for one level. */
    public double minimumCoverage(int index) {
        return this.minimumCoverage.get(index);
    }

    /**
     * Selects a level for viewport coverage. Coverage equal to a threshold stays at the higher-detail level;
     * coverage below the final threshold uses the final level.
     */
    public int selectCoverage(double coverage) {
        if (!Double.isFinite(coverage) || coverage < 0.0 || coverage > 1.0) {
            throw new IllegalArgumentException("Effect screen coverage must be in [0, 1]");
        }
        return this.selectLoss(1.0 - coverage);
    }

    /**
     * Selects a level for the internal detail-loss metric. Higher loss selects a cheaper level; loss equal to a
     * threshold stays at the higher-detail level.
     */
    public int selectLoss(double loss) {
        if (!Double.isFinite(loss) || loss < 0.0 || loss > 1.0) {
            throw new IllegalArgumentException("Effect screen detail loss must be in [0, 1]");
        }
        for (int index = 0; index < this.lossThresholds.length; index++) {
            if (loss <= this.lossThresholds[index]) {
                return index;
            }
        }
        return this.lossThresholds.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EffectScreenLodProfile profile
            && this.minimumCoverage.equals(profile.minimumCoverage);
    }

    @Override
    public int hashCode() {
        return this.minimumCoverage.hashCode();
    }

    @Override
    public String toString() {
        return "EffectScreenLodProfile" + this.minimumCoverage;
    }
}
