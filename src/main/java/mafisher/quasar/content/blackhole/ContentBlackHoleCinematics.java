package mafisher.quasar.content.blackhole;

/**
 * Pure cinematic timeline for the content black hole.
 *
 * <p>The show has three acts: a core-sized space drill that snaps into a dark
 * horizon, an accretion ring that only forms after the horizon exists, and a
 * reverse drill that drains the ring before the horizon collapses. Every value
 * is a multiplier or an absolute factor derived only from the effect progress,
 * so the timeline is deterministic, duration independent, and headless-testable.</p>
 */
final class ContentBlackHoleCinematics {
    /** Progress at which the intro vortex ends and the steady phase begins. */
    static final float INTRO_END = 0.175F;
    /** Progress at which the vortex reaches full strength. */
    static final float VORTEX_END = 0.055F;
    /** Progress at which the vortex stops holding and starts to rebound. */
    static final float VORTEX_HOLD_END = 0.060F;
    /** Progress at which the rebound ends. */
    static final float SNAP_END = 0.072F;
    /** Progress at which the horizon snaps in; the audio ignition beat. */
    static final float IGNITION = 0.070F;
    /** Progress at which the horizon has settled. */
    static final float HORIZON_END = 0.085F;
    /** Progress at which the accretion ring starts forming. */
    static final float RING_START = 0.090F;
    /** Progress at which the accretion ring is complete. */
    static final float RING_END = 0.160F;
    /** Progress at which the destabilisation starts. */
    static final float DESTABILISE = 0.860F;
    /** Progress at which the final collapse starts. */
    static final float COLLAPSE = 0.905F;
    /** Progress at which the implosion starts. */
    static final float IMPLOSION = 0.945F;
    /** Progress at which the echo wave starts. */
    static final float ECHO = 0.968F;

    /**
     * Drill radius as a multiple of the effect scale. The visible shadow is
     * {@code B_CRITICAL} (~2.6x) the horizon, so 4.0 keeps the drill about 1.5x
     * the black core instead of hiding it inside the shadow.
     */
    private static final float DEFAULT_VORTEX_RADIUS = 4.0F;

    private ContentBlackHoleCinematics() {
    }

    static Frame frame(float progress) {
        float p = clamp01(progress);
        if (p < INTRO_END) {
            return intro(p);
        }
        if (p < DESTABILISE) {
            return steady();
        }
        return outro(p);
    }

    /** Multiplies the authored disk spin and procedural time. */
    static float cinematicAge(float ageTicks, float progress) {
        return Math.max(0.0F, ageTicks) * frame(progress).ageScale();
    }

    private static Frame intro(float p) {
        float fade = smooth(0.0F, 0.004F, p);
        float rise = smooth(0.0F, VORTEX_END, p);
        float snap = easeOutCubic(smooth(VORTEX_HOLD_END, SNAP_END, p));
        float decay = smooth(SNAP_END, HORIZON_END, p);
        float vortex = rise * 2.2F * (1.0F - snap) - 0.5F * snap * (1.0F - decay);

        float ignite = easeOutCubic(smooth(VORTEX_HOLD_END, 0.075F, p));
        float settle = smooth(0.075F, HORIZON_END, p);
        float horizon = lerp(lerp(0.05F, 1.06F, ignite), 1.0F, settle);
        float ringFormation = smooth(RING_START, RING_END, p);
        float disk = lerp(0.50F, 1.0F, easeOutCubic(ringFormation));
        float turbulence = 1.0F + 0.5F * (1.0F - smooth(0.020F, 0.100F, p));
        float age = lerp(0.20F, 1.0F, easeOutCubic(smooth(0.010F, 0.080F, p)));

        float expand = smooth(0.070F, 0.130F, p);
        float shockRadius = 8.0F * easeOutCubic(expand);
        float shockAbsorb = (1.0F - expand) * smooth(0.066F, 0.074F, p);
        float coreFlash = 0.16F * bump(p, 0.070F, 0.010F);
        float darkness = 0.30F * bump(p, 0.072F, 0.012F);
        return new Frame(
            horizon,
            disk,
            lerp(0.70F, 1.0F, smooth(0.0F, 0.035F, p)),
            ringFormation,
            ringFormation,
            ringFormation,
            turbulence,
            age,
            vortex,
            DEFAULT_VORTEX_RADIUS,
            shockRadius,
            0.08F * shockAbsorb,
            shockAbsorb,
            coreFlash,
            0.0F,
            darkness,
            fade
        );
    }

    private static Frame steady() {
        return new Frame(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F,
            0.0F, DEFAULT_VORTEX_RADIUS, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    }

    private static Frame outro(float p) {
        float destabilise = smooth(DESTABILISE, COLLAPSE, p);
        float collapse = easeInCubic(smooth(COLLAPSE, IMPLOSION, p));
        float implosion = smooth(IMPLOSION, 0.965F, p);
        float wave = smooth(IMPLOSION, 1.0F, p);
        float fade = 1.0F - smooth(0.990F, 1.0F, p);

        float vortex;
        if (p < IMPLOSION) {
            vortex = 1.4F * destabilise + 0.8F * smooth(COLLAPSE, IMPLOSION, p);
        } else if (p < 0.965F) {
            vortex = lerp(2.2F, -0.7F, smooth(IMPLOSION, 0.965F, p));
        } else {
            vortex = lerp(-0.7F, 0.0F, smooth(0.965F, 0.995F, p));
        }

        float horizon = lerp(1.0F, 0.20F, collapse) * (1.0F - easeInCubic(implosion));
        float disk = lerp(1.0F, 0.22F, collapse) * (1.0F - easeInCubic(implosion));
        float ringFormation = horizon;
        float warp = 1.0F - smooth(0.945F, 0.995F, p);
        float turbulence = 1.0F + 1.0F * destabilise + 1.0F * collapse;
        float age = 1.0F + 1.4F * destabilise + 1.2F * collapse + 2.0F * implosion;

        float shockRadius;
        float shockStrength;
        float shockAbsorb;
        if (p < IMPLOSION) {
            shockRadius = 8.0F * (1.0F - collapse);
            shockAbsorb = smooth(0.905F, 0.920F, p)
                * (1.0F - smooth(0.940F, IMPLOSION, p));
            shockStrength = 0.06F * shockAbsorb;
        } else {
            shockRadius = 8.0F * easeOutCubic(wave);
            float ramp = smooth(IMPLOSION, IMPLOSION + 0.002F, p);
            shockAbsorb = 0.90F * (1.0F - wave) * ramp;
            shockStrength = 0.06F * (1.0F - wave) * ramp;
        }
        if (shockAbsorb <= 0.001F) {
            shockRadius = 0.0F;
            shockStrength = 0.0F;
        }

        float coreFlash = 0.20F * bump(p, 0.952F, 0.008F);
        float darkness = 0.55F * bump(p, 0.950F, 0.012F);
        float jet = 0.50F * easeOutCubic(smooth(COLLAPSE, IMPLOSION, p)) * (1.0F - implosion);
        return new Frame(
            horizon,
            disk,
            warp,
            ringFormation,
            ringFormation,
            ringFormation,
            turbulence,
            age,
            vortex,
            DEFAULT_VORTEX_RADIUS,
            shockRadius,
            shockStrength,
            shockAbsorb,
            coreFlash,
            jet,
            darkness,
            fade
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp01(t);
    }

    private static float smooth(float from, float to, float value) {
        if (to <= from) {
            return value >= to ? 1.0F : 0.0F;
        }
        float t = clamp01((value - from) / (to - from));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutCubic(float value) {
        float t = 1.0F - clamp01(value);
        return 1.0F - t * t * t;
    }

    private static float easeInCubic(float value) {
        float t = clamp01(value);
        return t * t * t;
    }

    private static float bump(float value, float center, float halfWidth) {
        float offset = (value - center) / halfWidth;
        return (float)Math.exp(-offset * offset);
    }

    /**
     * One evaluated frame. All scale fields are multipliers around the authored
     * settings; {@code shockRadius} and {@code vortexRadiusScale} are in
     * multiples of the effect scale.
     */
    record Frame(
        float horizonScale,
        float diskScale,
        float lensWarpScale,
        float ringFormation,
        float diskBrightnessScale,
        float ringStrengthScale,
        float turbulenceScale,
        float ageScale,
        float vortexStrength,
        float vortexRadiusScale,
        float shockRadius,
        float shockStrength,
        float shockAbsorb,
        float coreFlash,
        float outroJet,
        float darkness,
        float fade
    ) {
    }
}
