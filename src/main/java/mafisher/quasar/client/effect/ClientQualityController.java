package mafisher.quasar.client.effect;

import mafisher.quasar.QuasarClientConfig;
import mafisher.quasar.effect.EffectQualityTier;

/** Client-lifetime quality tier selection and automatic degradation state. */
final class ClientQualityController {
    private static final EffectGpuTimer gpuTimer = new EffectGpuTimer(new GlGpuQueryBackend());
    private static EffectQualityGovernor governor;
    private static long worldRenderNanos;
    private static EffectQualityTier forcedTier;
    private static boolean gpuResetPending;

    private ClientQualityController() {
    }

    /** Returns the tier used by the current frame. */
    static EffectQualityTier tier() {
        if (forcedTier != null) {
            return forcedTier;
        }
        EffectQualityTier fixed = QuasarClientConfig.qualityMode().fixedTier();
        if (fixed != null) {
            return fixed;
        }
        return governor().tier();
    }

    static void recordWorldRender(long nanos) {
        worldRenderNanos = Math.max(0L, nanos);
    }

    /** Completes one frame sample; called after screen effects and composition. */
    static void recordLevelRender(long nanos) {
        long cost = worldRenderNanos + Math.max(0L, nanos);
        worldRenderNanos = 0L;
        processPendingGpuReset();
        long gpuNanos = gpuTimer.consumeCompletedNanos();
        if (autoQuality()) {
            governor().sample(cost, gpuNanos);
        }
    }

    /** Starts the GPU timer for the effect world pass; a no-op unless automatic quality is active. */
    static void beginWorldGpu() {
        processPendingGpuReset();
        if (autoQuality()) {
            gpuTimer.beginWorld();
        }
    }

    static void endWorldGpu() {
        gpuTimer.endWorld();
    }

    /** Starts the GPU timer for screen composition; a no-op unless automatic quality is active. */
    static void beginLevelGpu() {
        processPendingGpuReset();
        if (autoQuality()) {
            gpuTimer.beginLevel();
        }
    }

    static void endLevelGpu() {
        gpuTimer.endLevel();
    }

    /** Closes the GPU frame, polls completed results, and advances the timer ring. */
    static void endFrameGpu() {
        gpuTimer.endFrame();
    }

    /**
     * Resets the governor; the next frame starts again at the configured maximum tier. GPU queries are released on
     * the render thread, so lifecycle events from another thread only schedule the reset.
     */
    static void reset() {
        governor = null;
        worldRenderNanos = 0L;
        gpuResetPending = true;
    }

    static long lastP95Nanos() {
        return governor == null ? 0L : governor.lastP95Nanos();
    }

    /** Test/benchmark hook; {@code null} restores normal configuration-driven selection. */
    static void forceTier(EffectQualityTier tier) {
        forcedTier = tier;
    }

    private static boolean autoQuality() {
        return forcedTier == null && QuasarClientConfig.qualityMode().fixedTier() == null;
    }

    private static void processPendingGpuReset() {
        if (!gpuResetPending) {
            return;
        }
        gpuResetPending = false;
        gpuTimer.reset();
    }

    private static EffectQualityGovernor governor() {
        EffectQualityTier maximum = QuasarClientConfig.autoMaxTier();
        long budgetNanos = Math.max(1L, Math.round(QuasarClientConfig.effectBudgetMillis() * 1_000_000.0D));
        if (governor == null || governor.maximum() != maximum || governor.budgetNanos() != budgetNanos) {
            governor = new EffectQualityGovernor(maximum, budgetNanos);
        }
        return governor;
    }
}
