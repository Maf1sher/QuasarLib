package mafisher.quasar.api.gameplay;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.gameplay.GameplayEffectTypeRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Fires the gameplay effect type registration event after the common setup phase. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GameplayEffectTypeEvents {
    private GameplayEffectTypeEvents() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                NeoForge.EVENT_BUS.post(new RegisterGameplayEffectTypesEvent());
            } finally {
                GameplayEffectTypeRegistry.freeze();
            }
        });
    }
}
