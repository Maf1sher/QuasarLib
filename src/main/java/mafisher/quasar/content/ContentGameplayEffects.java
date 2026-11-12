package mafisher.quasar.content;

import mafisher.quasar.api.gameplay.RegisterGameplayEffectTypesEvent;
import mafisher.quasar.content.blackhole.ContentBlackHoleGameplayController;

/** Registers content-owned gameplay controllers for persistence resume. */
public final class ContentGameplayEffects {
    private ContentGameplayEffects() {
    }

    public static void registerTypes(RegisterGameplayEffectTypesEvent event) {
        event.register(
            ContentEffectIds.BLACK_HOLE,
            descriptor -> new ContentBlackHoleGameplayController()
        );
    }
}
