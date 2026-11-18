package mafisher.quasar.content.orbital;

import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.api.effect.EffectEasing;
import mafisher.quasar.api.effect.EffectParameterSet;
import mafisher.quasar.api.effect.EffectParameterState;

/**
 * Duration-independent curves for the orbital-laser beam show.
 *
 * <p>Every curve is anchored to {@link ContentOrbitalLaserSettings#phasePlan()}, so phase edits move
 * the visuals with the session instead of requiring hand-tuned absolute progress values. The marker
 * handoff, beam descent, impact flash, shock ring, collapse narrowing/snap, final flash, and rim
 * glow all read the same parameter set.</p>
 */
@SuppressWarnings("PMD.TooManyMethods")
final class ContentOrbitalLaserCinematics {
    /** One tick in normalized progress. */
    private static final float TICK = 1.0F / ContentOrbitalLaserSettings.TOTAL_TICKS;
    /** Descent progress at which the marker starts fading under the beam head. */
    private static final float MARKER_FADE_START = 0.55F
        * ContentOrbitalLaserSettings.DESCENT_TICKS * TICK;
    /** Time for the impact flash to decay. */
    private static final float FLASH_DECAY = 10.0F * TICK;
    /** Time for the shock ring to reach its maximum radius. */
    private static final float SHOCK_EXPAND = 16.0F * TICK;
    /** Time for the shock ring to fade out. */
    private static final float SHOCK_DECAY = 22.0F * TICK;
    /** Collapse progress at which the beam starts narrowing. */
    private static final float COLLAPSE_NARROW_START = 6.0F * TICK;
    /** Collapse progress at which the beam reaches its narrowest point and snaps off. */
    private static final float COLLAPSE_SNAP = 24.0F * TICK;
    /** Collapse progress at which the narrow beam has fully disappeared. */
    private static final float COLLAPSE_SNAP_END = 26.0F * TICK;
    /** Time for the final flash to decay into the aftermath. */
    private static final float FINAL_FLASH_DECAY = 6.0F * TICK;
    /** Collapse progress at which the hot hole rim starts to glow. */
    private static final float RIM_GLOW_START = 20.0F * TICK;
    /** Aftermath progress at which the rim glow has faded out. */
    private static final float RIM_GLOW_END = 20.0F * TICK;
    /** Aftermath progress at which the screen operation stops drawing. */
    private static final float SCREEN_VISIBLE_END = 22.0F * TICK;
    /** Time for the platform reveal scan to reach the hub. */
    private static final float PLATFORM_REVEAL_TIME = 20.0F * TICK;
    /** Time for the platform warp-out to finish inside the collapse phase. */
    private static final float PLATFORM_EXIT_TIME = 20.0F * TICK;
    /** Recoil decay after the muzzle fires. */
    private static final float PLATFORM_RECOIL_TIME = 8.0F * TICK;
    /** Muzzle-flash decay after the muzzle fires. */
    private static final float MUZZLE_FLASH_TIME = 6.0F * TICK;
    /** Time over which the charge releases into the shot. */
    private static final float PLATFORM_CHARGE_RELEASE = 4.0F * TICK;

    /** Shared curve name for master platform visibility. */
    static final String PLATFORM_VISIBLE = "PlatformVisible";
    /** Shared curve name for the bottom-up arrival reveal. */
    static final String PLATFORM_REVEAL = "PlatformReveal";
    /** Shared curve name for the normalized idle and charge rotation. */
    static final String PLATFORM_SPIN = "PlatformSpin";
    /** Shared curve name for the charge build and release. */
    static final String PLATFORM_CHARGE = "PlatformCharge";
    /** Shared curve name for the recoil after the shot. */
    static final String PLATFORM_RECOIL = "PlatformRecoil";
    /** Shared curve name for the top-down warp-out. */
    static final String PLATFORM_EXIT = "PlatformExit";
    /** Shared curve name for the muzzle flash. */
    static final String MUZZLE_FLASH = "MuzzleFlash";

    /** Server-authored multiplier for the effective beam and impact footprint. */
    static final String BEAM_RADIUS_SCALE = "BeamRadiusScale";
    /** Server-authored beam depth below the surface anchor. */
    static final String TIP_DEPTH = "TipDepth";

    static final EffectParameterSet PARAMETERS = buildParameters();

    private ContentOrbitalLaserCinematics() {
    }

    static EffectParameterSet parameters() {
        return PARAMETERS;
    }

    /** Returns whether the beam or impact emission has any visible work at this progress. */
    static boolean beamVisible(float progress) {
        return PARAMETERS.sampleFloat("Visible", progress) > 0.001F;
    }

    /** Returns whether the platform hull has any visible work at this progress. */
    static boolean platformVisible(float progress) {
        return PARAMETERS.sampleFloat(PLATFORM_VISIBLE, progress) > 0.001F;
    }

    /** Returns whether the screen operation has any visible work at this progress. */
    static boolean screenVisible(float progress) {
        return beamVisible(progress) || platformVisible(progress);
    }

    /** Returns the marker alpha for the world pass at this progress. */
    static float markerAlpha(float progress) {
        return PARAMETERS.sampleFloat("MarkerAlpha", progress);
    }

    /** Returns the clamped effective-radius multiplier from server overrides. */
    static float beamRadiusScale(float progress, EffectParameterState parameters) {
        float value = PARAMETERS.sampleFloat(BEAM_RADIUS_SCALE, progress, parameters);
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 1.0F;
    }

    /** Returns the non-negative beam depth below the surface anchor. */
    static float tipDepth(float progress, EffectParameterState parameters) {
        float value = PARAMETERS.sampleFloat(TIP_DEPTH, progress, parameters);
        return Float.isFinite(value) && value > 0.0F ? value : 0.0F;
    }

    /** Normalized progress at which the beam snaps off. */
    static float collapseSnapProgress() {
        return phaseStartProgress(ContentOrbitalLaserSettings.PHASE_COLLAPSE) + COLLAPSE_SNAP;
    }

    /** Normalized progress at which the narrow beam has fully disappeared. */
    static float collapseSnapEndProgress() {
        return phaseStartProgress(ContentOrbitalLaserSettings.PHASE_COLLAPSE) + COLLAPSE_SNAP_END;
    }

    /** Normalized progress at which the aftermath starts. */
    static float aftermathStartProgress() {
        return phaseStartProgress(ContentOrbitalLaserSettings.PHASE_AFTERMATH);
    }

    private static float phaseStartProgress(int phaseIndex) {
        return ContentOrbitalLaserSettings.phasePlan().phaseStartTick(phaseIndex)
            / (float)ContentOrbitalLaserSettings.TOTAL_TICKS;
    }

    private static EffectParameterSet buildParameters() {
        EffectParameterSet.Builder builder = EffectParameterSet.builder()
            .anchors(ContentOrbitalLaserSettings.phasePlan());
        addBeamCurves(builder);
        addMarkerCurve(builder);
        addImpactCurves(builder);
        addCollapseCurves(builder);
        addPlatformCurves(builder);
        addServerParameters(builder);
        return builder.build();
    }

    private static void addBeamCurves(EffectParameterSet.Builder builder) {
        builder.curve("Visible", curve(
            key(0.0F, 0.0F),
            key("descent", -TICK, 0.0F, EffectEasing.LINEAR),
            key("descent", 0.0F, 1.0F, EffectEasing.LINEAR),
            key("aftermath", SCREEN_VISIBLE_END, 1.0F),
            key("aftermath", SCREEN_VISIBLE_END + 2.0F * TICK, 0.0F, EffectEasing.LINEAR),
            key(1.0F, 0.0F)
        ));
        builder.curve("BeamTip", curve(
            key(0.0F, 1.0F),
            key("descent", 0.0F, 1.0F, EffectEasing.EASE_IN_CUBIC),
            key("impact", 0.0F, 0.0F),
            key(1.0F, 0.0F)
        ));
        builder.curve("BeamStrength", curve(
            key(0.0F, 0.0F),
            key("descent", -TICK, 0.0F, EffectEasing.LINEAR),
            key("descent", 0.0F, 1.0F, EffectEasing.LINEAR),
            key("collapse", 0.0F, 1.0F),
            key("collapse", COLLAPSE_SNAP, 1.0F, EffectEasing.LINEAR),
            key("collapse", COLLAPSE_SNAP_END, 0.0F, EffectEasing.LINEAR),
            key(1.0F, 0.0F)
        ));
        builder.curve("BeamWidth", curve(
            key(0.0F, 0.0F),
            key("descent", -TICK, 0.0F, EffectEasing.LINEAR),
            key("descent", 0.0F, 1.0F, EffectEasing.LINEAR),
            key("collapse", 0.0F, 1.0F),
            key("collapse", COLLAPSE_NARROW_START, 0.94F, EffectEasing.EASE_IN),
            key("collapse", COLLAPSE_SNAP, 0.12F, EffectEasing.EASE_IN_CUBIC),
            key("collapse", COLLAPSE_SNAP_END, 0.04F, EffectEasing.LINEAR),
            key("aftermath", 0.0F, 0.0F),
            key(1.0F, 0.0F)
        ));
    }

    private static void addMarkerCurve(EffectParameterSet.Builder builder) {
        builder.curve("MarkerAlpha", curve(
            key(0.0F, 1.0F),
            key("descent", 0.0F, 1.0F),
            key("descent", MARKER_FADE_START, 1.0F),
            key("impact", 0.0F, 0.0F, EffectEasing.EASE_IN_OUT),
            key(1.0F, 0.0F)
        ));
    }

    private static void addImpactCurves(EffectParameterSet.Builder builder) {
        builder.curve("ImpactFlash", curve(
            key(0.0F, 0.0F),
            key("impact", -TICK, 0.0F, EffectEasing.LINEAR),
            key("impact", 0.0F, 1.0F, EffectEasing.LINEAR),
            key("impact", FLASH_DECAY, 0.0F, EffectEasing.LINEAR),
            key(1.0F, 0.0F)
        ));
        builder.curve("ShockRadiusScale", curve(
            key(0.0F, 0.0F),
            key("impact", -TICK, 0.0F, EffectEasing.LINEAR),
            key("impact", 0.0F, 0.35F, EffectEasing.EASE_OUT),
            key("impact", SHOCK_EXPAND, 1.0F),
            key("collapse", 0.0F, 1.0F),
            key("aftermath", 0.0F, 0.0F),
            key(1.0F, 0.0F)
        ));
        builder.curve("ShockStrength", curve(
            key(0.0F, 0.0F),
            key("impact", -TICK, 0.0F, EffectEasing.LINEAR),
            key("impact", 0.0F, 1.0F, EffectEasing.LINEAR),
            key("impact", SHOCK_DECAY, 0.0F, EffectEasing.LINEAR),
            key(1.0F, 0.0F)
        ));
        builder.curve("ContactGlow", curve(
            key(0.0F, 0.0F),
            key("impact", -TICK, 0.0F, EffectEasing.LINEAR),
            key("impact", 0.0F, 0.8F, EffectEasing.LINEAR),
            key("sustain", 0.0F, 1.0F),
            key("collapse", 0.0F, 0.35F),
            key("collapse", COLLAPSE_SNAP, 0.12F),
            key("collapse", COLLAPSE_SNAP_END, 0.0F),
            key("aftermath", 0.0F, 0.0F),
            key(1.0F, 0.0F)
        ));
    }

    private static void addCollapseCurves(EffectParameterSet.Builder builder) {
        builder.curve("FinalFlash", curve(
            key(0.0F, 0.0F),
            key("collapse", COLLAPSE_SNAP - TICK, 0.0F, EffectEasing.LINEAR),
            key("collapse", COLLAPSE_SNAP, 1.0F, EffectEasing.LINEAR),
            key("collapse", COLLAPSE_SNAP_END, 0.75F, EffectEasing.LINEAR),
            key("aftermath", FINAL_FLASH_DECAY, 0.0F, EffectEasing.LINEAR),
            key(1.0F, 0.0F)
        ));
        builder.curve("RimGlow", curve(
            key(0.0F, 0.0F),
            key("collapse", RIM_GLOW_START, 0.0F),
            key("collapse", COLLAPSE_SNAP, 1.0F, EffectEasing.EASE_IN),
            key("aftermath", 0.0F, 1.0F),
            key("aftermath", RIM_GLOW_END * 0.5F, 0.45F),
            key("aftermath", RIM_GLOW_END, 0.0F, EffectEasing.EASE_OUT),
            key(1.0F, 0.0F)
        ));
    }

    private static void addPlatformCurves(EffectParameterSet.Builder builder) {
        builder.curve(PLATFORM_VISIBLE, curve(
            key(0.0F, 0.0F),
            key("arrival", 0.0F, 0.0F),
            key("arrival", TICK, 1.0F, EffectEasing.EASE_OUT),
            key("aftermath", SCREEN_VISIBLE_END, 1.0F),
            key("aftermath", SCREEN_VISIBLE_END + 2.0F * TICK, 0.0F, EffectEasing.LINEAR),
            key(1.0F, 0.0F)
        ));
        builder.curve(PLATFORM_REVEAL, curve(
            key(0.0F, 0.0F),
            key("arrival", 0.0F, 0.0F),
            key("arrival", PLATFORM_REVEAL_TIME, 1.0F, EffectEasing.EASE_OUT),
            key(1.0F, 1.0F)
        ));
        builder.curve(PLATFORM_SPIN, curve(
            key(0.0F, 0.0F),
            key("arrival", 0.0F, 0.0F),
            key("collapse", ContentOrbitalLaserSettings.COLLAPSE_TICKS * TICK, 1.0F,
                EffectEasing.LINEAR),
            key(1.0F, 1.0F)
        ));
        builder.curve(PLATFORM_CHARGE, curve(
            key(0.0F, 0.0F),
            key("charge", 0.0F, 0.0F),
            key("descent", 0.0F, 1.0F, EffectEasing.EASE_IN),
            key("descent", PLATFORM_CHARGE_RELEASE, 0.0F, EffectEasing.LINEAR),
            key(1.0F, 0.0F)
        ));
        builder.curve(PLATFORM_RECOIL, curve(
            key(0.0F, 0.0F),
            key("descent", -TICK, 0.0F, EffectEasing.LINEAR),
            key("descent", 0.0F, 1.0F, EffectEasing.LINEAR),
            key("descent", PLATFORM_RECOIL_TIME, 0.0F, EffectEasing.EASE_OUT),
            key(1.0F, 0.0F)
        ));
        builder.curve(MUZZLE_FLASH, curve(
            key(0.0F, 0.0F),
            key("descent", -TICK, 0.0F, EffectEasing.LINEAR),
            key("descent", 0.0F, 1.0F, EffectEasing.LINEAR),
            key("descent", MUZZLE_FLASH_TIME, 0.0F, EffectEasing.EASE_OUT),
            key(1.0F, 0.0F)
        ));
        builder.curve(PLATFORM_EXIT, curve(
            key(0.0F, 0.0F),
            key("collapse", 0.0F, 0.0F),
            key("collapse", PLATFORM_EXIT_TIME, 1.0F, EffectEasing.EASE_IN),
            key(1.0F, 1.0F)
        ));
    }

    private static void addServerParameters(EffectParameterSet.Builder builder) {
        builder.parameter(BEAM_RADIUS_SCALE, EffectCurve.Value.scalar(1.0F));
        builder.parameter(TIP_DEPTH, EffectCurve.Value.scalar(0.0F));
    }

    private static EffectCurve.Builder curve(Key... keys) {
        EffectCurve.Builder builder = EffectCurve.scalar();
        for (Key key : keys) {
            if (key.anchor() == null) {
                builder.keyframe(key.progress(), key.value(), key.easing());
            } else {
                builder.keyframe(key.anchor(), key.offset(), key.value(), key.easing());
            }
        }
        return builder;
    }

    private static Key key(float progress, float value) {
        return new Key(progress, null, 0.0F, value, EffectEasing.EASE_IN_OUT);
    }

    private static Key key(float progress, float value, EffectEasing easing) {
        return new Key(progress, null, 0.0F, value, easing);
    }

    private static Key key(String anchor, float offset, float value) {
        return new Key(Float.NaN, anchor, offset, value, EffectEasing.EASE_IN_OUT);
    }

    private static Key key(String anchor, float offset, float value, EffectEasing easing) {
        return new Key(Float.NaN, anchor, offset, value, easing);
    }

    private record Key(
        float progress,
        String anchor,
        float offset,
        float value,
        EffectEasing easing
    ) {
    }
}
