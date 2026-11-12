package mafisher.quasar.api.cinematic;

import java.util.Objects;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import mafisher.quasar.effect.EffectSoundValidation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * One ordered action on a cinematic timeline. Cue ticks are relative to the timeline start and are validated
 * against the timeline duration when the timeline is built.
 */
public sealed interface CinematicCue
    permits CinematicCue.SoundCue,
    CinematicCue.VisualStartCue,
    CinematicCue.VisualStopCue,
    CinematicCue.SegmentedStartCue,
    CinematicCue.SegmentedStopCue {

    int tick();

    /** Play a positional sound once. The server owns the cue; clients hear it when the packet arrives. */
    record SoundCue(
        int tick,
        Vec3 position,
        SoundEvent sound,
        SoundSource source,
        float volume,
        float pitch
    ) implements CinematicCue {
        public SoundCue {
            requireTick(tick);
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(sound, "sound");
            Objects.requireNonNull(source, "source");
            EffectSoundValidation.requirePlayable(position, volume, pitch, "Invalid cinematic sound cue");
        }
    }

    /** Start one world visual under a timeline-local key. */
    record VisualStartCue(int tick, String key, VisualEffectSpec spec) implements CinematicCue {
        public VisualStartCue {
            requireTick(tick);
            key = requireKey(key);
            Objects.requireNonNull(spec, "spec");
        }
    }

    /** Stop the visual started under a timeline-local key. */
    record VisualStopCue(int tick, String key) implements CinematicCue {
        public VisualStopCue {
            requireTick(tick);
            key = requireKey(key);
        }
    }

    /** Start one segmented world visual under a timeline-local key. */
    record SegmentedStartCue(
        int tick,
        String key,
        SegmentedVisualEffectSpec spec
    ) implements CinematicCue {
        public SegmentedStartCue {
            requireTick(tick);
            key = requireKey(key);
            Objects.requireNonNull(spec, "spec");
        }
    }

    /** Stop the segmented visual started under a timeline-local key. */
    record SegmentedStopCue(int tick, String key) implements CinematicCue {
        public SegmentedStopCue {
            requireTick(tick);
            key = requireKey(key);
        }
    }

    static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank() || key.length() > 64) {
            throw new IllegalArgumentException("Cinematic keys must be 1 to 64 non-blank characters");
        }
        return key;
    }

    static void requireTick(int tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("Cinematic cue ticks cannot be negative");
        }
    }
}
