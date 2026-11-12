package mafisher.quasar.api.audio;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * How a client attenuates one scheduled sound cue. Distance attenuation is linear from the cue
 * position to {@code maxDistance}. Directional attenuation is a cone at the source: listeners
 * inside {@code innerAngleDegrees} hear full volume, listeners outside {@code outerAngleDegrees}
 * hear {@code outsideGain}, and the gain is interpolated in between.
 */
public sealed interface EffectAudioAttenuation
    permits EffectAudioAttenuation.Vanilla,
    EffectAudioAttenuation.Distance,
    EffectAudioAttenuation.Directional {

    /** Minecraft's normal sound attenuation; no custom distance or direction gain. */
    Vanilla VANILLA = new Vanilla();

    /** Use Minecraft's built-in attenuation for this cue. */
    record Vanilla() implements EffectAudioAttenuation {
    }

    /** Linear distance attenuation from full volume at the source to silence at {@code maxDistance}. */
    record Distance(float maxDistance) implements EffectAudioAttenuation {
        public Distance {
            if (!Float.isFinite(maxDistance) || maxDistance <= 0.0F) {
                throw new IllegalArgumentException("Effect audio distance must be finite and positive");
            }
        }
    }

    /**
     * A directional cone at the source with optional distance attenuation.
     *
     * @param direction world-space direction the sound is emitted toward
     * @param innerAngleDegrees full-volume cone half-angle
     * @param outerAngleDegrees outer cone half-angle; beyond it the gain is {@code outsideGain}
     * @param outsideGain gain outside the outer cone, between 0 and 1
     * @param maxDistance distance at which sound becomes silent; zero disables distance attenuation
     */
    record Directional(
        Vec3 direction,
        float innerAngleDegrees,
        float outerAngleDegrees,
        float outsideGain,
        float maxDistance
    ) implements EffectAudioAttenuation {
        public Directional {
            Objects.requireNonNull(direction, "direction");
            if (!Double.isFinite(direction.x) || !Double.isFinite(direction.y) || !Double.isFinite(direction.z)
                || direction.lengthSqr() <= 1.0E-12) {
                throw new IllegalArgumentException("Effect audio direction must be finite and non-zero");
            }
            direction = direction.normalize();
            if (!Float.isFinite(innerAngleDegrees) || !Float.isFinite(outerAngleDegrees)
                || innerAngleDegrees < 0.0F || outerAngleDegrees > 180.0F
                || innerAngleDegrees > outerAngleDegrees) {
                throw new IllegalArgumentException("Effect audio cone angles must be ordered between 0 and 180");
            }
            if (!Float.isFinite(outsideGain) || outsideGain < 0.0F || outsideGain > 1.0F) {
                throw new IllegalArgumentException("Effect audio outside gain must be between 0 and 1");
            }
            if (!Float.isFinite(maxDistance) || maxDistance < 0.0F) {
                throw new IllegalArgumentException("Effect audio max distance must be finite and non-negative");
            }
        }
    }

    static Distance distance(float maxDistance) {
        return new Distance(maxDistance);
    }

    static Directional directional(
        Vec3 direction,
        float innerAngleDegrees,
        float outerAngleDegrees,
        float outsideGain,
        float maxDistance
    ) {
        return new Directional(direction, innerAngleDegrees, outerAngleDegrees, outsideGain, maxDistance);
    }
}
