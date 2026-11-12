package mafisher.quasar.api.gameplay;

import java.util.List;
import java.util.Objects;
import mafisher.quasar.api.audio.EffectPhaseTiming;
import mafisher.quasar.api.effect.EffectTimeline;

/** Immutable ordered phase plan; phase membership is derived from effect age. */
public final class EffectPhasePlan implements EffectPhaseTiming {
    public static final int MAX_DURATION_TICKS = EffectTimeline.MAX_DURATION_TICKS;

    private final List<EffectPhase> phases;
    private final int[] phaseStarts;
    private final int totalDurationTicks;

    private EffectPhasePlan(List<EffectPhase> phases) {
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("An effect phase plan needs at least one phase");
        }

        this.phases = List.copyOf(phases);
        this.phaseStarts = new int[phases.size()];
        long total = 0L;
        for (int index = 0; index < phases.size(); index++) {
            EffectPhase phase = Objects.requireNonNull(phases.get(index), "phase");
            this.phaseStarts[index] = (int)total;
            total += phase.durationTicks();
            if (total > MAX_DURATION_TICKS) {
                throw new IllegalArgumentException(
                    "Effect phase plan exceeds " + MAX_DURATION_TICKS + " ticks"
                );
            }
        }
        this.totalDurationTicks = (int)total;
    }

    public static EffectPhasePlan of(EffectPhase... phases) {
        Objects.requireNonNull(phases, "phases");
        return new EffectPhasePlan(List.of(phases));
    }

    public static EffectPhasePlan of(List<EffectPhase> phases) {
        Objects.requireNonNull(phases, "phases");
        return new EffectPhasePlan(phases);
    }

    public List<EffectPhase> phases() {
        return this.phases;
    }

    public int phaseCount() {
        return this.phases.size();
    }

    public int totalDurationTicks() {
        return this.totalDurationTicks;
    }

    /** Returns the phase index for an age, or -1 once the plan is finished. */
    public int phaseIndexAt(int ageTicks) {
        if (ageTicks < 0) {
            throw new IllegalArgumentException("Effect age cannot be negative");
        }
        if (ageTicks >= this.totalDurationTicks) {
            return -1;
        }
        for (int index = this.phases.size() - 1; index >= 0; index--) {
            if (ageTicks >= this.phaseStarts[index]) {
                return index;
            }
        }
        return -1;
    }

    public int phaseStartTick(int phaseIndex) {
        requirePhaseIndex(phaseIndex);
        return this.phaseStarts[phaseIndex];
    }

    @Override
    public String phaseName(int phaseIndex) {
        requirePhaseIndex(phaseIndex);
        return this.phases.get(phaseIndex).name();
    }

    public int phaseDurationTicks(int phaseIndex) {
        requirePhaseIndex(phaseIndex);
        return this.phases.get(phaseIndex).durationTicks();
    }

    public int phaseAgeTicks(int ageTicks) {
        int phaseIndex = this.phaseIndexAt(ageTicks);
        return phaseIndex < 0 ? 0 : ageTicks - this.phaseStarts[phaseIndex];
    }

    public float phaseProgress(int ageTicks) {
        int phaseIndex = this.phaseIndexAt(ageTicks);
        if (phaseIndex < 0) {
            return 1.0F;
        }
        return Math.min(1.0F, this.phaseAgeTicks(ageTicks) / (float)this.phaseDurationTicks(phaseIndex));
    }

    private void requirePhaseIndex(int phaseIndex) {
        if (phaseIndex < 0 || phaseIndex >= this.phases.size()) {
            throw new IllegalArgumentException("Effect phase index is out of range");
        }
    }
}
