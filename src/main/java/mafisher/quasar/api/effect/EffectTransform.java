package mafisher.quasar.api.effect;

import java.util.Objects;

/** World position and Euler orientation for one effect instance. Angles are in degrees. */
public record EffectTransform(EffectPoint position, float yaw, float pitch, float roll) {
    public EffectTransform {
        Objects.requireNonNull(position, "position");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("Effect transforms must contain only finite values");
        }
    }

    /** Interpolates position and the shortest angular path between two transforms. */
    public static EffectTransform interpolate(EffectTransform from, EffectTransform to, float partialTick) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!Float.isFinite(partialTick)) {
            throw new IllegalArgumentException("Interpolation progress must be finite");
        }
        float progress = Math.max(0.0F, Math.min(1.0F, partialTick));
        return new EffectTransform(
            new EffectPoint(
                from.position.x() + (to.position.x() - from.position.x()) * progress,
                from.position.y() + (to.position.y() - from.position.y()) * progress,
                from.position.z() + (to.position.z() - from.position.z()) * progress
            ),
            interpolateAngle(from.yaw, to.yaw, progress),
            interpolateAngle(from.pitch, to.pitch, progress),
            interpolateAngle(from.roll, to.roll, progress)
        );
    }

    private static float interpolateAngle(float from, float to, float progress) {
        if (progress <= 0.0F) {
            return from;
        }
        if (progress >= 1.0F) {
            return to;
        }
        float difference = (to - from) % 360.0F;
        if (difference >= 180.0F) {
            difference -= 360.0F;
        } else if (difference < -180.0F) {
            difference += 360.0F;
        }
        return from + difference * progress;
    }
}
