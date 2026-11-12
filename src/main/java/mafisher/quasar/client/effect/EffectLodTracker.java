package mafisher.quasar.client.effect;

import java.util.Objects;
import mafisher.quasar.api.client.EffectLod;
import mafisher.quasar.api.client.EffectLodProfile;

/**
 * Per-instance client LOD state. Adds hysteresis around profile thresholds so camera motion cannot flicker between
 * adjacent levels, and tracks a short transition blend after a level change.
 *
 * <p>The state is client-only and cosmetic. The engine keeps one instance per standalone effect and per segmented
 * grid cell, keyed by the cell's instance ID.</p>
 */
final class EffectLodTracker {
    /** Fraction of a threshold that must be crossed before a level changes. */
    static final double HYSTERESIS_FRACTION = 0.15;

    /** Length of the transition blend, in ticks. */
    static final double BLEND_TICKS = 5.0;

    private EffectLodTracker() {
    }

    /** Mutable per-instance selection state; owned and cleared by the client manager. */
    static final class State {
        private int level;
        private int previousLevel;
        private double blendStartGameTime;
        private boolean initialized;
    }

    /**
     * Updates one instance's state and returns the LOD to expose to its renderer. Selection values come from the
     * pure {@link EffectLodSelection} result for the current frame.
     */
    static EffectLod update(State state, EffectLodProfile profile, double cameraDistance, double gapInRadii,
        double renderGameTime) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(profile, "profile");
        if (!Double.isFinite(cameraDistance) || cameraDistance < 0.0) {
            throw new IllegalArgumentException("Invalid LOD camera distance");
        }
        if (!Double.isFinite(gapInRadii) || gapInRadii < 0.0) {
            throw new IllegalArgumentException("Invalid LOD gap");
        }
        if (!Double.isFinite(renderGameTime)) {
            throw new IllegalArgumentException("Invalid render game time");
        }

        int target = profile.select(gapInRadii);
        if (!state.initialized) {
            state.level = target;
            state.previousLevel = target;
            state.blendStartGameTime = renderGameTime - BLEND_TICKS;
            state.initialized = true;
        } else {
            int levelCount = profile.levelCount();
            state.level = Math.min(state.level, levelCount - 1);
            state.previousLevel = Math.min(state.previousLevel, levelCount - 1);
            int level = state.level;
            if (target > level && crossesUp(profile, level, gapInRadii)) {
                state.previousLevel = level;
                state.level = target;
                state.blendStartGameTime = renderGameTime;
            } else if (target < level && crossesDown(profile, level, gapInRadii)) {
                state.previousLevel = level;
                state.level = target;
                state.blendStartGameTime = renderGameTime;
            }
        }

        double blend = 1.0;
        if (state.previousLevel != state.level) {
            if (renderGameTime < state.blendStartGameTime) {
                state.blendStartGameTime = renderGameTime;
            }
            blend = Math.min(1.0, (renderGameTime - state.blendStartGameTime) / BLEND_TICKS);
            if (blend >= 1.0) {
                state.previousLevel = state.level;
            }
        } else {
            state.previousLevel = state.level;
        }
        return new EffectLod(state.level, state.previousLevel, blend, cameraDistance, gapInRadii);
    }

    private static boolean crossesUp(EffectLodProfile profile, int level, double gapInRadii) {
        double boundary = profile.threshold(level);
        return gapInRadii > boundary * (1.0 + HYSTERESIS_FRACTION);
    }

    private static boolean crossesDown(EffectLodProfile profile, int level, double gapInRadii) {
        double boundary = profile.threshold(level - 1);
        if (boundary <= 0.0) {
            return gapInRadii <= 0.0;
        }
        return gapInRadii < boundary * (1.0 - HYSTERESIS_FRACTION);
    }
}
