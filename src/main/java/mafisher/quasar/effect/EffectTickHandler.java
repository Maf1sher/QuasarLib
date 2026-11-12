package mafisher.quasar.effect;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.ApiStatus;

/** Ticks world visuals first, then segmented, gameplay, and cinematic sessions, on the server thread. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
@ApiStatus.Internal
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
            EffectAudioService.tick(serverLevel);
            LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(serverLevel);
            if (runtime != null) {
                runtime.tick();
            }
        }
    }
}
