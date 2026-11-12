package mafisher.quasar.effect;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * Shared validation for one positional sound declaration. Engine-internal; used by audio cues,
 * triggers, scopes, and cinematic sound cues so the accepted range cannot drift between them.
 */
@ApiStatus.Internal
public final class EffectSoundValidation {
    private EffectSoundValidation() {
    }

    /** Requires a finite position, non-negative finite volume, and positive finite pitch. */
    public static void requirePlayable(Vec3 position, float volume, float pitch, String errorMessage) {
        Objects.requireNonNull(position, "position");
        boolean validPosition = Double.isFinite(position.x)
            && Double.isFinite(position.y)
            && Double.isFinite(position.z);
        boolean validVolume = Float.isFinite(volume) && volume >= 0.0F;
        boolean validPitch = Float.isFinite(pitch) && pitch > 0.0F;
        if (!validPosition || !validVolume || !validPitch) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
