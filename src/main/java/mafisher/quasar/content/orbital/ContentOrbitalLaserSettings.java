package mafisher.quasar.content.orbital;

import java.util.Objects;
import mafisher.quasar.api.audio.EffectPhaseTiming;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
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
    /** Duration of the platform-arrival phase. */
    public static final int ARRIVAL_TICKS = 40;
    /** Duration of the platform-charge phase. */
    public static final int CHARGE_TICKS = 40;
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
    /** Total show duration derived from the eight phases. */
    public static final int TOTAL_TICKS = TARGET_TICKS + ARRIVAL_TICKS + CHARGE_TICKS
        + DESCENT_TICKS + IMPACT_TICKS + SUSTAIN_TICKS + COLLAPSE_TICKS + AFTERMATH_TICKS;

    /** Phase index of the target lock. */
    public static final int PHASE_TARGET = 0;
    /** Phase index of the platform arrival. */
    public static final int PHASE_ARRIVAL = 1;
    /** Phase index of the platform charge. */
    public static final int PHASE_CHARGE = 2;
    /** Phase index of the beam descent. */
    public static final int PHASE_DESCENT = 3;
    /** Phase index of the impact. */
    public static final int PHASE_IMPACT = 4;
    /** Phase index of the sustained beam. */
    public static final int PHASE_SUSTAIN = 5;
    /** Phase index of the collapse. */
    public static final int PHASE_COLLAPSE = 6;
    /** Phase index of the aftermath. */
    public static final int PHASE_AFTERMATH = 7;

    /** Phase name of the target lock. */
    static final String PHASE_NAME_TARGET = "target";
    /** Phase name of the platform arrival. */
    static final String PHASE_NAME_ARRIVAL = "arrival";
    /** Phase name of the platform charge. */
    static final String PHASE_NAME_CHARGE = "charge";
    /** Phase name of the beam descent. */
    static final String PHASE_NAME_DESCENT = "descent";
    /** Phase name of the impact. */
    static final String PHASE_NAME_IMPACT = "impact";
    /** Phase name of the sustained beam. */
    static final String PHASE_NAME_SUSTAIN = "sustain";
    /** Phase name of the collapse. */
    static final String PHASE_NAME_COLLAPSE = "collapse";
    /** Phase name of the aftermath. */
    static final String PHASE_NAME_AFTERMATH = "aftermath";

    /** Height above the first free column Y used to keep the marker clear of the surface. */
    public static final double MARKER_LIFT = 0.05;
    /** Maximum command-time ray length from the source eye to the aimed block. */
    public static final double MAX_TARGET_DISTANCE = 256.0;
    /** Audience for the showpiece visual. */
    public static final EffectAudience AUDIENCE = EffectAudience.dimensionWide();

    /**
     * Beam extent above the resolved column base. The beam starts at the platform muzzle, which
     * sits {@link ContentOrbitalLaserPlatformSettings#PLATFORM_MUZZLE_DROP} below the hub.
     */
    public static final float BEAM_TOP_HEIGHT =
        ContentOrbitalLaserPlatformSettings.PLATFORM_ALTITUDE
            - ContentOrbitalLaserPlatformSettings.PLATFORM_MUZZLE_DROP;
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

    /** Builds the eight-phase plan shared by the session and its content tests. */
    public static EffectPhasePlan phasePlan() {
        return EffectPhasePlan.of(
            new EffectPhase(PHASE_NAME_TARGET, TARGET_TICKS),
            new EffectPhase(PHASE_NAME_ARRIVAL, ARRIVAL_TICKS),
            new EffectPhase(PHASE_NAME_CHARGE, CHARGE_TICKS),
            new EffectPhase(PHASE_NAME_DESCENT, DESCENT_TICKS),
            new EffectPhase(PHASE_NAME_IMPACT, IMPACT_TICKS),
            new EffectPhase(PHASE_NAME_SUSTAIN, SUSTAIN_TICKS),
            new EffectPhase(PHASE_NAME_COLLAPSE, COLLAPSE_TICKS),
            new EffectPhase(PHASE_NAME_AFTERMATH, AFTERMATH_TICKS)
        );
    }

    /** Progress at which the ground carve starts: the impact phase. */
    public static float carveWindowStart() {
        return (TARGET_TICKS + ARRIVAL_TICKS + CHARGE_TICKS + DESCENT_TICKS)
            / (float)TOTAL_TICKS;
    }

    /** Progress at which ground edits stop: the collapse phase. */
    public static float carveWindowEnd() {
        return (TARGET_TICKS + ARRIVAL_TICKS + CHARGE_TICKS + DESCENT_TICKS
            + IMPACT_TICKS + SUSTAIN_TICKS) / (float)TOTAL_TICKS;
    }

    /**
     * Returns whether the context's persisted phase plan names the current phase {@code phaseName}.
     *
     * <p>Restored sessions keep the phase list saved with their descriptor, so a legacy six-phase
     * plan has different indices than the current eight-phase plan. Content control flow compares
     * names so resume still opens the impact and sustain windows at the right point.</p>
     */
    static boolean isInPhase(GameplayEffectContext context, String phaseName) {
        Objects.requireNonNull(context, "context");
        return isInPhase(context.spec().phases(), context.phaseIndex(), phaseName);
    }

    /** Returns whether {@code phases} names the phase at {@code phaseIndex} {@code phaseName}. */
    static boolean isInPhase(EffectPhaseTiming phases, int phaseIndex, String phaseName) {
        Objects.requireNonNull(phases, "phases");
        Objects.requireNonNull(phaseName, "phaseName");
        return phaseIndex >= 0 && phaseIndex < phases.phaseCount()
            && phaseName.equals(phases.phaseName(phaseIndex));
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
