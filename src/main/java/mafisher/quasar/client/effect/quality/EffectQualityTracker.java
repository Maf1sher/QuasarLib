package mafisher.quasar.client.effect.quality;

import java.util.Objects;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Per-effect quality hysteresis. A new tier must remain the target for a short hold window before it is applied, so
 * a score moving around a threshold cannot make cosmetic layers flicker.
 */
@ApiStatus.Internal
public final class EffectQualityTracker {
    /** Length of the quality hold window, in ticks. */
    static final double HOLD_TICKS = 10.0;

    private EffectQualityTracker() {
    }

    /** Mutable per-instance tier state; owned and cleared by the client manager. */
    public static final class State {
        private EffectQualityTier tier = EffectQualityTier.HIGH;
        private EffectQualityTier pendingTier = EffectQualityTier.HIGH;
        private double pendingSince;
        private boolean initialized;
    }

    /** Updates one instance and returns the tier that should be used this frame. */
    public static EffectQualityTier update(State state, EffectQualityTier target, double renderGameTime) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(renderGameTime)) {
            throw new IllegalArgumentException("Invalid quality render game time");
        }
        if (!state.initialized) {
            state.tier = target;
            state.pendingTier = target;
            state.pendingSince = renderGameTime;
            state.initialized = true;
            return target;
        }
        if (target == state.tier) {
            state.pendingTier = target;
            state.pendingSince = renderGameTime;
            return state.tier;
        }
        if (state.pendingTier != target) {
            state.pendingTier = target;
            state.pendingSince = renderGameTime;
            return state.tier;
        }
        if (renderGameTime < state.pendingSince) {
            state.pendingSince = renderGameTime;
        }
        if (renderGameTime - state.pendingSince >= HOLD_TICKS) {
            state.tier = target;
            state.pendingTier = target;
            state.pendingSince = renderGameTime;
        }
        return state.tier;
    }
}
