package mafisher.quasar.effect;

import java.util.Objects;

/** Minecraft-independent broad-phase bounds and intersection math for a damaging effect. */
public sealed interface EffectDamageShape permits EffectDamageShape.Box, EffectDamageShape.Sphere,
    EffectDamageShape.Cylinder, EffectDamageShape.Beam {
    EffectBounds bounds();

    boolean intersects(EffectBounds targetBox);

    record Box(EffectBounds bounds) implements EffectDamageShape {
        public Box {
            Objects.requireNonNull(bounds, "bounds");
        }

        @Override
        public boolean intersects(EffectBounds targetBox) {
            return this.bounds.intersects(targetBox);
        }
    }

    record Sphere(EffectPoint center, double radius) implements EffectDamageShape {
        public Sphere {
            Objects.requireNonNull(center, "center");
            requirePositive(radius, "radius");
        }

        @Override
        public EffectBounds bounds() {
            return new EffectBounds(
                this.center.x() - this.radius, this.center.y() - this.radius, this.center.z() - this.radius,
                this.center.x() + this.radius, this.center.y() + this.radius, this.center.z() + this.radius
            );
        }

        @Override
        public boolean intersects(EffectBounds targetBox) {
            EffectPoint nearest = targetBox.closestPoint(this.center);
            return this.center.distanceToSqr(nearest.x(), nearest.y(), nearest.z()) <= this.radius * this.radius;
        }
    }

    /** A vertical cylinder extending upward from its base position. */
    record Cylinder(EffectPoint base, double radius, double height) implements EffectDamageShape {
        public Cylinder {
            Objects.requireNonNull(base, "base");
            requirePositive(radius, "radius");
            requirePositive(height, "height");
        }

        @Override
        public EffectBounds bounds() {
            return new EffectBounds(
                this.base.x() - this.radius, this.base.y(), this.base.z() - this.radius,
                this.base.x() + this.radius, this.base.y() + this.height, this.base.z() + this.radius
            );
        }

        @Override
        public boolean intersects(EffectBounds targetBox) {
            if (targetBox.maxY() < this.base.y() || targetBox.minY() > this.base.y() + this.height) {
                return false;
            }
            EffectPoint nearest = targetBox.closestPoint(this.base);
            double dx = this.base.x() - nearest.x();
            double dz = this.base.z() - nearest.z();
            return dx * dx + dz * dz <= this.radius * this.radius;
        }
    }

    /** A finite beam tested by closest distance from its segment to the target box. */
    record Beam(EffectPoint start, EffectPoint end, double radius) implements EffectDamageShape {
        public Beam {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            requirePositive(radius, "radius");
        }

        @Override
        public EffectBounds bounds() {
            return new EffectBounds(
                Math.min(this.start.x(), this.end.x()),
                Math.min(this.start.y(), this.end.y()),
                Math.min(this.start.z(), this.end.z()),
                Math.max(this.start.x(), this.end.x()),
                Math.max(this.start.y(), this.end.y()),
                Math.max(this.start.z(), this.end.z())
            ).inflate(this.radius);
        }

        @Override
        public boolean intersects(EffectBounds targetBox) {
            if (segmentIntersects(this.start, this.end, targetBox)) {
                return true;
            }
            double low = 0.0;
            double high = 1.0;
            for (int iteration = 0; iteration < 32; iteration++) {
                double firstThird = (2.0 * low + high) / 3.0;
                double secondThird = (low + 2.0 * high) / 3.0;
                if (segmentPointDistanceSquared(this.start, this.end, firstThird, targetBox)
                    <= segmentPointDistanceSquared(this.start, this.end, secondThird, targetBox)) {
                    high = secondThird;
                } else {
                    low = firstThird;
                }
            }
            double closestDistanceSquared = Math.min(
                segmentPointDistanceSquared(this.start, this.end, low, targetBox),
                segmentPointDistanceSquared(this.start, this.end, high, targetBox)
            );
            return closestDistanceSquared <= this.radius * this.radius;
        }
    }

    private static double segmentPointDistanceSquared(
        EffectPoint start,
        EffectPoint end,
        double progress,
        EffectBounds bounds
    ) {
        double x = start.x() + (end.x() - start.x()) * progress;
        double y = start.y() + (end.y() - start.y()) * progress;
        double z = start.z() + (end.z() - start.z()) * progress;
        double dx = x - clamp(x, bounds.minX(), bounds.maxX());
        double dy = y - clamp(y, bounds.minY(), bounds.maxY());
        double dz = z - clamp(z, bounds.minZ(), bounds.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean segmentIntersects(EffectPoint start, EffectPoint end, EffectBounds bounds) {
        double minT = 0.0;
        double maxT = 1.0;

        double deltaX = end.x() - start.x();
        if (Math.abs(deltaX) < 1.0E-12) {
            if (start.x() < bounds.minX() || start.x() > bounds.maxX()) {
                return false;
            }
        } else {
            double firstT = (bounds.minX() - start.x()) / deltaX;
            double secondT = (bounds.maxX() - start.x()) / deltaX;
            if (firstT > secondT) {
                double swap = firstT;
                firstT = secondT;
                secondT = swap;
            }
            minT = Math.max(minT, firstT);
            maxT = Math.min(maxT, secondT);
            if (minT > maxT) {
                return false;
            }
        }

        double deltaY = end.y() - start.y();
        if (Math.abs(deltaY) < 1.0E-12) {
            if (start.y() < bounds.minY() || start.y() > bounds.maxY()) {
                return false;
            }
        } else {
            double firstT = (bounds.minY() - start.y()) / deltaY;
            double secondT = (bounds.maxY() - start.y()) / deltaY;
            if (firstT > secondT) {
                double swap = firstT;
                firstT = secondT;
                secondT = swap;
            }
            minT = Math.max(minT, firstT);
            maxT = Math.min(maxT, secondT);
            if (minT > maxT) {
                return false;
            }
        }

        double deltaZ = end.z() - start.z();
        if (Math.abs(deltaZ) < 1.0E-12) {
            if (start.z() < bounds.minZ() || start.z() > bounds.maxZ()) {
                return false;
            }
        } else {
            double firstT = (bounds.minZ() - start.z()) / deltaZ;
            double secondT = (bounds.maxZ() - start.z()) / deltaZ;
            if (firstT > secondT) {
                double swap = firstT;
                firstT = secondT;
                secondT = swap;
            }
            minT = Math.max(minT, firstT);
            maxT = Math.min(maxT, secondT);
            if (minT > maxT) {
                return false;
            }
        }
        return true;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

}
