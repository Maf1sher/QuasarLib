package mafisher.quasar.content;

import mafisher.quasar.api.gameplay.RegisterGameplayEffectTypesEvent;
import mafisher.quasar.content.blackhole.ContentBlackHoleGameplayController;
import mafisher.quasar.content.orbital.ContentOrbitalLaserGameplayController;

/** Registers content-owned gameplay controllers for persistence resume. */
public final class ContentGameplayEffects {
    private ContentGameplayEffects() {
    }

    public static void registerTypes(RegisterGameplayEffectTypesEvent event) {
        event.register(
            ContentEffectIds.BLACK_HOLE,
            descriptor -> new ContentBlackHoleGameplayController()
        );
        event.register(
            ContentEffectIds.ORBITAL_LASER,
            descriptor -> new ContentOrbitalLaserGameplayController()
        );
    }
}
