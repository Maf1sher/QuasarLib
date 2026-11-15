package mafisher.quasar.content.orbital;

import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;

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

    /** Damage applied to one target by one sustained-beam damage tick. */
    public static final float BEAM_DAMAGE = 4.0F;
    /** Delay before the beam can damage the same target again. */
    public static final int BEAM_DAMAGE_REHIT_TICKS = 10;
    /** Maximum living targets damaged by one sustained-beam interval. */
    public static final int MAX_BEAM_DAMAGE_TARGETS = 16;
    /** Server ticks between sustained-beam damage scans. */
    public static final int BEAM_DAMAGE_INTERVAL_TICKS = 4;
    /** Damage-cylinder radius as a fraction of the effective radius. */
    public static final float BEAM_DAMAGE_RADIUS_FACTOR = 1.0F;
    /** Impact-shockwave radius as a fraction of the effective radius. */
    public static final float SHOCKWAVE_RADIUS_FACTOR = SHOCK_MAX_RADIUS_FACTOR;
    /** Outward impact-shockwave velocity in blocks per tick. */
    public static final double SHOCKWAVE_STRENGTH = 0.8;
    /** Upward component of the impact shockwave. */
    public static final double SHOCKWAVE_LIFT = 0.35;
    /** Shockwave falloff exponent inside its radius. */
    public static final double SHOCKWAVE_FALLOFF = 1.0;
    /** Maximum entities examined by one impact shockwave. */
    public static final int MAX_SHOCKWAVE_TARGETS = 64;
    /** Maximum targets examined by one beam-damage hitbox call. */
    public static final int MAX_BEAM_TARGETS_EXAMINED = 64;
    /** Bulk section edits allowed per server tick for the burn. */
    public static final int MAX_SECTION_EDITS = 8;

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

    /** Progress at which the ground carve starts: the impact phase. */
    public static float carveWindowStart() {
        return (TARGET_TICKS + DESCENT_TICKS) / (float)TOTAL_TICKS;
    }

    /** Progress at which ground edits stop: the collapse phase. */
    public static float carveWindowEnd() {
        return (TARGET_TICKS + DESCENT_TICKS + IMPACT_TICKS + SUSTAIN_TICKS)
            / (float)TOTAL_TICKS;
    }

    /** Per-tick budgets for the destructive orbital-laser session. */
    public static EffectTickBudget gameplayBudget() {
        return new EffectTickBudget(
            MAX_BEAM_TARGETS_EXAMINED,
            1,
            0,
            0,
            0,
            MAX_SHOCKWAVE_TARGETS,
            0,
            MAX_SECTION_EDITS
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
