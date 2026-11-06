package mafisher.quasar.effect.network;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.client.effect.ClientEffectRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class EffectNetwork {
    private EffectNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("6");
        registrar.playToClient(ResumeEffectsPayload.TYPE, ResumeEffectsPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.resume(payload));
                }
            });
        registrar.playToClient(ArmScreenCompositorFailurePayload.TYPE,
            ArmScreenCompositorFailurePayload.STREAM_CODEC, (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(ClientEffectRuntime::armScreenCompositorFailureOnce);
                }
            });
        registrar.playToClient(StartVisualEffectPayload.TYPE, StartVisualEffectPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.start(payload));
                }
            });
        registrar.playToClient(UpdateVisualEffectPayload.TYPE, UpdateVisualEffectPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.update(payload));
                }
            });
        registrar.playToClient(UpdateSegmentedVisualEffectPayload.TYPE,
            UpdateSegmentedVisualEffectPayload.STREAM_CODEC, (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.updateSegmented(payload));
                }
            });
        registrar.playToClient(StopVisualEffectPayload.TYPE, StopVisualEffectPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.stop(payload));
                }
            });
        registrar.playToClient(EffectAudioPlanPayload.TYPE, EffectAudioPlanPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.effectAudioPlan(payload));
                }
            });
        registrar.playToClient(StopEffectAudioPayload.TYPE, StopEffectAudioPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.stopEffectAudio(payload));
                }
            });
    }
}
