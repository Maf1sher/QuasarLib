package mafisher.quasar.content.orbital;

import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.gameplay.CompanionSpec;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import org.jspecify.annotations.Nullable;

/** Builds the gameplay session, companion, and preview specs for the orbital laser. */
public final class ContentOrbitalLaserSpecs {
    private ContentOrbitalLaserSpecs() {
    }

    /** Builds the six-phase gameplay session anchored at the resolved surface column. */
    public static GameplayEffectSpec gameplay(
        @Nullable UUID ownerId,
        TargetColumn target,
        int radius,
        long seed
    ) {
        return new GameplayEffectSpec(
            ContentEffectIds.ORBITAL_LASER,
            ownerId,
            target.center(),
            ContentOrbitalLaserSettings.phasePlan(),
            seed,
            new EffectTickBudget(0, 0, 0, 0),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE,
            EffectAudioSpec.NONE,
            EffectPriority.NORMAL,
            ContentOrbitalLaserSettings.requireRadius(radius)
        );
    }

    /** Builds the one showpiece companion owned by a gameplay session. */
    public static CompanionSpec companion() {
        return CompanionSpec.builder(
                ContentEffectIds.ORBITAL_LASER, ContentOrbitalLaserSettings.AUDIENCE
            )
            .offset(0.0, ContentOrbitalLaserSettings.MARKER_LIFT, 0.0)
            .scale(1.0F)
            .priority(EffectPriority.NORMAL)
            .lifetime(EffectLifetimePolicy.CANCEL_WITH_SCOPE)
            .build();
    }

    /** Builds the visual-only marker preview used by {@code orbital-laser visual}. */
    public static VisualEffectSpec preview(TargetColumn target, int radius, long seed) {
        return VisualEffectSpec.builder(
                ContentEffectIds.ORBITAL_LASER,
                target.markerPosition(),
                ContentOrbitalLaserSettings.TOTAL_TICKS,
                seed,
                ContentOrbitalLaserSettings.AUDIENCE
            )
            .scale(ContentOrbitalLaserSettings.requireRadius(radius))
            .build();
    }
}
