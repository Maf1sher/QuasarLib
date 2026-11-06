package mafisher.quasar.effect.audio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable server-authored audio declaration for one effect. Cues use effect-local ticks, so the
 * client can schedule them against the effect's absolute start time. A builder created from an
 * {@link EffectPhaseTiming} resolves phase-relative cues to absolute effect ticks on the server.
 */
public final class EffectAudioSpec {
    public static final int MAX_CUES = 128;
    public static final EffectAudioSpec NONE = new EffectAudioSpec(List.of());

    private final List<EffectAudioCue> cues;

    private EffectAudioSpec(List<EffectAudioCue> cues) {
        if (cues.size() > MAX_CUES) {
            throw new IllegalArgumentException("An effect audio spec supports at most " + MAX_CUES + " cues");
        }
        List<EffectAudioCue> sorted = new ArrayList<>(cues.size());
        for (EffectAudioCue cue : cues) {
            sorted.add(Objects.requireNonNull(cue, "cue"));
        }
        sorted.sort(Comparator.comparingInt(EffectAudioCue::tick));
        this.cues = List.copyOf(sorted);
    }

    public static EffectAudioSpec of(EffectAudioCue... cues) {
        Objects.requireNonNull(cues, "cues");
        return of(List.of(cues));
    }

    public static EffectAudioSpec of(List<EffectAudioCue> cues) {
        Objects.requireNonNull(cues, "cues");
        return cues.isEmpty() ? NONE : new EffectAudioSpec(cues);
    }

    public List<EffectAudioCue> cues() {
        return this.cues;
    }

    public int cueCount() {
        return this.cues.size();
    }

    public boolean isEmpty() {
        return this.cues.isEmpty();
    }

    /** True when every cue tick is before {@code durationTicks}. */
    public boolean fitsWithin(int durationTicks) {
        if (this.cues.isEmpty()) {
            return true;
        }
        if (durationTicks < 1) {
            return false;
        }
        for (EffectAudioCue cue : this.cues) {
            if (cue.tick() >= durationTicks) {
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

        private Builder(int durationTicks, EffectPhaseTiming phases) {
            if (durationTicks < 1 || durationTicks > EffectAudioPlan.MAX_DURATION_TICKS) {
                throw new IllegalArgumentException("Effect audio duration must be between 1 and 72000 ticks");
            }
            this.durationTicks = durationTicks;
            this.phases = phases;
        }

        public Builder cue(
            int tick,
            Vec3 position,
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch,
            EffectAudioAttenuation attenuation
        ) {
            this.add(new EffectAudioCue(tick, position, soundId, source, volume, pitch, attenuation));
            return this;
        }

        public Builder cue(
            int tick,
            Vec3 position,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch,
            EffectAudioAttenuation attenuation
        ) {
            Objects.requireNonNull(sound, "sound");
            return this.cue(tick, position, sound.getLocation(), source, volume, pitch, attenuation);
        }

        public Builder cue(
            int tick,
            Vec3 position,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch
        ) {
            return this.cue(tick, position, sound, source, volume, pitch, EffectAudioAttenuation.VANILLA);
        }

        public Builder cue(
            int tick,
            Vec3 position,
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch
        ) {
            return this.cue(tick, position, soundId, source, volume, pitch, EffectAudioAttenuation.VANILLA);
        }

        public Builder phaseCue(
            int phaseIndex,
            int offsetTicks,
            Vec3 position,
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch,
            EffectAudioAttenuation attenuation
        ) {
            return this.cue(
                this.phaseTick(phaseIndex, offsetTicks),
                position,
                soundId,
                source,
                volume,
                pitch,
                attenuation
            );
        }

        public Builder phaseCue(
            int phaseIndex,
            int offsetTicks,
            Vec3 position,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch,
            EffectAudioAttenuation attenuation
        ) {
            Objects.requireNonNull(sound, "sound");
            return this.phaseCue(
                phaseIndex,
                offsetTicks,
                position,
                sound.getLocation(),
                source,
                volume,
                pitch,
                attenuation
            );
        }

        public Builder phaseCue(
            String phaseName,
            int offsetTicks,
            Vec3 position,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch,
            EffectAudioAttenuation attenuation
        ) {
            return this.phaseCue(
                this.phaseIndex(phaseName),
                offsetTicks,
                position,
                sound,
                source,
                volume,
                pitch,
                attenuation
            );
        }

        public Builder phaseCue(
            String phaseName,
            int offsetTicks,
            Vec3 position,
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch,
            EffectAudioAttenuation attenuation
        ) {
            return this.phaseCue(
                this.phaseIndex(phaseName),
                offsetTicks,
                position,
                soundId,
                source,
                volume,
                pitch,
                attenuation
            );
        }

        public Builder phaseCue(
            int phaseIndex,
            int offsetTicks,
            Vec3 position,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch
        ) {
            return this.phaseCue(
                phaseIndex, offsetTicks, position, sound, source, volume, pitch, EffectAudioAttenuation.VANILLA
            );
        }

        public Builder phaseCue(
            int phaseIndex,
            int offsetTicks,
            Vec3 position,
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch
        ) {
            return this.phaseCue(
                phaseIndex, offsetTicks, position, soundId, source, volume, pitch, EffectAudioAttenuation.VANILLA
            );
        }

        public Builder phaseCue(
            String phaseName,
            int offsetTicks,
            Vec3 position,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch
        ) {
            return this.phaseCue(
                phaseName, offsetTicks, position, sound, source, volume, pitch, EffectAudioAttenuation.VANILLA
            );
        }

        public Builder phaseCue(
            String phaseName,
            int offsetTicks,
            Vec3 position,
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch
        ) {
            return this.phaseCue(
                phaseName, offsetTicks, position, soundId, source, volume, pitch, EffectAudioAttenuation.VANILLA
            );
        }

        public EffectAudioSpec build() {
            return this.cues.isEmpty() ? NONE : new EffectAudioSpec(this.cues);
        }

        private void add(EffectAudioCue cue) {
            Objects.requireNonNull(cue, "cue");
            if (cue.tick() >= this.durationTicks) {
                throw new IllegalArgumentException(
                    "Effect audio cue at tick " + cue.tick() + " must be before duration " + this.durationTicks
                );
            }
            if (this.cues.size() >= MAX_CUES) {
                throw new IllegalArgumentException(
                    "An effect audio spec supports at most " + MAX_CUES + " cues"
                );
            }
            this.cues.add(cue);
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
