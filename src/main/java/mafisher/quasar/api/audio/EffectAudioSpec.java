package mafisher.quasar.api.audio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.api.effect.EffectTimeline;
import org.jspecify.annotations.Nullable;

/**
 * Immutable server-authored audio declaration for one effect. Cues use effect-local ticks, so the
 * client can schedule them against the effect's absolute start time. A builder created from an
 * {@link EffectPhaseTiming} resolves phase-relative cues to absolute effect ticks on the server.
 * Named triggers are one-shot sounds fired on demand through {@link EffectAudioService}.
 */
public final class EffectAudioSpec {
    public static final int MAX_CUES = 128;
    public static final int MAX_TRIGGERS = 32;
    public static final EffectAudioSpec NONE = new EffectAudioSpec(List.of(), List.of());

    private final List<EffectAudioCue> cues;
    private final List<EffectAudioTrigger> triggers;

    private EffectAudioSpec(List<EffectAudioCue> cues, List<EffectAudioTrigger> triggers) {
        if (cues.size() > MAX_CUES) {
            throw new IllegalArgumentException("An effect audio spec supports at most " + MAX_CUES + " cues");
        }
        if (triggers.size() > MAX_TRIGGERS) {
            throw new IllegalArgumentException(
                "An effect audio spec supports at most " + MAX_TRIGGERS + " triggers"
            );
        }
        List<EffectAudioCue> sorted = new ArrayList<>(cues.size());
        for (EffectAudioCue cue : cues) {
            sorted.add(Objects.requireNonNull(cue, "cue"));
        }
        sorted.sort(Comparator.comparingInt(EffectAudioCue::tick));
        this.cues = List.copyOf(sorted);

        List<EffectAudioTrigger> copiedTriggers = new ArrayList<>(triggers.size());
        Set<String> ids = new LinkedHashSet<>();
        for (EffectAudioTrigger trigger : triggers) {
            Objects.requireNonNull(trigger, "trigger");
            if (!ids.add(trigger.id())) {
                throw new IllegalArgumentException("Duplicate effect audio trigger ID: " + trigger.id());
            }
            copiedTriggers.add(trigger);
        }
        this.triggers = List.copyOf(copiedTriggers);
    }

    public static EffectAudioSpec of(EffectAudioCue... cues) {
        Objects.requireNonNull(cues, "cues");
        return of(List.of(cues));
    }

    public static EffectAudioSpec of(List<EffectAudioCue> cues) {
        return of(cues, List.of());
    }

    public static EffectAudioSpec of(List<EffectAudioCue> cues, List<EffectAudioTrigger> triggers) {
        Objects.requireNonNull(cues, "cues");
        Objects.requireNonNull(triggers, "triggers");
        return cues.isEmpty() && triggers.isEmpty()
            ? NONE
            : new EffectAudioSpec(cues, triggers);
    }

    public List<EffectAudioCue> cues() {
        return this.cues;
    }

    public int cueCount() {
        return this.cues.size();
    }

    public List<EffectAudioTrigger> triggers() {
        return this.triggers;
    }

    public int triggerCount() {
        return this.triggers.size();
    }

    /** Returns the trigger declaration with {@code id}, or null when absent. */
    @Nullable
    public EffectAudioTrigger triggerOrNull(String id) {
        Objects.requireNonNull(id, "id");
        for (EffectAudioTrigger trigger : this.triggers) {
            if (trigger.id().equals(id)) {
                return trigger;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return this.cues.isEmpty() && this.triggers.isEmpty();
    }

    /** True when every cue tick and loop window fits before {@code durationTicks}. */
    public boolean fitsWithin(int durationTicks) {
        if (this.cues.isEmpty()) {
            return true;
        }
        if (durationTicks < 1) {
            return false;
        }
        for (EffectAudioCue cue : this.cues) {
            if (cue.tick() >= durationTicks || !cue.envelope().isValidFor(cue.tick(), durationTicks)) {
                return false;
            }
        }
        return true;
    }

    /** Throws when a cue would never play inside {@code durationTicks}. */
    public void requireFitsWithin(int durationTicks) {
        if (!fitsWithin(durationTicks)) {
            throw new IllegalArgumentException(
                "Effect audio cue must be before duration " + durationTicks
            );
        }
    }

    public static Builder builder(int durationTicks) {
        return new Builder(durationTicks, null);
    }

    public static Builder builder(EffectPhaseTiming phases) {
        Objects.requireNonNull(phases, "phases");
        return new Builder(phases.totalDurationTicks(), phases);
    }

    public static final class Builder {
        private final int durationTicks;
        private final EffectPhaseTiming phases;
        private final List<EffectAudioCue> cues = new ArrayList<>();
        private final List<EffectAudioTrigger> triggers = new ArrayList<>();

        private Builder(int durationTicks, EffectPhaseTiming phases) {
            if (!EffectTimeline.isValidDuration(durationTicks)) {
                throw new IllegalArgumentException(
                    "Effect audio duration must be between 1 and " + EffectTimeline.MAX_DURATION_TICKS + " ticks"
                );
            }
            this.durationTicks = durationTicks;
            this.phases = phases;
        }

        public Builder cue(EffectAudioCue cue) {
            this.add(Objects.requireNonNull(cue, "cue"));
            return this;
        }

        public Builder phaseCue(int phaseIndex, int offsetTicks, EffectAudioCue cue) {
            return this.cue(this.shiftTo(this.phaseTick(phaseIndex, offsetTicks), cue));
        }

        public Builder phaseCue(String phaseName, int offsetTicks, EffectAudioCue cue) {
            return this.phaseCue(this.phaseIndex(phaseName), offsetTicks, cue);
        }

        public Builder trigger(EffectAudioTrigger trigger) {
            Objects.requireNonNull(trigger, "trigger");
            if (this.triggers.size() >= MAX_TRIGGERS) {
                throw new IllegalArgumentException(
                    "An effect audio spec supports at most " + MAX_TRIGGERS + " triggers"
                );
            }
            for (EffectAudioTrigger existing : this.triggers) {
                if (existing.id().equals(trigger.id())) {
                    throw new IllegalArgumentException("Duplicate effect audio trigger ID: " + trigger.id());
                }
            }
            this.triggers.add(trigger);
            return this;
        }

        public EffectAudioSpec build() {
            return this.cues.isEmpty() && this.triggers.isEmpty()
                ? NONE
                : new EffectAudioSpec(this.cues, this.triggers);
        }

        private void add(EffectAudioCue cue) {
            Objects.requireNonNull(cue, "cue");
            if (cue.tick() >= this.durationTicks) {
                throw new IllegalArgumentException(
                    "Effect audio cue at tick " + cue.tick() + " must be before duration " + this.durationTicks
                );
            }
            cue.envelope().requireValidFor(cue.tick(), this.durationTicks);
            if (this.cues.size() >= MAX_CUES) {
                throw new IllegalArgumentException(
                    "An effect audio spec supports at most " + MAX_CUES + " cues"
                );
            }
            this.cues.add(cue);
        }

        private EffectAudioCue shiftTo(int startTick, EffectAudioCue cue) {
            Objects.requireNonNull(cue, "cue");
            EffectAudioEnvelope envelope = cue.envelope();
            int stopTick = envelope.stopTick();
            if (stopTick != EffectAudioEnvelope.END_OF_EFFECT) {
                long shiftedStop = (long)stopTick + (startTick - cue.tick());
                if (shiftedStop > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Phase cue stop tick overflows the effect timeline");
                }
                envelope = new EffectAudioEnvelope(
                    true,
                    envelope.fadeInTicks(),
                    envelope.fadeOutTicks(),
                    (int)shiftedStop
                );
            }
            return new EffectAudioCue(
                startTick,
                cue.position(),
                cue.soundId(),
                cue.source(),
                cue.volume(),
                cue.pitch(),
                cue.attenuation(),
                envelope,
                cue.motion()
            );
        }

        private int phaseTick(int phaseIndex, int offsetTicks) {
            if (this.phases == null) {
                throw new IllegalStateException("Phase cues require a phase plan");
            }
            if (offsetTicks < 0) {
                throw new IllegalArgumentException("Phase cue offsets cannot be negative");
            }
            long tick = (long)this.phases.phaseStartTick(phaseIndex) + offsetTicks;
            if (tick > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Phase cue tick overflows the effect timeline");
            }
            return (int)tick;
        }

        private int phaseIndex(String phaseName) {
            if (this.phases == null) {
                throw new IllegalStateException("Phase cues require a phase plan");
            }
            Objects.requireNonNull(phaseName, "phaseName");
            int found = -1;
            for (int index = 0; index < this.phases.phaseCount(); index++) {
                if (this.phases.phaseName(index).equals(phaseName)) {
                    if (found >= 0) {
                        throw new IllegalArgumentException("Effect phase name is not unique: " + phaseName);
                    }
                    found = index;
                }
            }
            if (found < 0) {
                throw new IllegalArgumentException("Unknown effect phase: " + phaseName);
            }
            return found;
        }
    }
}
