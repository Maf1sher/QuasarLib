package mafisher.quasar.content;

/**
 * Pure cinematic timeline for the content black hole.
 *
 * <p>The show has three acts: a space vortex that snaps into a light-absorbing
 * birth, a steady accretion phase, and a reverse vortex that ends in a dark
 * implosion and a space rebound. Every value is a multiplier or an absolute
 * factor derived only from the effect progress, so the timeline is
 * deterministic, duration independent, and headless-testable.</p>
 */
final class ContentBlackHoleCinematics {
    /** Progress at which the intro vortex ends and the steady phase begins. */
    static final float INTRO_END = 0.120F;
    /** Progress at which the vortex has fully formed and starts to rebound. */
    static final float VORTEX_END = 0.018F;
    /** Progress at which the rebound ends and the horizon has snapped in. */
    static final float SNAP_END = 0.032F;
    /** Progress at which the horizon snaps to full size; the audio ignition beat. */
    static final float IGNITION = 0.030F;
    /** Progress at which the destabilisation starts. */
    static final float DESTABILISE = 0.860F;
    /** Progress at which the final collapse starts. */
    static final float COLLAPSE = 0.910F;
    /** Progress at which the implosion starts. */
    static final float IMPLOSION = 0.945F;
    /** Progress at which the echo wave starts. */
    static final float ECHO = 0.968F;

    private static final float DEFAULT_VORTEX_RADIUS = 7.0F;

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
        float vortexRise = smooth(0.0F, VORTEX_END, p);
        float rebound = easeOutCubic(smooth(VORTEX_END, SNAP_END, p));
        float vortex = (1.6F * vortexRise - 1.95F * rebound)
            * (1.0F - smooth(SNAP_END, 0.045F, p));
        float ignite = easeOutCubic(smooth(VORTEX_END, SNAP_END, p));
        float settle = smooth(SNAP_END, 0.080F, p);
        float horizon = lerp(lerp(0.10F, 1.06F, ignite), 1.0F, settle);
        float disk = lerp(0.25F, 1.0F, easeOutCubic(smooth(VORTEX_END, 0.055F, p)));
        float diskBrightness = smooth(0.022F, 0.042F, p);
        float ring = smooth(0.020F, 0.038F, p);
        float turbulence = 1.0F + 0.4F * (1.0F - smooth(0.020F, 0.080F, p));
        float age = lerp(0.25F, 1.0F, easeOutCubic(smooth(0.010F, 0.055F, p)));
        float expand = smooth(0.024F, 0.075F, p);
        float shockRadius = 6.0F * easeOutCubic(expand);
        float shockAbsorb = (1.0F - expand) * smooth(0.022F, 0.028F, p);
        float coreFlash = 0.18F * bump(p, 0.030F, 0.010F);
        float darkness = 0.30F * bump(p, 0.031F, 0.012F);
        return new Frame(
            horizon,
            disk,
            lerp(0.70F, 1.0F, smooth(0.0F, 0.025F, p)),
            diskBrightness,
            ring,
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
        return new Frame(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F,
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
        float warp = 1.0F - smooth(0.945F, 0.995F, p);
        float diskBrightness = 1.0F + 0.15F * destabilise + 0.25F * collapse;
        float ring = 1.0F + 0.30F * destabilise + 0.50F * collapse;
        float turbulence = 1.0F + 1.0F * destabilise + 1.0F * collapse;
        float age = 1.0F + 1.4F * destabilise + 1.2F * collapse + 2.0F * implosion;

        float shockRadius;
        float shockStrength;
        float shockAbsorb;
        if (p < IMPLOSION) {
            shockRadius = 6.0F * (1.0F - collapse);
            shockAbsorb = smooth(0.915F, 0.940F, p)
                * (1.0F - smooth(0.940F, IMPLOSION, p));
            shockStrength = 0.06F * shockAbsorb;
        } else {
            shockRadius = 8.0F * easeOutCubic(wave);
            float waveRamp = smooth(IMPLOSION, IMPLOSION + 0.002F, p);
            shockAbsorb = 0.90F * (1.0F - wave) * waveRamp;
            shockStrength = 0.06F * (1.0F - wave) * waveRamp;
        }
        if (shockAbsorb <= 0.001F) {
            shockRadius = 0.0F;
            shockStrength = 0.0F;
        }

        float coreFlash = 0.22F * bump(p, 0.952F, 0.008F);
        float darkness = 0.55F * bump(p, 0.950F, 0.012F);
        float jet = 0.60F * easeOutCubic(smooth(COLLAPSE, IMPLOSION, p)) * (1.0F - implosion);
        return new Frame(
            horizon,
            disk,
            warp,
            diskBrightness,
            ring,
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
     * settings; {@code shockRadius} is in multiples of the effect scale and
     * {@code vortexRadiusScale} is in multiples of the effect scale.
     */
    record Frame(
        float horizonScale,
        float diskScale,
        float lensWarpScale,
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
