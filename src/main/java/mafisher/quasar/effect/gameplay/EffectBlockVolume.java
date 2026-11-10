package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import mafisher.quasar.effect.EffectBounds;
import mafisher.quasar.effect.EffectPoint;

/** Minecraft-independent block volume used by budgeted block cursors. */
public sealed interface EffectBlockVolume
    permits EffectBlockVolume.Sphere,
    EffectBlockVolume.Ellipsoid,
    EffectBlockVolume.Cylinder,
    EffectBlockVolume.Box {
    EffectBounds bounds();

    boolean contains(double x, double y, double z);

    /** Whether one 16x16x16 section can contain any point of this volume. */
    boolean intersectsSection(int sectionX, int sectionY, int sectionZ);

    static Sphere sphere(EffectPoint center, double radius) {
        return new Sphere(center, radius);
    }

    static Ellipsoid ellipsoid(
        EffectPoint center,
        double radiusX,
        double radiusY,
        double radiusZ
    ) {
        return new Ellipsoid(center, radiusX, radiusY, radiusZ);
    }

    static Cylinder cylinder(EffectPoint center, double radius, double minY, double maxY) {
        return new Cylinder(center, radius, minY, maxY);
    }

    static Box box(EffectBounds bounds) {
        return new Box(bounds);
    }

    /** Closed sphere; block positions are tested by their integer block centre. */
    record Sphere(EffectPoint center, double radius) implements EffectBlockVolume {
        public Sphere {
            Objects.requireNonNull(center, "center");
            if (!Double.isFinite(radius) || radius <= 0.0) {
                throw new IllegalArgumentException("Block sphere radius must be finite and positive");
            }
        }

        @Override
        public EffectBounds bounds() {
            return new EffectBounds(
                this.center.x() - this.radius, this.center.y() - this.radius, this.center.z() - this.radius,
                this.center.x() + this.radius, this.center.y() + this.radius, this.center.z() + this.radius
            );
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double dx = x - this.center.x();
            double dy = y - this.center.y();
            double dz = z - this.center.z();
            return dx * dx + dy * dy + dz * dz <= this.radius * this.radius;
        }

        @Override
        public boolean intersectsSection(int sectionX, int sectionY, int sectionZ) {
            double minX = (double)sectionX * 16.0;
            double minY = (double)sectionY * 16.0;
            double minZ = (double)sectionZ * 16.0;
            double dx = this.center.x() - Math.max(minX, Math.min(minX + 16.0, this.center.x()));
            double dy = this.center.y() - Math.max(minY, Math.min(minY + 16.0, this.center.y()));
            double dz = this.center.z() - Math.max(minZ, Math.min(minZ + 16.0, this.center.z()));
            return dx * dx + dy * dy + dz * dz <= this.radius * this.radius;
        }
    }

    /** Closed axis-aligned ellipsoid; block positions are tested by their integer block centre. */
    record Ellipsoid(
        EffectPoint center,
        double radiusX,
        double radiusY,
        double radiusZ
    ) implements EffectBlockVolume {
        public Ellipsoid {
            Objects.requireNonNull(center, "center");
            if (!Double.isFinite(radiusX) || radiusX <= 0.0
                || !Double.isFinite(radiusY) || radiusY <= 0.0
                || !Double.isFinite(radiusZ) || radiusZ <= 0.0) {
                throw new IllegalArgumentException("Block ellipsoid radii must be finite and positive");
            }
        }

        @Override
        public EffectBounds bounds() {
            return new EffectBounds(
                this.center.x() - this.radiusX,
                this.center.y() - this.radiusY,
                this.center.z() - this.radiusZ,
                this.center.x() + this.radiusX,
                this.center.y() + this.radiusY,
                this.center.z() + this.radiusZ
            );
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double dx = (x - this.center.x()) / this.radiusX;
            double dy = (y - this.center.y()) / this.radiusY;
            double dz = (z - this.center.z()) / this.radiusZ;
            return dx * dx + dy * dy + dz * dz <= 1.0;
        }

        @Override
        public boolean intersectsSection(int sectionX, int sectionY, int sectionZ) {
            double minX = (double)sectionX * 16.0;
            double minY = (double)sectionY * 16.0;
            double minZ = (double)sectionZ * 16.0;
            double dx = (this.center.x() - Math.max(minX, Math.min(minX + 16.0, this.center.x())))
                / this.radiusX;
            double dy = (this.center.y() - Math.max(minY, Math.min(minY + 16.0, this.center.y())))
                / this.radiusY;
            double dz = (this.center.z() - Math.max(minZ, Math.min(minZ + 16.0, this.center.z())))
                / this.radiusZ;
            return dx * dx + dy * dy + dz * dz <= 1.0;
        }
    }

    /** Closed vertical cylinder; the radius is measured in the X/Z plane. */
    record Cylinder(EffectPoint center, double radius, double minY, double maxY)
        implements EffectBlockVolume {
        public Cylinder {
            Objects.requireNonNull(center, "center");
            if (!Double.isFinite(radius) || radius <= 0.0) {
                throw new IllegalArgumentException("Block cylinder radius must be finite and positive");
            }
            if (!Double.isFinite(minY) || !Double.isFinite(maxY) || maxY < minY) {
                throw new IllegalArgumentException("Block cylinder height must be finite and ordered");
            }
        }

        @Override
        public EffectBounds bounds() {
            return new EffectBounds(
                this.center.x() - this.radius,
                this.minY,
                this.center.z() - this.radius,
                this.center.x() + this.radius,
                this.maxY,
                this.center.z() + this.radius
            );
        }

        @Override
        public boolean contains(double x, double y, double z) {
            if (y < this.minY || y > this.maxY) {
                return false;
            }
            double dx = x - this.center.x();
            double dz = z - this.center.z();
            return dx * dx + dz * dz <= this.radius * this.radius;
        }

        @Override
        public boolean intersectsSection(int sectionX, int sectionY, int sectionZ) {
            double minX = (double)sectionX * 16.0;
            double minY = (double)sectionY * 16.0;
            double minZ = (double)sectionZ * 16.0;
            if (minY + 16.0 < this.minY || minY > this.maxY) {
                return false;
            }
            double dx = this.center.x() - Math.max(minX, Math.min(minX + 16.0, this.center.x()));
            double dz = this.center.z() - Math.max(minZ, Math.min(minZ + 16.0, this.center.z()));
            return dx * dx + dz * dz <= this.radius * this.radius;
        }
    }

    /** Closed axis-aligned box. */
    record Box(EffectBounds bounds) implements EffectBlockVolume {
        public Box {
            Objects.requireNonNull(bounds, "bounds");
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return x >= this.bounds.minX() && x <= this.bounds.maxX()
                && y >= this.bounds.minY() && y <= this.bounds.maxY()
                && z >= this.bounds.minZ() && z <= this.bounds.maxZ();
        }

        @Override
        public boolean intersectsSection(int sectionX, int sectionY, int sectionZ) {
            double minX = (double)sectionX * 16.0;
            double minY = (double)sectionY * 16.0;
            double minZ = (double)sectionZ * 16.0;
            return this.bounds.maxX() >= minX && this.bounds.minX() <= minX + 16.0
                && this.bounds.maxY() >= minY && this.bounds.minY() <= minY + 16.0
                && this.bounds.maxZ() >= minZ && this.bounds.minZ() <= minZ + 16.0;
        }
    }
}
