package mafisher.quasar.effect.gameplay;

import java.util.List;
import java.util.Objects;

/**
 * Result of one {@link EffectWorldQuery#scan} call. {@code examined} counts blocks charged to the
 * world-sample budget, and {@code truncated} means the scan stopped at the budget or result cap
 * before visiting every block in the range.
 */
public record EffectScanResult(List<EffectBlockSample> samples, int examined, boolean truncated) {
    public EffectScanResult {
        samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
        if (examined < 0 || examined < samples.size()) {
            throw new IllegalArgumentException("Examined blocks must be non-negative and cover the samples");
        }
    }
}
