package mafisher.quasar.content;

import mafisher.quasar.api.client.RegisterScreenEffectOperationsEvent;
import mafisher.quasar.api.client.RegisterVisualEffectRenderersEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only content renderer registration through the public QuasarLib SPI. */
@EventBusSubscriber(modid = ContentBootstrap.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ContentClientEffects {
    private ContentClientEffects() {
    }

    @SubscribeEvent
    public static void registerOperations(RegisterScreenEffectOperationsEvent event) {
        event.register(ContentScreenOperations.RUNE_GLOW, new ContentRuneGlowOperation());
        event.register(ContentScreenOperations.BLACK_HOLE, new ContentBlackHoleOperation());
    }

    @SubscribeEvent
    public static void registerRenderers(RegisterVisualEffectRenderersEvent event) {
        event.register(ContentEffectIds.RUNE_CIRCLE, new ContentRuneCircleRenderer());
        event.register(ContentEffectIds.BLACK_HOLE, new ContentBlackHoleRenderer());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            ContentBlackHoleImpulses.clear();
        }
    }
}
