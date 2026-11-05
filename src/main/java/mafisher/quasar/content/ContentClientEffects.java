package mafisher.quasar.content;

import mafisher.quasar.client.effect.RegisterVisualEffectRenderersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Client-only content renderer registration through the public QuasarLib SPI. */
@EventBusSubscriber(modid = ContentBootstrap.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ContentClientEffects {
    private ContentClientEffects() {
    }

    @SubscribeEvent
    public static void registerRenderers(RegisterVisualEffectRenderersEvent event) {
        event.register(ContentEffectIds.RUNE_CIRCLE, new ContentRuneCircleRenderer());
    }
}
