package mafisher.quasar.content.orbital;

import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;

/** Content-owned timing, target limits, and phase plan for the orbital-laser acceptance test. */
public final class ContentOrbitalLaserSettings {
    /** Smallest accepted command radius. */
    public static final int MIN_RADIUS = 2;
    /** Largest accepted command radius. */
    public static final int MAX_RADIUS = 32;
    /** Radius used when the command omits the argument. */
    public static final int DEFAULT_RADIUS = 6;

    /** Duration of the target-lock phase. */
    public static final int TARGET_TICKS = 40;
    /** Duration of the beam-descent phase. */
    public static final int DESCENT_TICKS = 20;
    /** Duration of the impact phase. */
    public static final int IMPACT_TICKS = 4;
    /** Duration of the sustained-beam phase. */
    public static final int SUSTAIN_TICKS = 100;
    /** Duration of the collapse phase. */
    public static final int COLLAPSE_TICKS = 30;
    /** Duration of the aftermath phase. */
    public static final int AFTERMATH_TICKS = 26;
    /** Total show duration derived from the six phases. */
    public static final int TOTAL_TICKS = TARGET_TICKS + DESCENT_TICKS + IMPACT_TICKS
        + SUSTAIN_TICKS + COLLAPSE_TICKS + AFTERMATH_TICKS;

    /** Phase index of the target lock. */
    public static final int PHASE_TARGET = 0;
    /** Phase index of the beam descent. */
    public static final int PHASE_DESCENT = 1;
    /** Phase index of the impact. */
    public static final int PHASE_IMPACT = 2;
    /** Phase index of the sustained beam. */
    public static final int PHASE_SUSTAIN = 3;
    /** Phase index of the collapse. */
    public static final int PHASE_COLLAPSE = 4;
    /** Phase index of the aftermath. */
    public static final int PHASE_AFTERMATH = 5;

    /** Height above the first free column Y used to keep the marker clear of the surface. */
    public static final double MARKER_LIFT = 0.05;
    /** Maximum command-time ray length from the source eye to the aimed block. */
    public static final double MAX_TARGET_DISTANCE = 256.0;
    /** Audience for the showpiece visual. */
    public static final EffectAudience AUDIENCE = EffectAudience.dimensionWide();

    /** Beam extent above the resolved column base. */
    public static final float BEAM_TOP_HEIGHT = 256.0F;
    /** Solid plasma core radius as a fraction of the requested radius. */
    public static final float BEAM_CORE_RADIUS_FACTOR = 0.62F;
    /** Additive plasma envelope radius as a fraction of the requested radius. */
    public static final float BEAM_GLOW_RADIUS_FACTOR = 1.60F;
    /** Maximum shock-ring radius as a fraction of the requested radius. */
    public static final float SHOCK_MAX_RADIUS_FACTOR = 3.5F;
    /** Impact-flash radius as a fraction of the requested radius. */
    public static final float IMPACT_FLASH_RADIUS_FACTOR = 2.0F;
    /** Minimum composition scale for the fullscreen beam operation. */
    public static final float BEAM_COMPOSITION_FLOOR = 0.75F;

    private ContentOrbitalLaserSettings() {
    }

    /** Builds the six-phase plan shared by the session and its content tests. */
    public static EffectPhasePlan phasePlan() {
        return EffectPhasePlan.of(
            new EffectPhase("target", TARGET_TICKS),
            new EffectPhase("descent", DESCENT_TICKS),
            new EffectPhase("impact", IMPACT_TICKS),
            new EffectPhase("sustain", SUSTAIN_TICKS),
            new EffectPhase("collapse", COLLAPSE_TICKS),
            new EffectPhase("aftermath", AFTERMATH_TICKS)
        );
    }

    /** Validates a radius for direct callers that bypass the command argument. */
    public static float requireRadius(int radius) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException(
                "Orbital-laser radius must be between " + MIN_RADIUS + " and " + MAX_RADIUS
            );
        }
        return radius;
    }
}
