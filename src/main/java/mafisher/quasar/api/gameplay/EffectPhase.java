package mafisher.quasar.api.gameplay;

import mafisher.quasar.api.effect.EffectTimeline;

/** One named, bounded phase in a gameplay effect timeline. */
public record EffectPhase(String name, int durationTicks) {
    public EffectPhase {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Effect phase name cannot be blank");
        }
        if (!EffectTimeline.isValidDuration(durationTicks)) {
            throw new IllegalArgumentException(
                "Effect phase duration must be between 1 and " + EffectTimeline.MAX_DURATION_TICKS + " ticks"
            );
        }
    }
}
