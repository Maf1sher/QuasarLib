package mafisher.quasar.effect.audio;

import java.util.Objects;
import mafisher.quasar.effect.EffectSoundValidation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** One client-scheduled sound event, with a tick relative to the owning effect's start. */
public record EffectAudioCue(
    int tick,
    Vec3 position,
    ResourceLocation soundId,
    SoundSource source,
    float volume,
    float pitch,
    EffectAudioAttenuation attenuation,
    EffectAudioEnvelope envelope,
    EffectAudioMotion motion
) {
    public EffectAudioCue {
        if (tick < 0) {
            throw new IllegalArgumentException("Effect audio cue ticks cannot be negative");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(soundId, "soundId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(attenuation, "attenuation");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(motion, "motion");
        EffectSoundValidation.requirePlayable(position, volume, pitch, "Invalid effect audio cue");
        if (envelope.looping() && envelope.stopTick() != EffectAudioEnvelope.END_OF_EFFECT
            && envelope.stopTick() <= tick) {
            throw new IllegalArgumentException("Looping audio cues must stop after their start tick");
        }
    }

    public EffectAudioCue(
        int tick,
        Vec3 position,
        ResourceLocation soundId,
        SoundSource source,
        float volume,
        float pitch,
        EffectAudioAttenuation attenuation
    ) {
        this(tick, position, soundId, source, volume, pitch, attenuation,
            EffectAudioEnvelope.ONE_SHOT, EffectAudioMotion.ANCHORED);
    }

    public EffectAudioCue(
        int tick,
        Vec3 position,
        ResourceLocation soundId,
        SoundSource source,
        float volume,
        float pitch
    ) {
        this(tick, position, soundId, source, volume, pitch, EffectAudioAttenuation.VANILLA);
    }
}
