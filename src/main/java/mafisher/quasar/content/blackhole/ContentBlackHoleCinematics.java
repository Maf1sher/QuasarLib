package mafisher.quasar.content.blackhole;

import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.api.effect.EffectEasing;
import mafisher.quasar.api.effect.EffectParameterSet;

/**
 * Duration-independent curve definition for the content black hole.
 *
 * <p>The show has three acts: a core-sized space drill that snaps into a dark horizon, an
 * accretion ring that only forms after the horizon exists, and a reverse drill that drains the
 * ring before the horizon collapses. Every channel is a named curve sampled from normalized
 * progress, so the same definition works at every authored duration.</p>
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

    static final EffectParameterSet PARAMETERS = buildParameters();

    private ContentBlackHoleCinematics() {
    }

    static EffectParameterSet parameters() {
        return PARAMETERS;
    }

    /** Multiplies the authored disk spin and procedural time by the cinematic age curve. */
    static float cinematicAge(float ageTicks, float progress) {
        return Math.max(0.0F, ageTicks) * PARAMETERS.sampleFloat("AgeScale", progress);
    }

    private static EffectParameterSet buildParameters() {
        return EffectParameterSet.builder()
            .anchor("intro_end", INTRO_END)
            .anchor("vortex_end", VORTEX_END)
            .anchor("vortex_hold_end", VORTEX_HOLD_END)
            .anchor("snap_end", SNAP_END)
            .anchor("ignition", IGNITION)
            .anchor("horizon_end", HORIZON_END)
            .anchor("ring_start", RING_START)
            .anchor("ring_end", RING_END)
            .anchor("destabilise", DESTABILISE)
            .anchor("collapse", COLLAPSE)
            .anchor("implosion", IMPLOSION)
            .anchor("echo", ECHO)
            .curve("HorizonScale", curve(
                key(0.000F, 0.0500F), key(0.060F, 0.0500F), key(0.066F, 0.7852F),
                key(0.070F, 1.0424F), key(0.072F, 1.0589F), key(0.075F, 1.0600F),
                key(0.085F, 1.0000F), key(0.175F, 1.0000F), key(0.860F, 1.0000F),
                key(0.905F, 1.0000F), key(0.920F, 0.9747F), key(0.940F, 0.2988F),
                key(0.945F, 0.2000F), key(0.950F, 0.1992F), key(0.955F, 0.1750F),
                key(0.960F, 0.0799F), key(0.965F, 0.0000F), key(1.000F, 0.0000F)))
            .curve("DiskScale", curve(
                key(0.000F, 0.5000F), key(0.090F, 0.5000F), key(0.100F, 0.5786F),
                key(0.125F, 0.9375F), key(0.130F, 0.9695F), key(0.160F, 1.0000F),
                key(0.860F, 1.0000F), key(0.905F, 1.0000F), key(0.920F, 0.9753F),
                key(0.940F, 0.3163F), key(0.945F, 0.2200F), key(0.950F, 0.2192F),
                key(0.955F, 0.1925F), key(0.960F, 0.0879F), key(0.965F, 0.0000F),
                key(1.000F, 0.0000F)))
            .curve("LensWarpScale", curve(
                key(0.000F, 0.7000F), key(0.035F, 1.0000F), key(0.945F, 1.0000F),
                key(0.995F, 0.0000F), key(1.000F, 0.0000F)))
            .curve("RingFormation", curve(
                key(0.000F, 0.0000F), key(0.090F, 0.0000F), key(0.100F, 0.0554F),
                key(0.125F, 0.5000F), key(0.130F, 0.6064F), key(0.160F, 1.0000F),
                key(0.860F, 1.0000F), key(0.905F, 1.0000F), key(0.920F, 0.9747F),
                key(0.940F, 0.2988F), key(0.945F, 0.2000F), key(0.950F, 0.1992F),
                key(0.955F, 0.1750F), key(0.960F, 0.0799F), key(0.965F, 0.0000F),
                key(1.000F, 0.0000F)))
            .curve("TurbulenceScale", curve(
                key(0.000F, 1.5000F), key(0.020F, 1.5000F), key(0.045F, 1.3840F),
                key(0.080F, 1.0781F), key(0.100F, 1.0000F), key(0.860F, 1.0000F),
                key(0.880F, 1.4170F, EffectEasing.LINEAR), key(0.905F, 2.0000F, EffectEasing.LINEAR),
                key(0.920F, 2.0317F, EffectEasing.LINEAR), key(0.940F, 2.8766F, EffectEasing.LINEAR),
                key(0.945F, 3.0000F, EffectEasing.LINEAR), key(1.000F, 3.0000F, EffectEasing.LINEAR)))
            .curve("AgeScale", curve(
                key(0.000F, 0.2000F), key(0.010F, 0.2000F), key(0.020F, 0.3257F),
                key(0.045F, 0.9000F), key(0.080F, 1.0000F), key(0.860F, 1.0000F),
                key(0.880F, 1.5838F, EffectEasing.LINEAR), key(0.905F, 2.4000F, EffectEasing.LINEAR),
                key(0.920F, 2.4380F, EffectEasing.LINEAR), key(0.940F, 3.4519F, EffectEasing.LINEAR),
                key(0.945F, 3.6000F, EffectEasing.LINEAR), key(0.950F, 3.9125F, EffectEasing.LINEAR),
                key(0.955F, 4.6000F, EffectEasing.LINEAR), key(0.965F, 5.6000F, EffectEasing.LINEAR),
                key(1.000F, 5.6000F, EffectEasing.LINEAR)))
            .curve("VortexStrength", curve(
                key(0.000F, 0.0000F), key(0.002F, 0.0085F), key(0.004F, 0.0332F),
                key(0.010F, 0.1917F), key(0.020F, 0.6612F), key(0.035F, 1.5388F),
                key(0.045F, 2.0083F), key(0.055F, 2.2000F), key(0.060F, 2.2000F),
                key(0.066F, -0.1625F), key(0.070F, -0.4989F), key(0.072F, -0.5000F),
                key(0.074F, -0.4681F), key(0.075F, -0.4324F), key(0.080F, -0.1650F),
                key(0.085F, 0.0000F), key(0.175F, 0.0000F), key(0.860F, 0.0000F),
                key(0.880F, 0.5838F), key(0.905F, 1.4000F), key(0.920F, 1.6531F),
                key(0.940F, 2.1656F), key(0.945F, 2.2000F), key(0.950F, 1.7469F),
                key(0.955F, 0.7500F), key(0.960F, -0.2469F), key(0.965F, -0.7000F),
                key(0.968F, -0.6804F), key(0.975F, -0.5185F), key(0.980F, -0.3500F),
                key(0.990F, -0.0519F), key(0.995F, 0.0000F), key(1.000F, 0.0000F)))
            .curve("VortexRadiusScale", curve(
                key(0.000F, 4.0000F, EffectEasing.LINEAR),
                key(1.000F, 4.0000F, EffectEasing.LINEAR)))
            .curve("ShockRadius", curve(
                key(0.000F, 0.0000F), key(0.070F, 0.0000F), key(0.080F, 1.6493F),
                key(0.085F, 3.1946F), key(0.090F, 4.7485F), key(0.100F, 7.0000F),
                key(0.125F, 8.0000F), key(0.130F, 8.0000F), key(0.175F, 0.0000F),
                key(0.860F, 0.0000F), key(0.905F, 0.0000F), key(0.920F, 7.7466F),
                key(0.940F, 0.9876F), key(0.945F, 0.0000F), key(0.950F, 0.5461F),
                key(0.955F, 1.9147F), key(0.960F, 3.6304F), key(0.965F, 5.2622F),
                key(0.968F, 6.0783F), key(0.975F, 7.3550F), key(0.980F, 7.7829F),
                key(0.990F, 7.9947F), key(0.995F, 8.0000F), key(1.000F, 0.0000F)))
            .curve("ShockStrength", curve(
                key(0.000F, 0.0000F), key(0.070F, 0.0000F), key(0.072F, 0.0673F),
                key(0.074F, 0.0790F), key(0.075F, 0.0784F), key(0.080F, 0.0741F),
                key(0.085F, 0.0675F), key(0.090F, 0.0593F), key(0.100F, 0.0400F),
                key(0.125F, 0.0016F), key(0.130F, 0.0000F), key(0.905F, 0.0000F),
                key(0.920F, 0.0600F, EffectEasing.LINEAR),
                key(0.940F, 0.0600F, EffectEasing.LINEAR),
                key(0.945F, 0.0000F), key(0.950F, 0.0586F), key(0.952F, 0.0573F),
                key(0.955F, 0.0548F), key(0.960F, 0.0490F), key(0.965F, 0.0420F),
                key(0.968F, 0.0373F), key(0.975F, 0.0259F), key(0.980F, 0.0180F),
                key(0.990F, 0.0052F), key(0.995F, 0.0014F), key(1.000F, 0.0000F)))
            .curve("ShockAbsorb", curve(
                key(0.000F, 0.0000F), key(0.070F, 0.5000F), key(0.072F, 0.8410F),
                key(0.074F, 0.9873F), key(0.075F, 0.9803F), key(0.080F, 0.9259F),
                key(0.085F, 0.8438F), key(0.090F, 0.7407F), key(0.100F, 0.5000F),
                key(0.125F, 0.0197F), key(0.130F, 0.0000F), key(0.905F, 0.0000F),
                key(0.920F, 1.0000F, EffectEasing.LINEAR),
                key(0.940F, 1.0000F, EffectEasing.LINEAR),
                key(0.945F, 0.0000F), key(0.950F, 0.8790F), key(0.952F, 0.8600F),
                key(0.955F, 0.8216F), key(0.960F, 0.7357F), key(0.965F, 0.6295F),
                key(0.968F, 0.5595F), key(0.975F, 0.3888F), key(0.980F, 0.2705F),
                key(0.990F, 0.0784F), key(0.995F, 0.0210F), key(1.000F, 0.0000F)))
            .curve("CoreFlash", curve(
                key(0.000F, 0.0000F), key(0.060F, 0.0589F), key(0.066F, 0.1363F),
                key(0.070F, 0.1600F), key(0.072F, 0.1537F), key(0.080F, 0.0589F),
                key(0.090F, 0.0029F), key(0.100F, 0.0000F), key(0.175F, 0.0000F),
                key(0.945F, 0.0930F), key(0.950F, 0.1879F), key(0.952F, 0.2000F),
                key(0.955F, 0.1738F), key(0.960F, 0.0736F), key(0.965F, 0.0143F),
                key(0.968F, 0.0037F), key(0.975F, 0.0000F), key(1.000F, 0.0000F)))
            .curve("OutroJet", curve(
                key(0.000F, 0.0000F), key(0.905F, 0.0000F), key(0.920F, 0.3403F),
                key(0.940F, 0.5000F), key(0.945F, 0.5000F), key(0.950F, 0.4219F),
                key(0.955F, 0.2500F), key(0.960F, 0.0781F), key(0.965F, 0.0000F),
                key(1.000F, 0.0000F)))
            .curve("Darkness", curve(
                key(0.000F, 0.0000F), key(0.060F, 0.1104F), key(0.066F, 0.2336F),
                key(0.070F, 0.2918F), key(0.072F, 0.3000F), key(0.074F, 0.2918F),
                key(0.080F, 0.1924F), key(0.090F, 0.0316F), key(0.100F, 0.0013F),
                key(0.175F, 0.0000F), key(0.945F, 0.4623F), key(0.950F, 0.5500F),
                key(0.952F, 0.5349F), key(0.955F, 0.4623F), key(0.960F, 0.2746F),
                key(0.965F, 0.1153F), key(0.968F, 0.0580F), key(0.975F, 0.0072F),
                key(1.000F, 0.0000F)))
            .curve("Fade", curve(
                key(0.000F, 0.0000F), key(0.004F, 1.0000F), key(0.990F, 1.0000F),
                key(1.000F, 0.0000F)))
            .build();
    }

    private static EffectCurve curve(Key... keys) {
        EffectCurve.Builder builder = EffectCurve.scalar();
        for (Key key : keys) {
            builder.keyframe(key.time(), key.value(), key.easing());
        }
        return builder.build();
    }

    private static Key key(float time, float value) {
        return new Key(time, value, EffectEasing.EASE_IN_OUT);
    }

    private static Key key(float time, float value, EffectEasing easing) {
        return new Key(time, value, easing);
    }

    private record Key(float time, float value, EffectEasing easing) {
    }
}
