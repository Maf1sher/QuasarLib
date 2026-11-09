package mafisher.quasar.content;

/**
 * Pure cinematic timeline for the content black hole.
 *
 * <p>The show has three acts: a lens-first birth, a steady accretion phase, and a
 * collapse that ends in an implosion and an echo. Every value is a multiplier or
 * an absolute factor derived only from the effect progress, so the timeline is
 * deterministic, duration independent, and headless-testable.</p>
 */
final class ContentBlackHoleCinematics {
    /** Progress at which the intro ends and the steady phase begins. */
    static final float INTRO_END = 0.130F;
    /** Progress at which the horizon snaps to full size. */
    static final float IGNITION = 0.040F;
    /** Progress at which the destabilisation starts. */
    static final float DESTABILISE = 0.880F;
    /** Progress at which the final collapse starts. */
    static final float COLLAPSE = 0.930F;
    /** Progress at which the implosion starts. */
    static final float IMPLOSION = 0.960F;
    /** Progress at which the echo ring starts expanding. */
    static final float ECHO = 0.972F;

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
        float tension = smooth(0.0F, 0.012F, p);
        float ignite = easeOutCubic(smooth(0.012F, IGNITION, p));
        float settle = smooth(IGNITION, 0.085F, p);
        float shock = smooth(IGNITION, 0.090F, p);
        float diskIgnition = smooth(0.020F, 0.065F, p);
        float brightness = smooth(0.030F, 0.052F, p)
            * (1.0F + 1.6F * (1.0F - smooth(0.052F, 0.105F, p)));
        float ring = smooth(0.028F, 0.045F, p)
            * (1.0F + 5.0F * (1.0F - smooth(0.050F, 0.120F, p)));
        float turbulence = 1.0F + 1.5F * (1.0F - smooth(0.050F, 0.120F, p))
            * smooth(0.028F, 0.045F, p);

        float horizon = lerp(lerp(0.45F, 1.08F, ignite), 1.0F, settle);
        float shockRadius = 7.5F * easeOutCubic(shock);
        float shockStrength = (1.0F - shock) * smooth(IGNITION, 0.048F, p);
        return new Frame(
            horizon,
            lerp(0.30F, 1.0F, easeOutCubic(diskIgnition)),
            lerp(0.65F, 1.0F, smooth(0.0F, 0.025F, p))
                + 0.35F * bump(p, 0.045F, 0.030F),
            brightness,
            ring,
            turbulence,
            lerp(0.35F, 1.0F, easeOutCubic(smooth(0.010F, 0.060F, p))),
            shockStrength > 0.001F ? shockRadius : 0.0F,
            shockStrength,
            bump(p, 0.045F, 0.016F),
            0.0F,
            tension
        );
    }

    private static Frame steady() {
        return new Frame(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F,
            0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    }

    private static Frame outro(float p) {
        float destabilise = smooth(DESTABILISE, COLLAPSE, p);
        float collapse = easeInCubic(smooth(COLLAPSE, IMPLOSION, p));
        float implosion = smooth(IMPLOSION, 0.978F, p);
        float echo = smooth(ECHO, 1.0F, p);
        float fade = 1.0F - smooth(0.985F, 1.0F, p);

        float horizon = lerp(1.0F, 0.18F, collapse) * (1.0F - easeInCubic(implosion));
        float disk = lerp(1.0F, 0.22F, collapse) * (1.0F - easeInCubic(implosion));
        float warp = 1.0F - smooth(0.945F, 0.990F, p);

        float shockRadius;
        float shockStrength;
        if (p < IMPLOSION) {
            shockRadius = 7.5F * (1.0F - collapse);
            shockStrength = smooth(COLLAPSE, 0.936F, p)
                * (1.0F - smooth(0.954F, IMPLOSION, p));
        } else {
            shockRadius = 7.8F * easeOutCubic(echo);
            shockStrength = 0.30F * (1.0F - echo);
        }
        if (shockStrength <= 0.001F) {
            shockRadius = 0.0F;
        }

        float flash = (float)Math.sin(Math.PI * implosion);
        float jet = easeOutCubic(smooth(COLLAPSE, IMPLOSION, p)) * (1.0F - implosion);
        return new Frame(
            horizon,
            disk,
            warp,
            1.0F + 1.0F * destabilise + 1.5F * collapse,
            1.0F + 1.5F * destabilise + 2.5F * collapse,
            1.0F + 1.2F * destabilise + 1.0F * collapse,
            1.0F + 1.6F * destabilise + 1.4F * collapse + 2.0F * implosion,
            shockRadius,
            shockStrength,
            flash,
            jet,
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
     * settings; {@code shockRadius} is in multiples of the effect scale.
     */
    record Frame(
        float horizonScale,
        float diskScale,
        float lensWarpScale,
        float diskBrightnessScale,
        float ringStrengthScale,
        float turbulenceScale,
        float ageScale,
        float shockRadius,
        float shockStrength,
        float coreFlash,
        float outroJet,
        float fade
    ) {
    }
}
