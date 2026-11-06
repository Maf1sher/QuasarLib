package mafisher.quasar.effect.audio;

import java.util.Objects;
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
    EffectAudioAttenuation attenuation
) {
    public EffectAudioCue {
        if (tick < 0) {
            throw new IllegalArgumentException("Effect audio cue ticks cannot be negative");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(soundId, "soundId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(attenuation, "attenuation");
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || !Float.isFinite(volume) || volume < 0.0F
            || !Float.isFinite(pitch) || pitch <= 0.0F) {
            throw new IllegalArgumentException("Invalid effect audio cue");
        }
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
