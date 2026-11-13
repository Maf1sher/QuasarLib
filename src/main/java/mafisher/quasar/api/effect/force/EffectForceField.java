package mafisher.quasar.api.effect.force;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.effect.EffectPoint;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative force volume for one gameplay tick. Instances are immutable pure math: the
 * engine resolves candidate entities, calls {@link #velocityDeltaAt(Vec3)} for each target, and adds
 * the returned world-space velocity once. External rigid-body bridges can reuse the same value and
 * math without a second force model.
 *
 * <p>Strengths are velocity deltas in blocks per tick. A positive {@link Radial} strength pulls
 * targets toward the center; a negative value pushes them away. Falloff uses
 * {@code (1 - distance / radius) ^ falloff} inside the volume and is zero outside. A falloff of zero
 * applies a constant strength throughout the volume.</p>
 */
public sealed interface EffectForceField permits EffectForceField.Radial, EffectForceField.Shockwave,
    EffectForceField.Vortex, EffectForceField.Pressure, EffectForceField.Accretion {
    /** Hard safety ceiling for one force volume; larger scenarios use multiple fields. */
    double MAX_RADIUS = 1_024.0;
    /** Hard safety ceiling for one velocity delta per application. */
    double MAX_STRENGTH = 64.0;
    /** Hard safety ceiling for the falloff exponent. */
    double MAX_FALLOFF = 16.0;

    /** Broad-phase volume that contains every point where this field can affect a target. */
    EffectBounds bounds();

    /** Center used to order candidates when a target cap is reached. */
    EffectPoint center();

    /** Maximum entities this field may examine in one application. */
    int maxTargets();

    /**
     * Returns the velocity delta to add to a target at {@code point}, or {@link Vec3#ZERO} when the
     * point is outside the field. The input must be finite.
     */
    Vec3 velocityDeltaAt(Vec3 point);

    /** Creates an inward radial field. Non-negative strength is enforced. */
    static EffectForceField pull(
        EffectPoint center,
        double radius,
        double strength,
        double falloff,
        int maxTargets
    ) {
        return new Radial(center, radius, Math.abs(strength), falloff, maxTargets);
    }

    /** Creates an outward radial field. Non-negative strength is enforced. */
    static EffectForceField push(
        EffectPoint center,
        double radius,
        double strength,
        double falloff,
        int maxTargets
    ) {
        return new Radial(center, radius, -Math.abs(strength), falloff, maxTargets);
    }

    /** Creates a shockwave with an upward lift component. */
    static EffectForceField shockwave(
        EffectPoint center,
        double radius,
        double strength,
        double lift,
        double falloff,
        int maxTargets
    ) {
        return new Shockwave(center, radius, strength, lift, falloff, maxTargets);
    }

    /** Creates a horizontal vortex around the Y axis. */
    static EffectForceField vortex(
        EffectPoint center,
        double radius,
        double tangentialStrength,
        double inwardStrength,
        double lift,
        double falloff,
        int maxTargets
    ) {
        return new Vortex(center, radius, tangentialStrength, inwardStrength, lift, falloff, maxTargets);
    }

    /**
     * Creates a three-dimensional accretion field: inward pull plus tangential swirl around an
     * arbitrary axis, with optional lift along that axis. Use the black-hole disk normal as the
     * axis so the swirl follows a tilted disk.
     */
    static EffectForceField accretion(
        EffectPoint center,
        Vec3 axis,
        double radius,
        double inwardStrength,
        double tangentialStrength,
        double lift,
        double falloff,
        int maxTargets
    ) {
        return new Accretion(
            center, axis, radius, inwardStrength, tangentialStrength, lift, falloff, maxTargets
        );
    }

    /** Creates directional pressure inside an axis-aligned volume. */
    static EffectForceField pressure(
        EffectBounds volume,
        Vec3 direction,
        double strength,
        double falloff,
        int maxTargets
    ) {
        return new Pressure(volume, direction, strength, falloff, maxTargets);
    }

    /** A spherical radial pull (positive strength) or push (negative strength). */
    record Radial(
        EffectPoint center,
        double radius,
        double strength,
        double falloff,
        int maxTargets
    ) implements EffectForceField {
        public Radial {
            requireCenter(center);
            requireRadius(radius);
            requireStrength(strength);
            requireFalloff(falloff);
            requireTargets(maxTargets);
        }

        @Override
        public EffectBounds bounds() {
            return sphereBounds(this.center, this.radius);
        }

        @Override
        public Vec3 velocityDeltaAt(Vec3 point) {
            requirePoint(point);
            double dx = this.center.x() - point.x;
            double dy = this.center.y() - point.y;
            double dz = this.center.z() - point.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance <= 0.0 || distance >= this.radius) {
                return Vec3.ZERO;
            }
            double scale = this.strength * falloffFactor(distance, this.radius, this.falloff) / distance;
            return new Vec3(dx * scale, dy * scale, dz * scale);
        }
    }

    /** An outward impulse with an optional upward component, strongest at the center. */
    record Shockwave(
        EffectPoint center,
        double radius,
        double strength,
        double lift,
        double falloff,
        int maxTargets
    ) implements EffectForceField {
        public Shockwave {
            requireCenter(center);
            requireRadius(radius);
            requireStrength(strength);
            requireLift(lift);
            requireFalloff(falloff);
            requireTargets(maxTargets);
        }

        @Override
        public EffectBounds bounds() {
            return sphereBounds(this.center, this.radius);
        }

        @Override
        public Vec3 velocityDeltaAt(Vec3 point) {
            requirePoint(point);
            double dx = point.x - this.center.x();
            double dz = point.z - this.center.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double distance = Math.sqrt(horizontal * horizontal
                + (point.y - this.center.y()) * (point.y - this.center.y()));
            if (distance >= this.radius) {
                return Vec3.ZERO;
            }
            double factor = this.strength * falloffFactor(distance, this.radius, this.falloff);
            if (horizontal <= 1.0E-9) {
                return new Vec3(0.0, factor * this.lift, 0.0);
            }
            double scale = factor / horizontal;
            return new Vec3(dx * scale, factor * this.lift, dz * scale);
        }
    }

    /** A horizontal vortex around the Y axis with optional inward pull and upward lift. */
    record Vortex(
        EffectPoint center,
        double radius,
        double tangentialStrength,
        double inwardStrength,
        double lift,
        double falloff,
        int maxTargets
    ) implements EffectForceField {
        public Vortex {
            requireCenter(center);
            requireRadius(radius);
            requireStrength(tangentialStrength);
            requireStrength(inwardStrength);
            requireLift(lift);
            requireFalloff(falloff);
            requireTargets(maxTargets);
        }

        @Override
        public EffectBounds bounds() {
            return sphereBounds(this.center, this.radius);
        }

        @Override
        public Vec3 velocityDeltaAt(Vec3 point) {
            requirePoint(point);
            double dx = point.x - this.center.x();
            double dz = point.z - this.center.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double distance = Math.sqrt(horizontal * horizontal
                + (point.y - this.center.y()) * (point.y - this.center.y()));
            if (distance >= this.radius || horizontal <= 1.0E-9) {
                return new Vec3(0.0, distance < this.radius ? this.lift * falloffFactor(
                    distance, this.radius, this.falloff) : 0.0, 0.0);
            }
            double factor = falloffFactor(distance, this.radius, this.falloff);
            double tangentX = -dz / horizontal;
            double tangentZ = dx / horizontal;
            double inwardX = -dx / horizontal;
            double inwardZ = -dz / horizontal;
            return new Vec3(
                (tangentX * this.tangentialStrength + inwardX * this.inwardStrength) * factor,
                this.lift * factor,
                (tangentZ * this.tangentialStrength + inwardZ * this.inwardStrength) * factor
            );
        }
    }

    /**
     * A spherical accretion field with an arbitrary swirl axis. Every target is pulled toward the
     * center, rotated around {@code axis}, and optionally lifted along it. The field is pure math
     * so optional physics bridges can reuse it unchanged.
     */
    record Accretion(
        EffectPoint center,
        Vec3 axis,
        double radius,
        double inwardStrength,
        double tangentialStrength,
        double lift,
        double falloff,
        int maxTargets
    ) implements EffectForceField {
        public Accretion {
            requireCenter(center);
            requirePoint(axis);
            if (axis.lengthSqr() <= 0.0) {
                throw new IllegalArgumentException("Accretion axis must not be zero");
            }
            axis = axis.normalize();
            requireRadius(radius);
            requireStrength(inwardStrength);
            requireStrength(tangentialStrength);
            requireLift(lift);
            requireFalloff(falloff);
            requireTargets(maxTargets);
        }

        @Override
        public EffectBounds bounds() {
            return sphereBounds(this.center, this.radius);
        }

        @Override
        public Vec3 velocityDeltaAt(Vec3 point) {
            requirePoint(point);
            double dx = this.center.x() - point.x;
            double dy = this.center.y() - point.y;
            double dz = this.center.z() - point.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance <= 0.0 || distance >= this.radius) {
                return Vec3.ZERO;
            }
            double factor = falloffFactor(distance, this.radius, this.falloff);
            double inverse = 1.0 / distance;
            Vec3 radial = new Vec3(dx * inverse, dy * inverse, dz * inverse);
            Vec3 tangent = this.axis.cross(radial);
            if (tangent.lengthSqr() > 1.0E-12) {
                tangent = tangent.normalize();
            } else {
                tangent = Vec3.ZERO;
            }
            Vec3 inward = radial.scale(this.inwardStrength);
            Vec3 swirl = tangent.scale(this.tangentialStrength);
            Vec3 lift = this.axis.scale(this.lift);
            return inward.add(swirl).add(lift).scale(factor);
        }
    }

    /** Constant-direction pressure inside an axis-aligned volume, fading toward its boundary. */
    record Pressure(
        EffectBounds volume,
        Vec3 direction,
        double strength,
        double falloff,
        int maxTargets
    ) implements EffectForceField {
        public Pressure {
            Objects.requireNonNull(volume, "volume");
            requirePoint(direction);
            if (direction.lengthSqr() <= 0.0) {
                throw new IllegalArgumentException("Force direction must not be zero");
            }
            requireStrength(strength);
            requireFalloff(falloff);
            requireTargets(maxTargets);
            direction = direction.normalize();
        }

        @Override
        public EffectBounds bounds() {
            return this.volume;
        }

        @Override
        public EffectPoint center() {
            return new EffectPoint(
                (this.volume.minX() + this.volume.maxX()) * 0.5,
                (this.volume.minY() + this.volume.maxY()) * 0.5,
                (this.volume.minZ() + this.volume.maxZ()) * 0.5
            );
        }

        @Override
        public Vec3 velocityDeltaAt(Vec3 point) {
            requirePoint(point);
            if (!this.volume.contains(new EffectPoint(point.x, point.y, point.z))) {
                return Vec3.ZERO;
            }
            EffectPoint center = this.center();
            double dx = (this.volume.maxX() - this.volume.minX()) * 0.5;
            double dy = (this.volume.maxY() - this.volume.minY()) * 0.5;
            double dz = (this.volume.maxZ() - this.volume.minZ()) * 0.5;
            double maxDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (maxDistance <= 0.0) {
                return this.direction.scale(this.strength);
            }
            double distance = Math.sqrt(center.distanceToSqr(point.x, point.y, point.z));
            double factor = this.strength * falloffFactor(distance, maxDistance, this.falloff);
            return this.direction.scale(factor);
        }
    }

    private static EffectBounds sphereBounds(EffectPoint center, double radius) {
        return new EffectBounds(
            center.x() - radius, center.y() - radius, center.z() - radius,
            center.x() + radius, center.y() + radius, center.z() + radius
        );
    }

    private static double falloffFactor(double distance, double radius, double exponent) {
        if (radius <= 0.0) {
            return 0.0;
        }
        double normalized = 1.0 - Math.max(0.0, Math.min(1.0, distance / radius));
        return exponent == 0.0 ? 1.0 : Math.pow(normalized, exponent);
    }

    private static void requireCenter(EffectPoint center) {
        Objects.requireNonNull(center, "center");
    }

    private static void requireRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException(
                "Force radius must be finite, positive, and at most " + MAX_RADIUS
            );
        }
    }

    private static void requireStrength(double strength) {
        if (!Double.isFinite(strength) || Math.abs(strength) > MAX_STRENGTH) {
            throw new IllegalArgumentException(
                "Force strength must be finite and at most " + MAX_STRENGTH
            );
        }
    }

    private static void requireLift(double lift) {
        if (!Double.isFinite(lift) || lift < 0.0 || lift > MAX_STRENGTH) {
            throw new IllegalArgumentException(
                "Force lift must be finite, non-negative, and at most " + MAX_STRENGTH
            );
        }
    }

    private static void requireFalloff(double falloff) {
        if (!Double.isFinite(falloff) || falloff < 0.0 || falloff > MAX_FALLOFF) {
            throw new IllegalArgumentException(
                "Force falloff must be finite between 0 and " + MAX_FALLOFF
            );
        }
    }

    private static void requireTargets(int maxTargets) {
        if (maxTargets < 1) {
            throw new IllegalArgumentException("A force field needs at least one target slot");
        }
    }

    private static void requirePoint(Vec3 point) {
        Objects.requireNonNull(point, "point");
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            throw new IllegalArgumentException("Force field point must be finite");
        }
    }
}
