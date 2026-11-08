package mafisher.quasar.compat;

import mafisher.quasar.QuasarLib;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Fires consumer bridge registration during common setup and then freezes it. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CompatBridgeEvents {
    private CompatBridgeEvents() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                NeoForge.EVENT_BUS.post(new RegisterCompatBridgesEvent());
            } finally {
                CompatRegistry.freezeBridges();
            }
        });
    }
}
