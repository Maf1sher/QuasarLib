package mafisher.quasar.client.effect.quality;

import java.util.Objects;
import mafisher.quasar.api.client.render.EffectScreenMetrics;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Maps a screen-space quality score to a per-effect presentation tier. The global tier is a ceiling: scoring can
 * only lower it, never raise it, and fixed quality modes bypass scoring entirely.
 */
@ApiStatus.Internal
public final class EffectQualityScoring {
    /** Score at or above which an effect keeps the global tier. */
    static final double FULL_SCORE = 0.08;
    /** Score at or above which an effect drops one tier. */
    static final double ONE_TIER_SCORE = 0.02;

    private EffectQualityScoring() {
    }

    /** Returns the effective tier for one effect; the result never exceeds {@code global}. */
    public static EffectQualityTier effectiveTier(EffectQualityTier global, EffectScreenMetrics metrics,
        boolean automatic) {
        Objects.requireNonNull(global, "global");
        Objects.requireNonNull(metrics, "metrics");
        if (!automatic || !metrics.projected()) {
            return global;
        }
        int penalty = metrics.score() >= FULL_SCORE ? 0 : metrics.score() >= ONE_TIER_SCORE ? 1 : 2;
        int level = Math.max(EffectQualityTier.LOW.level(), global.level() - penalty);
        return EffectQualityTier.values()[level];
    }
}
