package mafisher.quasar.content.blackhole;

import mafisher.quasar.api.effect.budget.EffectBudgets;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.content.ContentEffectIds;

/** Builds the gameplay session spec that mirrors a content black-hole visual. */
public final class ContentBlackHoleGameplaySpec {
    private ContentBlackHoleGameplaySpec() {
    }

    public static GameplayEffectSpec of(VisualEffectSpec visual) {
        int duration = visual.durationTicks();
        int form = Math.max(1, Math.round(ContentBlackHoleCinematics.INTRO_END * duration));
        int open = Math.max(1, Math.round((ContentBlackHoleCinematics.DESTABILISE
            - ContentBlackHoleCinematics.INTRO_END) * duration));
        int collapse = Math.max(1, Math.round((ContentBlackHoleCinematics.IMPLOSION
            - ContentBlackHoleCinematics.DESTABILISE) * duration));
        int aftermath = Math.max(1, duration - form - open - collapse);
        EffectPhasePlan phases = EffectPhasePlan.of(
            new EffectPhase("form", form),
            new EffectPhase("open", open),
            new EffectPhase("collapse", collapse),
            new EffectPhase("aftermath", aftermath)
        );
        int blockChanges = Math.max(512, Math.min(4_096, EffectBudgets.blockChangesPerLevelTick()));
        return new GameplayEffectSpec(
            ContentEffectIds.BLACK_HOLE,
            null,
            visual.position(),
            phases,
            visual.seed(),
            new EffectTickBudget(384, 4, 0, blockChanges, 16_384, 128, 1_024,
                Math.max(1, EffectBudgets.sectionEditsPerLevelTick())),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE,
            visual.audio(),
            EffectPriority.CRITICAL,
            visual.scale()
        );
    }
}
