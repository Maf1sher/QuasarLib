package mafisher.quasar.effect.gameplay;

import mafisher.quasar.QuasarLib;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Closes gameplay sessions when their level or the server goes away. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class GameplayEffectLifecycleEvents {
    private GameplayEffectLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GameplayEffectService.stopAll(level, GameplayEffectEndReason.LEVEL_UNLOAD);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            GameplayEffectService.stopAll(level, GameplayEffectEndReason.SERVER_STOPPING);
        }
    }
}
