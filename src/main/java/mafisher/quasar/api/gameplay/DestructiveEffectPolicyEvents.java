package mafisher.quasar.api.gameplay;

import mafisher.quasar.QuasarLib;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Registers the config policy, fires consumer policy registration, then freezes the registry. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DestructiveEffectPolicyEvents {
    private DestructiveEffectPolicyEvents() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DestructiveEffectPolicies.registerConfigPolicy();
            try {
                NeoForge.EVENT_BUS.post(new RegisterDestructiveEffectPoliciesEvent());
            } finally {
                DestructiveEffectPolicies.freeze();
            }
        });
    }
}
