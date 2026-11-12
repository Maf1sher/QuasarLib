package mafisher.quasar.content.blackhole;

import mafisher.quasar.api.effect.EffectDebrisAttractor;
import net.minecraft.world.phys.Vec3;

/** Pure pacing and attractor tuning for the content black-hole debris. */
final class ContentBlackHoleDebris {
    static final int MIN_LIFETIME_TICKS = 10;
    static final double RADIAL_PULL = 1.35;

    private ContentBlackHoleDebris() {
    }

    /**
     * A straight radial fall into the horizon. {@code swirl = 0} keeps every piece on the line
     * from its torn block to the core instead of orbiting the hole.
     */
    static EffectDebrisAttractor attractor(Vec3 center, Vec3 axis, double coreRadius) {
        return new EffectDebrisAttractor(center, axis, coreRadius, RADIAL_PULL, 0.0);
    }

    /**
     * Travel time that keeps the fall readable: distance over a fixed blocks-per-tick speed,
     * clamped so near pieces do not blink out instantly and distant pieces do not orbit forever.
     */
    static int lifetimeTicks(double distance, double fallSpeed, int maxLifetimeTicks) {
        int maximum = Math.max(MIN_LIFETIME_TICKS, maxLifetimeTicks);
        if (!Double.isFinite(distance) || distance <= 0.0
            || !Double.isFinite(fallSpeed) || fallSpeed <= 0.0) {
            return MIN_LIFETIME_TICKS;
        }
        double raw = Math.ceil(distance / fallSpeed);
        if (!Double.isFinite(raw) || raw >= maximum) {
            return maximum;
        }
        return Math.max(MIN_LIFETIME_TICKS, (int)raw);
    }
}
