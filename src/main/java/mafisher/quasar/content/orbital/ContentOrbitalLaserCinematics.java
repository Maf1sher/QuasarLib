package mafisher.quasar.content.orbital;

import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.api.effect.EffectEasing;
import mafisher.quasar.api.effect.EffectParameterSet;

/**
 * Duration-independent curves for the orbital-laser beam show.
 *
 * <p>Every curve is anchored to {@link ContentOrbitalLaserSettings#phasePlan()}, so phase edits move
 * the visuals with the session instead of requiring hand-tuned absolute progress values. The marker
 * handoff, beam descent, impact flash, and shock ring all read the same parameter set.</p>
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

    private static EffectParameterSet buildParameters() {
        EffectParameterSet.Builder builder = EffectParameterSet.builder()
            .anchors(ContentOrbitalLaserSettings.phasePlan());
        addBeamCurves(builder);
        addMarkerCurve(builder);
        addImpactCurves(builder);
        return builder.build();
    }

    private static void addBeamCurves(EffectParameterSet.Builder builder) {
        builder.curve("Visible", curve(
            key(0.0F, 0.0F),
            key("descent", -TICK, 0.0F, EffectEasing.LINEAR),
            key("descent", 0.0F, 1.0F, EffectEasing.LINEAR),
            key("collapse", 0.0F, 1.0F),
            key("aftermath", 0.0F, 0.0F),
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
            key("aftermath", 0.0F, 0.0F),
            key(1.0F, 0.0F)
        ));
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
