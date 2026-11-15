package mafisher.quasar.client.effect.audio;

import org.jetbrains.annotations.ApiStatus;

/** Pure gain math shared by the client sound instance, the inspector, and headless tests. */
@ApiStatus.Internal
public final class EffectAudioGain {
    private EffectAudioGain() {
    }

    public static float distanceGain(double distance, float maxDistance) {
        if (maxDistance <= 0.0F || distance <= 0.0) {
            return 1.0F;
        }
        if (distance >= maxDistance) {
            return 0.0F;
        }
        return (float)(1.0 - distance / maxDistance);
    }

    public static float directionGain(double angleDegrees, float innerAngle, float outerAngle,
        float outsideGain) {
        if (angleDegrees <= innerAngle) {
            return 1.0F;
        }
        if (outerAngle <= innerAngle || angleDegrees >= outerAngle) {
            return outsideGain;
        }
        double progress = (angleDegrees - innerAngle) / (outerAngle - innerAngle);
        return (float)(1.0 + (outsideGain - 1.0) * progress);
    }

    public static float combined(float distanceGain, float directionGain) {
        return Math.max(0.0F, Math.min(1.0F, distanceGain * directionGain));
    }
}
