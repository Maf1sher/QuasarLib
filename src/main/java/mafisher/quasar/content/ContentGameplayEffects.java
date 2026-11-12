package mafisher.quasar.content;

import mafisher.quasar.api.gameplay.RegisterGameplayEffectTypesEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Registers content-owned gameplay controllers for persistence resume. */
@EventBusSubscriber(modid = ContentBootstrap.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ContentGameplayEffects {
    private ContentGameplayEffects() {
    }

    @SubscribeEvent
    public static void registerTypes(RegisterGameplayEffectTypesEvent event) {
        event.register(
            ContentEffectIds.BLACK_HOLE,
            descriptor -> new ContentBlackHoleGameplayController()
        );
    }
}
