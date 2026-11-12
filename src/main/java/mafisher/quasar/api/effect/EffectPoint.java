package mafisher.quasar.api.effect;

/** Minecraft-independent world-space point used by effect hitbox geometry. */
public record EffectPoint(double x, double y, double z) {
    public static final EffectPoint ZERO = new EffectPoint(0.0, 0.0, 0.0);

    public EffectPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Effect point coordinates must be finite");
        }
    }

    public double distanceToSqr(double otherX, double otherY, double otherZ) {
        double dx = this.x - otherX;
        double dy = this.y - otherY;
        double dz = this.z - otherZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
