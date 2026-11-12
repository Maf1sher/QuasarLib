package mafisher.quasar.effect;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.gameplay.GameplayEffectEndReason;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Clears every per-level effect runtime when its level or the server goes away. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class EffectLevelLifecycleEvents {
    private EffectLevelLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EffectLevelLifecycle.clear(level, EffectRemovalPolicy.KEEP_FOR_RESTORE,
                GameplayEffectEndReason.LEVEL_UNLOAD);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            EffectLevelLifecycle.clear(level, EffectRemovalPolicy.KEEP_FOR_RESTORE,
                GameplayEffectEndReason.SERVER_STOPPING);
        }
    }
}
