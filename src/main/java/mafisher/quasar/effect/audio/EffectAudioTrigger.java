package mafisher.quasar.effect.audio;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A named, server-triggered one-shot sound declared on an audio spec. Triggers are not scheduled
 * by tick; gameplay controllers or consumers fire them by ID and may override the position.
 * The definition travels with the audio plan so late joiners can receive the same triggers.
 */
public record EffectAudioTrigger(
    String id,
    Vec3 position,
    ResourceLocation soundId,
    SoundSource source,
    float volume,
    float pitch,
    EffectAudioAttenuation attenuation
) {
    public static final int MAX_ID_LENGTH = 64;

    public EffectAudioTrigger {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || id.length() > MAX_ID_LENGTH || id.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                "Effect audio trigger IDs must be 1-" + MAX_ID_LENGTH + " non-whitespace characters"
            );
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(soundId, "soundId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(attenuation, "attenuation");
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || !Float.isFinite(volume) || volume < 0.0F
            || !Float.isFinite(pitch) || pitch <= 0.0F) {
            throw new IllegalArgumentException("Invalid effect audio trigger");
        }
    }

    public EffectAudioTrigger(
        String id,
        Vec3 position,
        SoundEvent sound,
        SoundSource source,
        float volume,
        float pitch,
        EffectAudioAttenuation attenuation
    ) {
        this(id, position, Objects.requireNonNull(sound, "sound").getLocation(), source, volume, pitch, attenuation);
    }

    public EffectAudioTrigger(
        String id,
        Vec3 position,
        ResourceLocation soundId,
        SoundSource source,
        float volume,
        float pitch
    ) {
        this(id, position, soundId, source, volume, pitch, EffectAudioAttenuation.VANILLA);
    }

    /** Returns a copy that plays at an explicitly supplied event position. */
    public EffectAudioTrigger withPosition(Vec3 position) {
        return new EffectAudioTrigger(
            this.id, position, this.soundId, this.source, this.volume, this.pitch, this.attenuation
        );
    }
}
