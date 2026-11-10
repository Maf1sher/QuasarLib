package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import mafisher.quasar.effect.EffectBounds;
import mafisher.quasar.effect.EffectPoint;

/** Minecraft-independent block volume used by budgeted block cursors. */
public sealed interface EffectBlockVolume permits EffectBlockVolume.Sphere, EffectBlockVolume.Box {
    EffectBounds bounds();

    boolean contains(double x, double y, double z);

    /** Whether one 16x16x16 section can contain any point of this volume. */
    boolean intersectsSection(int sectionX, int sectionY, int sectionZ);

    static Sphere sphere(EffectPoint center, double radius) {
        return new Sphere(center, radius);
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
