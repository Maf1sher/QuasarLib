package mafisher.quasar.content.blackhole;

import mafisher.quasar.api.effect.debris.EffectDebrisAttractor;
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
}
