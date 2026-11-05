package mafisher.quasar.effect.gameplay;

/** One named, bounded phase in a gameplay effect timeline. */
public record EffectPhase(String name, int durationTicks) {
    public EffectPhase {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Effect phase name cannot be blank");
        }
        if (durationTicks < 1 || durationTicks > EffectPhasePlan.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Effect phase duration must be between 1 and 72000 ticks");
        }
    }
}
