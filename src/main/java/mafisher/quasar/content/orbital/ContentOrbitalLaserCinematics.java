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

    /** Returns whether the screen operation has any visible work at this progress. */
    static boolean screenVisible(float progress) {
        return PARAMETERS.sampleFloat("Visible", progress) > 0.001F;
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
