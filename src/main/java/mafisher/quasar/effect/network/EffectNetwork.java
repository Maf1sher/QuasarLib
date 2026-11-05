package mafisher.quasar.effect.network;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.client.effect.ClientVisualEffectManager;
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
        var registrar = event.registrar("1");
        registrar.playToClient(StartVisualEffectPayload.TYPE, StartVisualEffectPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientVisualEffectManager.start(payload));
                }
            });
        registrar.playToClient(StopVisualEffectPayload.TYPE, StopVisualEffectPayload.STREAM_CODEC,
            (payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    context.enqueueWork(() -> ClientVisualEffectManager.stop(payload));
                }
            });
    }
}
