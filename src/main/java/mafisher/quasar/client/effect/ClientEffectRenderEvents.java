package mafisher.quasar.client.effect;

import mafisher.quasar.QuasarLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.ApiStatus;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
@ApiStatus.Internal
public final class ClientEffectRenderEvents {
    private ClientEffectRenderEvents() {
    }

    @SubscribeEvent
    public static void renderEffects(RenderLevelStageEvent event) {
        if (handles(event.getStage())) {
            ClientEffectFrameRuntime.render(event);
        }
    }

    /** Stages the client runtime consumes, including the foreground-depth capture. */
    static boolean handles(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
            || stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
            || stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES
            || stage == RenderLevelStageEvent.Stage.AFTER_LEVEL;
    }
}
