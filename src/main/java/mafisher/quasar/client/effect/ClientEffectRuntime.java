package mafisher.quasar.client.effect;

import mafisher.quasar.effect.EffectQualityTier;
import mafisher.quasar.effect.network.EffectAudioPlanPayload;
import mafisher.quasar.effect.network.ResumeEffectsPayload;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopEffectAudioPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;

/**
 * Narrow, descriptor-safe entry point for payload handlers. Its method signatures use only common
 * types, so a dedicated server may load this class without loading client-only Minecraft types.
 * Client rendering and lifecycle work lives in {@link ClientEffectFrameRuntime}.
 */
public final class ClientEffectRuntime {
    private ClientEffectRuntime() {
    }

    public static void start(StartVisualEffectPayload payload) {
        ClientEffectFrameRuntime.start(payload);
    }

    public static void resume(ResumeEffectsPayload payload) {
        ClientEffectFrameRuntime.resume(payload);
    }

    public static void stop(StopVisualEffectPayload payload) {
        ClientEffectFrameRuntime.stop(payload);
    }

    public static void update(UpdateVisualEffectPayload payload) {
        ClientEffectFrameRuntime.update(payload);
    }

    public static void updateSegmented(UpdateSegmentedVisualEffectPayload payload) {
        ClientEffectFrameRuntime.updateSegmented(payload);
    }

    public static void effectAudioPlan(EffectAudioPlanPayload payload) {
        ClientEffectFrameRuntime.effectAudioPlan(payload);
    }

    public static void stopEffectAudio(StopEffectAudioPayload payload) {
        ClientEffectFrameRuntime.stopEffectAudio(payload);
    }

    /** Returns the quality tier currently used for client presentation. */
    public static EffectQualityTier qualityTier() {
        return ClientQualityController.tier();
    }

    public static boolean armScreenCompositorFailureOnce() {
        return ClientEffectFrameRuntime.armScreenCompositorFailureOnce();
    }
}
