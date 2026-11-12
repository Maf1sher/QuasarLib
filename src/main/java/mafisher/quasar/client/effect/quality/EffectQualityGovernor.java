package mafisher.quasar.client.effect.quality;

import java.util.Arrays;
import mafisher.quasar.api.effect.EffectQualityTier;

/**
 * Sliding-window quality governor. Samples the engine's per-frame effect cost and lowers or raises the quality tier
 * with hysteresis. This class is pure and headlessly testable; {@link ClientQualityController} owns the clock and
 * configuration.
 */
final class EffectQualityGovernor {
    private static final int DEFAULT_WINDOW = 120;
    private static final int DEFAULT_EVALUATION_INTERVAL = 30;
    private static final int DEGRADE_WINDOWS = 2;
    private static final int RECOVER_WINDOWS = 4;
    private static final double RECOVER_FRACTION = 0.6;
    private static final int COOLDOWN_WINDOWS = 2;

    private final EffectQualityTier maximum;
    private final long budgetNanos;
    private final long[] samples;
    private final long[] sortedSamples;
    private final int evaluationInterval;
    private int sampleIndex;
    private int sampleCount;
    private int samplesSinceEvaluation;
    private int windowsAbove;
    private int windowsBelow;
    private int cooldownWindows;
    private EffectQualityTier tier;
    private long lastP95Nanos;
    private long lastGpuNanos = -1L;

    EffectQualityGovernor(EffectQualityTier maximum, long budgetNanos) {
        this(maximum, budgetNanos, DEFAULT_WINDOW, DEFAULT_EVALUATION_INTERVAL);
    }

    EffectQualityGovernor(EffectQualityTier maximum, long budgetNanos, int windowSize, int evaluationInterval) {
        if (maximum == null) {
            throw new NullPointerException("maximum");
        }
        if (budgetNanos < 1L || windowSize < 1 || evaluationInterval < 1 || evaluationInterval > windowSize) {
            throw new IllegalArgumentException("Invalid quality governor parameters");
        }
        this.maximum = maximum;
        this.budgetNanos = budgetNanos;
        this.samples = new long[windowSize];
        this.sortedSamples = new long[windowSize];
        this.evaluationInterval = evaluationInterval;
        this.tier = maximum;
    }

    EffectQualityTier maximum() {
        return this.maximum;
    }

    long budgetNanos() {
        return this.budgetNanos;
    }

    EffectQualityTier tier() {
        return this.tier;
    }

    long lastP95Nanos() {
        return this.lastP95Nanos;
    }

    /** Most recent positive GPU sample, or {@code -1} when the last frame had no GPU result. */
    long lastGpuNanos() {
        return this.lastGpuNanos;
    }

    /** Adds one frame sample and returns the current tier. */
    EffectQualityTier sample(long costNanos) {
        return sample(costNanos, -1L);
    }

    /**
     * Adds one frame sample and returns the current tier. The window uses the larger of CPU submission and GPU
     * execution cost, because both measure the same passes and overlap in time. A non-positive GPU value means no
     * available result, so CPU alone drives the sample.
     */
    EffectQualityTier sample(long costNanos, long gpuNanos) {
        long cpuNanos = Math.max(0L, costNanos);
        this.lastGpuNanos = gpuNanos > 0L ? gpuNanos : -1L;
        long sample = this.lastGpuNanos > 0L ? Math.max(cpuNanos, this.lastGpuNanos) : cpuNanos;
        this.samples[this.sampleIndex] = sample;
        this.sampleIndex = (this.sampleIndex + 1) % this.samples.length;
        this.sampleCount = Math.min(this.sampleCount + 1, this.samples.length);
        this.samplesSinceEvaluation++;
        if (this.samplesSinceEvaluation < this.evaluationInterval) {
            return this.tier;
        }
        this.samplesSinceEvaluation = 0;
        this.lastP95Nanos = percentile95();
        this.evaluate();
        return this.tier;
    }

    /** Resets samples and hysteresis, clamping the starting tier to the configured maximum. */
    void reset(EffectQualityTier startingTier) {
        EffectQualityTier clamped = startingTier.ordinal() > this.maximum.ordinal() ? this.maximum : startingTier;
        this.tier = clamped;
        this.sampleIndex = 0;
        this.sampleCount = 0;
        this.samplesSinceEvaluation = 0;
        this.windowsAbove = 0;
        this.windowsBelow = 0;
        this.cooldownWindows = 0;
        this.lastP95Nanos = 0L;
        this.lastGpuNanos = -1L;
        Arrays.fill(this.samples, 0L);
        Arrays.fill(this.sortedSamples, 0L);
    }

    private void evaluate() {
        if (this.cooldownWindows > 0) {
            this.cooldownWindows--;
            return;
        }
        if (this.lastP95Nanos > this.budgetNanos) {
            this.windowsAbove++;
            this.windowsBelow = 0;
            if (this.windowsAbove >= DEGRADE_WINDOWS && this.tier.ordinal() > EffectQualityTier.LOW.ordinal()) {
                this.tier = EffectQualityTier.values()[this.tier.ordinal() - 1];
                this.windowsAbove = 0;
                this.cooldownWindows = COOLDOWN_WINDOWS;
            }
        } else if (this.lastP95Nanos < (long)(this.budgetNanos * RECOVER_FRACTION)) {
            this.windowsBelow++;
            this.windowsAbove = 0;
            if (this.windowsBelow >= RECOVER_WINDOWS && this.tier.ordinal() < this.maximum.ordinal()) {
                this.tier = EffectQualityTier.values()[this.tier.ordinal() + 1];
                this.windowsBelow = 0;
                this.cooldownWindows = COOLDOWN_WINDOWS;
            }
        } else {
            this.windowsAbove = 0;
            this.windowsBelow = 0;
        }
    }

    private long percentile95() {
        System.arraycopy(this.samples, 0, this.sortedSamples, 0, this.sampleCount);
        Arrays.sort(this.sortedSamples, 0, this.sampleCount);
        int index = Math.max(0, (int)Math.ceil(this.sampleCount * 0.95D) - 1);
        return this.sortedSamples[index];
    }
}
