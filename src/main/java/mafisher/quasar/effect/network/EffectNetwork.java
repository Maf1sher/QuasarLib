package mafisher.quasar.effect.network;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.scale.StartSegmentedVisualEffectPayload;
import mafisher.quasar.client.effect.ClientEffectRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.ApiStatus;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@ApiStatus.Internal
public final class EffectNetwork {
    private EffectNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("16");
        registrar.playToClient(ResumeEffectsPayload.TYPE, ResumeEffectsPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.resume(payload));
                }
            });
        registrar.playToClient(StartVisualEffectPayload.TYPE, StartVisualEffectPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.start(payload));
                }
            });
        registrar.playToClient(EffectTransformBatchPayload.TYPE, EffectTransformBatchPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.updateBatch(payload));
                }
            });
        registrar.playToClient(UpdateVisualEffectPayload.TYPE, UpdateVisualEffectPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.update(payload));
                }
            });
        registrar.playToClient(EffectParameterBatchPayload.TYPE,
            EffectParameterBatchPayload.STREAM_CODEC, (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.updateParameters(payload));
                }
            });
        registrar.playToClient(StartSegmentedVisualEffectPayload.TYPE,
            StartSegmentedVisualEffectPayload.STREAM_CODEC, (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.startSegmented(payload));
                }
            });
        registrar.playToClient(UpdateSegmentedVisualEffectPayload.TYPE,
            UpdateSegmentedVisualEffectPayload.STREAM_CODEC, (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.updateSegmented(payload));
                }
            });
        registrar.playToClient(StopSegmentedVisualEffectPayload.TYPE,
            StopSegmentedVisualEffectPayload.STREAM_CODEC, (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.stopSegmented(payload));
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
        registrar.playToClient(EffectAudioTriggerPayload.TYPE, EffectAudioTriggerPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.effectAudioTrigger(payload));
                }
            });
        registrar.playToClient(EffectDebrisPayload.TYPE, EffectDebrisPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientEffectRuntime.effectDebris(payload));
                }
            });
    }
}
