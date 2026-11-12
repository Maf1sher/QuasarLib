package mafisher.quasar.client.effect;

import mafisher.quasar.QuasarClientConfig;
import mafisher.quasar.effect.EffectQualityTier;

/**
 * Client-lifetime quality tier selection and automatic degradation state.
 *
 * <p>All mutable state is render-thread confined except the volatile reset handoff and the published
 * tier read by {@link ClientEffectRuntime#qualityTier()}.</p>
 */
final class ClientQualityController {
    private static final EffectGpuTimer gpuTimer = new EffectGpuTimer(new GlGpuQueryBackend());
    private static volatile EffectQualityTier publishedTier;
    private static EffectQualityGovernor governor;
    private static long worldRenderNanos;
    private static volatile boolean resetPending;

    private ClientQualityController() {
    }

    /** Returns the tier used by the current frame; call on the render thread. */
    static EffectQualityTier tier() {
        processPendingReset();
        EffectQualityTier fixed = QuasarClientConfig.qualityMode().fixedTier();
        EffectQualityTier selected = fixed != null ? fixed : governor().tier();
        publishedTier = selected;
        return selected;
    }

    /** Returns the most recently published tier for render-thread-external readers. */
    static EffectQualityTier publishedTier() {
        EffectQualityTier current = publishedTier;
        if (current != null) {
            return current;
        }
        EffectQualityTier fixed = QuasarClientConfig.qualityMode().fixedTier();
        EffectQualityTier selected = fixed != null ? fixed : QuasarClientConfig.autoMaxTier();
        publishedTier = selected;
        return selected;
    }

    static void recordWorldRender(long nanos) {
        worldRenderNanos = Math.max(0L, nanos);
    }

    /** Completes one frame sample; called after screen effects and composition. */
    static void recordLevelRender(long nanos) {
        processPendingReset();
        long cost = worldRenderNanos + Math.max(0L, nanos);
        worldRenderNanos = 0L;
        long gpuNanos = gpuTimer.consumeCompletedNanos();
        if (autoQuality()) {
            governor().sample(cost, gpuNanos);
        }
    }

    /** Starts the GPU timer for the effect world pass; a no-op unless automatic quality is active. */
    static void beginWorldGpu() {
        processPendingReset();
        if (autoQuality()) {
            gpuTimer.beginWorld();
        }
    }

    static void endWorldGpu() {
        gpuTimer.endWorld();
    }

    /** Starts the GPU timer for screen composition; a no-op unless automatic quality is active. */
    static void beginLevelGpu() {
        processPendingReset();
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
     * the render thread, so a reset requested by another thread is applied on the next render-thread call.
     */
    static void reset() {
        resetPending = true;
    }

    private static boolean autoQuality() {
        return QuasarClientConfig.qualityMode().fixedTier() == null;
    }

    private static void processPendingReset() {
        if (!resetPending) {
            return;
        }
        resetPending = false;
        governor = null;
        worldRenderNanos = 0L;
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
