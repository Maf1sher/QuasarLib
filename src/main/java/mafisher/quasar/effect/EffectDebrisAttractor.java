package mafisher.quasar.effect;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * Optional curved motion for client-only debris. Pieces spiral inward around {@code axis} and
 * disappear when they reach {@code horizonRadius}. The field is pure math and deterministic from
 * the burst seed and piece origin.
 *
 * @param center attractor center in world space
 * @param axis normalized swirl axis; the black hole uses the disk normal
 * @param horizonRadius radius at which a piece disappears
 * @param radialPull radial easing exponent; 1 is linear, larger values fall later
 * @param swirl full turns around the axis over one piece lifetime
 */
public record EffectDebrisAttractor(
    Vec3 center,
    Vec3 axis,
    double horizonRadius,
    double radialPull,
    double swirl
) {
    public EffectDebrisAttractor {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(axis, "axis");
        requireFinite(center, "center");
        requireFinite(axis, "axis");
        if (axis.lengthSqr() <= 1.0E-12) {
            throw new IllegalArgumentException("Debris attractor axis must not be zero");
        }
        if (!Double.isFinite(horizonRadius) || horizonRadius < 0.0) {
            throw new IllegalArgumentException("Debris attractor horizon must be finite and non-negative");
        }
        if (!Double.isFinite(radialPull) || radialPull < 0.25 || radialPull > 8.0) {
            throw new IllegalArgumentException("Debris radial pull must be between 0.25 and 8");
        }
        if (!Double.isFinite(swirl) || swirl < -8.0 || swirl > 8.0) {
            throw new IllegalArgumentException("Debris swirl must be between -8 and 8 turns");
        }
        axis = axis.normalize();
    }

    private static void requireFinite(Vec3 value, String name) {
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
