package mafisher.quasar.client.effect.quality;

import java.util.Objects;
import mafisher.quasar.api.client.render.EffectLod;
import mafisher.quasar.api.client.render.EffectLodProfile;
import org.jetbrains.annotations.ApiStatus;

/**
 * Per-instance client LOD state. Adds hysteresis around profile thresholds so camera motion cannot flicker between
 * adjacent levels, and tracks a short transition blend after a level change.
 *
 * <p>The state is client-only and cosmetic. The engine keeps one instance per standalone effect and per segmented
 * grid cell, keyed by the cell's instance ID.</p>
 */
@ApiStatus.Internal
public final class EffectLodTracker {
    /** Fraction of a threshold that must be crossed before a level changes. */
    static final double HYSTERESIS_FRACTION = 0.15;

    /** Length of the transition blend, in ticks. */
    static final double BLEND_TICKS = 5.0;

    private EffectLodTracker() {
    }

    /** Mutable per-instance selection state; owned and cleared by the client manager. */
    public static final class State {
        private int level;
        private int previousLevel;
        private double blendStartGameTime;
        private boolean initialized;

        /** Returns the current stable level, or {@code -1} before the first update. */
        public int level() {
            return this.initialized ? this.level : -1;
        }
    }

    /**
     * Updates one instance's state and returns the LOD to expose to its renderer. Selection values come from the
     * pure {@link EffectLodSelection} result for the current frame.
     */
    public static EffectLod update(State state, EffectLodProfile profile, double cameraDistance, double gapInRadii,
        double renderGameTime) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(profile, "profile");
        int target = profile.select(gapInRadii);
        int current = state.level();
        if (current < 0) {
            current = target;
        }
        current = Math.min(current, profile.levelCount() - 1);
        double currentBoundary = current < profile.levelCount() - 1 ? profile.threshold(current) : 0.0;
        double previousBoundary = current > 0 ? profile.threshold(current - 1) : 0.0;
        return update(state, profile.levelCount(), target, gapInRadii, currentBoundary, previousBoundary,
            cameraDistance, gapInRadii, renderGameTime);
    }

    /**
     * Metric-agnostic update used by both distance and screen-space profiles. {@code metricValue} grows as detail
     * gets cheaper, so the hysteresis rules are shared. {@code currentBoundary} is the loss boundary of the current
     * level and {@code previousBoundary} is the boundary of the level below it.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static EffectLod update(State state, int levelCount, int targetLevel, double metricValue,
        double currentBoundary, double previousBoundary, double cameraDistance, double gapInRadii,
        double renderGameTime) {
        Objects.requireNonNull(state, "state");
        requireSelection(levelCount, targetLevel, metricValue, currentBoundary, previousBoundary);
        requireMeasurement(cameraDistance, gapInRadii, renderGameTime);
        applySelection(state, levelCount, targetLevel, metricValue, currentBoundary, previousBoundary,
            renderGameTime);
        return snapshot(state, cameraDistance, gapInRadii, renderGameTime);
    }

    private static void requireSelection(int levelCount, int targetLevel, double metricValue,
        double currentBoundary, double previousBoundary) {
        if (levelCount < 1 || targetLevel < 0 || targetLevel >= levelCount) {
            throw new IllegalArgumentException("Invalid LOD level selection");
        }
        if (!Double.isFinite(metricValue) || metricValue < 0.0) {
            throw new IllegalArgumentException("Invalid LOD metric value");
        }
        if (!Double.isFinite(currentBoundary) || currentBoundary < 0.0
            || !Double.isFinite(previousBoundary) || previousBoundary < 0.0) {
            throw new IllegalArgumentException("Invalid LOD boundary");
        }
    }

    private static void requireMeasurement(double cameraDistance, double gapInRadii, double renderGameTime) {
        if (!Double.isFinite(cameraDistance) || cameraDistance < 0.0) {
            throw new IllegalArgumentException("Invalid LOD camera distance");
        }
        if (!Double.isFinite(gapInRadii) || gapInRadii < 0.0) {
            throw new IllegalArgumentException("Invalid LOD gap");
        }
        if (!Double.isFinite(renderGameTime)) {
            throw new IllegalArgumentException("Invalid render game time");
        }
    }

    private static void applySelection(State state, int levelCount, int targetLevel, double metricValue,
        double currentBoundary, double previousBoundary, double renderGameTime) {
        int target = Math.min(targetLevel, levelCount - 1);
        if (!state.initialized) {
            state.level = target;
            state.previousLevel = target;
            state.blendStartGameTime = renderGameTime - BLEND_TICKS;
            state.initialized = true;
            return;
        }
        state.level = Math.min(state.level, levelCount - 1);
        state.previousLevel = Math.min(state.previousLevel, levelCount - 1);
        int level = state.level;
        if (target > level && metricValue > currentBoundary * (1.0 + HYSTERESIS_FRACTION)) {
            state.previousLevel = level;
            state.level = target;
            state.blendStartGameTime = renderGameTime;
        } else if (target < level && crossesDown(previousBoundary, metricValue)) {
            state.previousLevel = level;
            state.level = target;
            state.blendStartGameTime = renderGameTime;
        }
    }

    private static EffectLod snapshot(State state, double cameraDistance, double gapInRadii,
        double renderGameTime) {
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

    private static boolean crossesDown(double previousBoundary, double metricValue) {
        if (previousBoundary <= 0.0) {
            return metricValue <= 0.0;
        }
        return metricValue < previousBoundary * (1.0 - HYSTERESIS_FRACTION);
    }
}
