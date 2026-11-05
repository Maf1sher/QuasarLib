package mafisher.quasar.effect;

/** Minecraft-independent axis-aligned bounds for effect hitbox math and broad-phase queries. */
public record EffectBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public EffectBounds {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
            || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Effect bounds must be finite and ordered");
        }
    }

    public EffectBounds inflate(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Inflation must be finite and non-negative");
        }
        return new EffectBounds(
            this.minX - amount, this.minY - amount, this.minZ - amount,
            this.maxX + amount, this.maxY + amount, this.maxZ + amount
        );
    }

    public boolean intersects(EffectBounds other) {
        return this.maxX > other.minX && this.minX < other.maxX
            && this.maxY > other.minY && this.minY < other.maxY
            && this.maxZ > other.minZ && this.minZ < other.maxZ;
    }

    public boolean contains(EffectPoint point) {
        return point.x() >= this.minX && point.x() <= this.maxX
            && point.y() >= this.minY && point.y() <= this.maxY
            && point.z() >= this.minZ && point.z() <= this.maxZ;
    }

    public EffectPoint closestPoint(EffectPoint point) {
        return new EffectPoint(
            clamp(point.x(), this.minX, this.maxX),
            clamp(point.y(), this.minY, this.maxY),
            clamp(point.z(), this.minZ, this.maxZ)
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
