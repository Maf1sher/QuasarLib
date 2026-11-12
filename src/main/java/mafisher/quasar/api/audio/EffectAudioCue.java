package mafisher.quasar.api.audio;

import java.util.Objects;
import mafisher.quasar.effect.EffectSoundValidation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
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

    /** Convenience factory for a one-shot cue that uses vanilla attenuation. */
    public static EffectAudioCue at(
        int tick,
        Vec3 position,
        SoundEvent sound,
        SoundSource source,
        float volume,
        float pitch
    ) {
        return at(tick, position, sound, source, volume, pitch, EffectAudioAttenuation.VANILLA);
    }

    /** Convenience factory for a resource-location cue that uses vanilla attenuation. */
    public static EffectAudioCue at(
        int tick,
        Vec3 position,
        ResourceLocation soundId,
        SoundSource source,
        float volume,
        float pitch
    ) {
        return at(tick, position, soundId, source, volume, pitch, EffectAudioAttenuation.VANILLA);
    }

    /** Convenience factory for a resource-location cue with explicit attenuation. */
    public static EffectAudioCue at(
        int tick,
        Vec3 position,
        ResourceLocation soundId,
        SoundSource source,
        float volume,
        float pitch,
        EffectAudioAttenuation attenuation
    ) {
        return new EffectAudioCue(tick, position, soundId, source, volume, pitch, attenuation);
    }

    /** Convenience factory for a one-shot cue with explicit attenuation. */
    public static EffectAudioCue at(
        int tick,
        Vec3 position,
        SoundEvent sound,
        SoundSource source,
        float volume,
        float pitch,
        EffectAudioAttenuation attenuation
    ) {
        Objects.requireNonNull(sound, "sound");
        return new EffectAudioCue(
            tick, position, sound.getLocation(), source, volume, pitch, attenuation
        );
    }
}
