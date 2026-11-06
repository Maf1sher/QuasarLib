package mafisher.quasar.effect;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.cinematic.CinematicEffectService;
import mafisher.quasar.effect.gameplay.GameplayEffectService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Ticks world visuals first, then segmented, gameplay, and cinematic sessions, on the server thread. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class EffectTickHandler {
    private EffectTickHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            VisualEffectService.tick(serverLevel);
            SegmentedVisualEffectService.tick(serverLevel);
            GameplayEffectService.tick(serverLevel);
            CinematicEffectService.tick(serverLevel);
        }
    }
}
