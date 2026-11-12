package mafisher.quasar.effect;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectDebrisAttractor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** Pure deterministic math for curved client-debris motion. */
@ApiStatus.Internal
public final class EffectDebrisMath {
    private EffectDebrisMath() {
    }

    /**
     * Returns the position of a piece at {@code progress} in {@code [0, 1]}. The piece starts at
     * {@code start}, follows the configured radial pull, and reaches the horizon at the end.
     * With a zero swirl the path is a straight line to the attractor centre; positive or negative
     * swirl adds turns around the axis.
     */
    public static Vec3 infallPosition(Vec3 start, EffectDebrisAttractor attractor, double progress) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(attractor, "attractor");
        double t = Math.max(0.0, Math.min(1.0, progress));
        Vec3 offset = start.subtract(attractor.center());
        double startRadius = offset.length();
        if (startRadius <= 1.0E-9) {
            return attractor.center();
        }
        double eased = Math.pow(t, attractor.radialPull());
        double radius = startRadius + (attractor.horizonRadius() - startRadius) * eased;
        if (attractor.swirl() == 0.0) {
            return attractor.center().add(offset.scale(radius / startRadius));
        }
        double angle = Math.PI * 2.0 * attractor.swirl() * t;
        Vec3 rotated = rotate(offset, attractor.axis(), angle);
        return attractor.center().add(rotated.scale(radius / startRadius));
    }

    /** Rodrigues rotation of {@code vector} around normalized {@code axis}. */
    public static Vec3 rotate(Vec3 vector, Vec3 axis, double radians) {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(axis, "axis");
        if (axis.lengthSqr() <= 1.0E-12) {
            return vector;
        }
        Vec3 unit = axis.normalize();
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        Vec3 parallel = unit.scale(unit.dot(vector));
        Vec3 perpendicular = vector.subtract(parallel);
        return parallel
            .add(perpendicular.scale(cosine))
            .add(unit.cross(perpendicular).scale(sine));
    }
}
