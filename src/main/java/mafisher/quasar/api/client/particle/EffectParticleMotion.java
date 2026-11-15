package mafisher.quasar.api.client.particle;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic local motion for one {@link EffectParticleField}.
 *
 * <p>The engine evaluates the closed-form path in the vertex shader from the
 * particle age. Gravity and drag use the standard ballistic formula, so the field
 * never needs per-particle integration.</p>
 *
 * @param mode velocity direction source
 * @param direction local direction for {@link VelocityMode#DIRECTIONAL}; ignored otherwise
 * @param speed initial speed range in blocks per tick
 * @param spread {@code 0} follows {@code direction}, {@code 1} is fully random; ignored by
 *     {@link VelocityMode#OUTWARD} and {@link VelocityMode#INWARD}
 * @param gravity downward acceleration in blocks per tick squared; negative floats upward
 * @param drag exponential velocity decay per tick, {@code 0} disables drag
 * @param turbulence sinusoidal displacement amplitude in blocks
 */
public record EffectParticleMotion(
    VelocityMode mode,
    Vec3 direction,
    SpeedRange speed,
    double spread,
    double gravity,
    double drag,
    double turbulence
) {
    /** Maximum initial speed in blocks per tick. */
    public static final double MAX_SPEED = 64.0;
    /** Maximum absolute gravity in blocks per tick squared. */
    public static final double MAX_GRAVITY = 1.0;
    /** Maximum turbulence amplitude in blocks. */
    public static final double MAX_TURBULENCE = 8.0;

    public EffectParticleMotion {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(speed, "speed");
        if (!isFinite(direction)) {
            throw new IllegalArgumentException("Particle direction must be finite");
        }
        if (direction.lengthSqr() > 0.0) {
            direction = direction.normalize();
        }
        if (!Double.isFinite(spread) || spread < 0.0 || spread > 1.0) {
            throw new IllegalArgumentException("Particle spread must be between 0 and 1");
        }
        if (!Double.isFinite(gravity) || gravity < -MAX_GRAVITY || gravity > MAX_GRAVITY) {
            throw new IllegalArgumentException("Particle gravity must be between -"
                + MAX_GRAVITY + " and " + MAX_GRAVITY);
        }
        if (!Double.isFinite(drag) || drag < 0.0 || drag > 1.0) {
            throw new IllegalArgumentException("Particle drag must be between 0 and 1");
        }
        if (!Double.isFinite(turbulence) || turbulence < 0.0 || turbulence > MAX_TURBULENCE) {
            throw new IllegalArgumentException("Particle turbulence must be between 0 and "
                + MAX_TURBULENCE);
        }
    }

    /** A stationary field; particles stay at their spawn point. */
    public static EffectParticleMotion stationary() {
        return new EffectParticleMotion(VelocityMode.DIRECTIONAL, Vec3.ZERO,
            new SpeedRange(0.0, 0.0), 0.0, 0.0, 0.0, 0.0);
    }

    /** A field that moves away from the shape centre with a speed range. */
    public static EffectParticleMotion outward(double speedMin, double speedMax, double spread) {
        return new EffectParticleMotion(VelocityMode.OUTWARD, Vec3.ZERO,
            new SpeedRange(speedMin, speedMax), spread, 0.0, 0.0, 0.0);
    }

    /** Maximum local displacement over the supplied lifetime, used for culling bounds. */
    public double maxDisplacement(long lifetimeTicks) {
        double ticks = Math.max(0L, lifetimeTicks);
        return this.speed.max() * ticks
            + 0.5 * Math.abs(this.gravity) * ticks * ticks
            + this.turbulence * ticks;
    }

    /** Initial speed range in blocks per tick. */
    public record SpeedRange(double min, double max) {
        public SpeedRange {
            if (!Double.isFinite(min) || min < 0.0 || !Double.isFinite(max) || max < min
                || max > MAX_SPEED) {
                throw new IllegalArgumentException("Particle speed must be finite, ordered, and at most "
                    + MAX_SPEED);
            }
        }
    }

    /** Source of the initial particle velocity. */
    public enum VelocityMode {
        /** Bias along {@link EffectParticleMotion#direction()}, then randomize by {@code spread}. */
        DIRECTIONAL,
        /** Move away from the local shape origin. */
        OUTWARD,
        /** Move toward the local shape origin. */
        INWARD
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
