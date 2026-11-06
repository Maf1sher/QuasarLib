package mafisher.quasar.effect.cinematic;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** One client-scheduled sound event, with a tick relative to the cinematic start. */
public record CinematicAudioCue(
    int tick,
    Vec3 position,
    ResourceLocation soundId,
    SoundSource source,
    float volume,
    float pitch
) {
    public CinematicAudioCue {
        if (tick < 0) {
            throw new IllegalArgumentException("Cinematic audio cue ticks cannot be negative");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(soundId, "soundId");
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || !Float.isFinite(volume) || volume < 0.0F
            || !Float.isFinite(pitch) || pitch <= 0.0F) {
            throw new IllegalArgumentException("Invalid cinematic audio cue");
        }
    }
}
