package mafisher.quasar.effect.block;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.block.RegisterBlockProtectionsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.ApiStatus;

/** Fires consumer block-protection registration and then freezes the registry. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@ApiStatus.Internal
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
