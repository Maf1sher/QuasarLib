package mafisher.quasar.api.client.particle;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * Local-space spawn shape for one {@link EffectParticleField}.
 *
 * <p>Coordinates are relative to the effect transform. The engine samples the shape
 * deterministically from the field seed and the particle instance index; no sample is
 * stored on the CPU.</p>
 */
public sealed interface EffectParticleShape
    permits EffectParticleShape.Point, EffectParticleShape.Sphere, EffectParticleShape.Box,
    EffectParticleShape.Disk, EffectParticleShape.Line {
    /** Maximum absolute coordinate accepted by a shape. */
    double MAX_COORDINATE = 1_024.0;

    /**
     * Conservative local distance from the effect origin to the farthest point of the shape.
     * Culling helpers add motion and size on top of this value.
     */
    double extent();

    /** Every particle starts at the effect origin. */
    record Point() implements EffectParticleShape {
        @Override
        public double extent() {
            return 0.0;
        }
    }

    /** Particles start in a sphere, on its surface when {@code surface} is true. */
    record Sphere(Vec3 center, double minRadius, double maxRadius, boolean surface)
        implements EffectParticleShape {
        public Sphere {
            requireFinite(center, "Particle sphere center");
            requireRadius(minRadius, maxRadius, "Particle sphere");
        }

        @Override
        public double extent() {
            return this.center.length() + this.maxRadius;
        }
    }

    /** Particles start uniformly inside an axis-aligned local box. */
    record Box(Vec3 min, Vec3 max) implements EffectParticleShape {
        public Box {
            requireFinite(min, "Particle box minimum");
            requireFinite(max, "Particle box maximum");
            if (min.x > max.x || min.y > max.y || min.z > max.z) {
                throw new IllegalArgumentException("Particle box minimum must not exceed its maximum");
            }
            requireCoordinate(max, "Particle box maximum");
        }

        @Override
        public double extent() {
            return Math.sqrt(
                square(Math.max(Math.abs(this.min.x), Math.abs(this.max.x)))
                    + square(Math.max(Math.abs(this.min.y), Math.abs(this.max.y)))
                    + square(Math.max(Math.abs(this.min.z), Math.abs(this.max.z)))
            );
        }
    }

    /** Particles start on a local annulus around {@code center}, in the plane normal to {@code normal}. */
    record Disk(Vec3 center, Vec3 normal, double minRadius, double maxRadius)
        implements EffectParticleShape {
        public Disk {
            requireFinite(center, "Particle disk center");
            requireFinite(normal, "Particle disk normal");
            if (normal.lengthSqr() < 1.0E-12) {
                throw new IllegalArgumentException("Particle disk normal must not be zero");
            }
            requireRadius(minRadius, maxRadius, "Particle disk");
        }

        @Override
        public double extent() {
            return this.center.length() + this.maxRadius;
        }
    }

    /** Particles start along a local line segment with a random radial offset. */
    record Line(Vec3 start, Vec3 end, double radius) implements EffectParticleShape {
        public Line {
            requireFinite(start, "Particle line start");
            requireFinite(end, "Particle line end");
            if (!Double.isFinite(radius) || radius < 0.0 || radius > MAX_COORDINATE) {
                throw new IllegalArgumentException("Particle line radius must be between 0 and "
                    + MAX_COORDINATE);
            }
        }

        @Override
        public double extent() {
            return Math.max(this.start.length(), this.end.length()) + this.radius;
        }
    }

    private static void requireFinite(Vec3 value, String name) {
        Objects.requireNonNull(value, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        requireCoordinate(value, name);
    }

    private static void requireCoordinate(Vec3 value, String name) {
        if (Math.abs(value.x) > MAX_COORDINATE || Math.abs(value.y) > MAX_COORDINATE
            || Math.abs(value.z) > MAX_COORDINATE) {
            throw new IllegalArgumentException(name + " must stay within " + MAX_COORDINATE);
        }
    }

    private static void requireRadius(double minRadius, double maxRadius, String name) {
        if (!Double.isFinite(minRadius) || minRadius < 0.0
            || !Double.isFinite(maxRadius) || maxRadius < minRadius
            || maxRadius > MAX_COORDINATE) {
            throw new IllegalArgumentException(name + " radii must be finite, ordered, and within "
                + MAX_COORDINATE);
        }
    }

    private static double square(double value) {
        return value * value;
    }
}
