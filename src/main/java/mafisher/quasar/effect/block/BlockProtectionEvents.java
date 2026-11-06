package mafisher.quasar.effect.block;

import mafisher.quasar.QuasarLib;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Fires consumer block-protection registration and then freezes the registry. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class BlockProtectionEvents {
    private BlockProtectionEvents() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                NeoForge.EVENT_BUS.post(new RegisterBlockProtectionsEvent());
            } finally {
                BlockProtectionService.freeze();
            }
        });
    }
}
