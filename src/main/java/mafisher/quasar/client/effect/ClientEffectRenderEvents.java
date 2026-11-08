package mafisher.quasar.client.effect;

import mafisher.quasar.QuasarLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientEffectRenderEvents {
    private ClientEffectRenderEvents() {
    }

    @SubscribeEvent
    public static void renderEffects(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
            || event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES
            || event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ClientEffectFrameRuntime.render(event);
        }
    }
}
