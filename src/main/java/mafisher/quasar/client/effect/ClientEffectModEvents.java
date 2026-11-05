package mafisher.quasar.client.effect;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.entity.QuasarEffectEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEffectModEvents {
    private ClientEffectModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(QuasarEffectEntities.EFFECT_PROBE.get(), EffectProbeRenderer::new);
        VisualEffectRendererRegistry.registerBuiltIns();
    }
}
