package mafisher.quasar;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.jetbrains.annotations.ApiStatus;

/** Validates cross-field server config relationships after each load. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@ApiStatus.Internal
public final class QuasarConfigEvents {
    private QuasarConfigEvents() {
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == QuasarConfig.SPEC) {
            QuasarConfig.validate();
        }
    }
}
