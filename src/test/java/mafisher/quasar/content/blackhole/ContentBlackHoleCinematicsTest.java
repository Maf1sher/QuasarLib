package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectParameterSet;
import org.junit.jupiter.api.Test;

class ContentBlackHoleCinematicsTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void frameIsContinuousAtEveryPhaseBoundary() {
        assertContinuous(ContentBlackHoleCinematics.INTRO_END);
        assertContinuous(ContentBlackHoleCinematics.DESTABILISE);
        assertContinuous(ContentBlackHoleCinematics.IMPLOSION);
    }

    @Test
    void introBendsSpaceBeforeTheHorizonIgnites() {
        Frame start = frame(0.005F);

        assertTrue(start.vortexStrength() > 0.0F);
        assertEquals(0.0F, start.ringFormation(), 0.02F);
        assertTrue(start.horizonScale() < 0.2F);
        assertEquals(1.0F, start.fade(), 1.0E-4F);
        assertEquals(0.0F, frame(0.0F).fade(), 1.0E-6F);
        assertTrue(frame(0.045F).vortexStrength() > 1.5F);
    }

    @Test
    void drillStaysCoreSized() {
        assertEquals(4.0F, frame(0.045F).vortexRadiusScale(), EPSILON);
        assertEquals(4.0F, frame(0.5F).vortexRadiusScale(), EPSILON);
        assertEquals(4.0F, frame(0.950F).vortexRadiusScale(), EPSILON);
    }

    @Test
    void vortexSnapsBackAsTheDarkHorizonAppears() {
        Frame rebound = frame(ContentBlackHoleCinematics.IGNITION);
        Frame settled = frame(0.175F);

        assertTrue(rebound.vortexStrength() < 0.0F);
        assertTrue(rebound.horizonScale() > 1.0F);
        assertTrue(rebound.darkness() > 0.1F);
        assertTrue(rebound.coreFlash() < 0.3F);
        assertEquals(0.0F, rebound.ringFormation(), 0.02F);
        assertTrue(rebound.diskBrightnessScale() <= 1.0F);
        assertTrue(rebound.ringStrengthScale() <= 1.0F);
        assertEquals(1.0F, settled.horizonScale(), 1.0E-3F);
        assertEquals(1.0F, settled.diskBrightnessScale(), 1.0E-3F);
        assertEquals(1.0F, settled.ringFormation(), 1.0E-3F);
        assertEquals(0.0F, settled.vortexStrength(), 1.0E-3F);
        assertEquals(1.0F, settled.fade(), 1.0E-3F);
    }

    @Test
    void darkShockwaveExpandsThenDisappears() {
        Frame early = frame(0.080F);
        Frame late = frame(0.095F);
        Frame settled = frame(0.175F);

        assertTrue(early.shockAbsorb() > 0.0F);
        assertTrue(early.shockRadius() > 0.0F);
        assertTrue(late.shockRadius() > early.shockRadius());
        assertEquals(0.0F, settled.shockAbsorb(), 1.0E-3F);
        assertEquals(0.0F, settled.shockRadius(), 1.0E-3F);
    }

    @Test
    void ringsFormAfterTheHorizon() {
        Frame beforeRing = frame(0.080F);
        Frame forming = frame(0.125F);
        Frame steady = frame(0.175F);

        assertEquals(0.0F, beforeRing.ringFormation(), 0.02F);
        assertEquals(0.0F, beforeRing.diskBrightnessScale(), 0.02F);
        assertEquals(0.0F, beforeRing.ringStrengthScale(), 0.02F);
        assertTrue(forming.ringFormation() > 0.0F);
        assertTrue(forming.ringFormation() < 1.0F);
        assertEquals(1.0F, steady.ringFormation(), 1.0E-3F);
    }

    @Test
    void ringsDisappearWithTheHorizon() {
        Frame start = frame(0.905F);
        Frame middle = frame(0.935F);
        Frame late = frame(0.955F);
        Frame gone = frame(0.965F);

        assertEquals(1.0F, start.horizonScale(), 1.0E-3F);
        assertEquals(start.horizonScale(), start.ringFormation(), 1.0E-3F);
        assertTrue(middle.horizonScale() > 0.0F);
        assertTrue(middle.horizonScale() < 1.0F);
        assertEquals(middle.horizonScale(), middle.ringFormation(), 1.0E-3F);
        assertEquals(late.horizonScale(), late.ringFormation(), 1.0E-3F);
        assertEquals(0.0F, gone.horizonScale(), 1.0E-3F);
        assertEquals(0.0F, gone.ringFormation(), 1.0E-3F);
    }

    @Test
    void outroCollapsesIntoADarkImplosionAndRebound() {
        Frame collapse = frame(0.930F);
        Frame flash = frame(0.955F);
        Frame gone = frame(0.965F);
        Frame echo = frame(0.990F);

        assertTrue(collapse.vortexStrength() > 1.0F);
        assertTrue(collapse.shockAbsorb() > 0.0F);
        assertTrue(flash.coreFlash() > 0.1F);
        assertTrue(flash.coreFlash() < 0.3F);
        assertEquals(flash.horizonScale(), flash.ringFormation(), 0.02F);
        assertTrue(flash.darkness() > 0.4F);
        assertTrue(flash.horizonScale() < 0.2F);
        assertEquals(0.0F, gone.horizonScale(), 0.02F);
        assertEquals(0.0F, gone.diskScale(), 0.02F);
        assertTrue(gone.vortexStrength() < 0.0F);
        assertTrue(echo.shockRadius() > 0.0F);
        assertTrue(echo.shockAbsorb() > 0.0F);
        assertTrue(echo.fade() > 0.0F);
        assertEquals(0.0F, frame(1.0F).fade(), 1.0E-6F);
    }

    @Test
    void anchorsMatchTheAuthoredShowConstants() {
        EffectParameterSet parameters = ContentBlackHoleCinematics.parameters();

        assertEquals(ContentBlackHoleCinematics.INTRO_END, parameters.anchor("intro_end"), 0.0F);
        assertEquals(ContentBlackHoleCinematics.IGNITION, parameters.anchor("ignition"), 0.0F);
        assertEquals(ContentBlackHoleCinematics.RING_END, parameters.anchor("ring_end"), 0.0F);
        assertEquals(ContentBlackHoleCinematics.DESTABILISE, parameters.anchor("destabilise"), 0.0F);
        assertEquals(ContentBlackHoleCinematics.IMPLOSION, parameters.anchor("implosion"), 0.0F);
    }

    @Test
    void cinematicAgeAcceleratesThroughTheOutro() {
        float early = ContentBlackHoleCinematics.cinematicAge(100.0F, 0.5F);
        float late = ContentBlackHoleCinematics.cinematicAge(100.0F, 0.95F);

        assertEquals(100.0F, early, 1.0E-4F);
        assertTrue(late > early * 2.0F);
        assertEquals(0.0F, ContentBlackHoleCinematics.cinematicAge(0.0F, 0.95F), 1.0E-6F);
    }

    @Test
    void curvesStayCloseToTheAcceptedShow() {
        Frame ignition = frame(ContentBlackHoleCinematics.IGNITION);
        Frame collapse = frame(0.905F);
        Frame flash = frame(0.952F);

        assertEquals(1.0424F, ignition.horizonScale(), 0.02F);
        assertEquals(0.16F, ignition.coreFlash(), 0.02F);
        assertEquals(0.30F, ignition.darkness(), 0.02F);
        assertEquals(1.0F, collapse.horizonScale(), 0.02F);
        assertEquals(1.4F, collapse.vortexStrength(), 0.05F);
        assertEquals(0.20F, flash.coreFlash(), 0.03F);
        assertEquals(0.55F, flash.darkness(), 0.03F);
    }

    private static void assertContinuous(float progress) {
        Frame before = frame(progress - EPSILON);
        Frame after = frame(progress + EPSILON);

        assertEquals(before.horizonScale(), after.horizonScale(), 1.0E-3F);
        assertEquals(before.diskScale(), after.diskScale(), 1.0E-3F);
        assertEquals(before.lensWarpScale(), after.lensWarpScale(), 1.0E-3F);
        assertEquals(before.ringFormation(), after.ringFormation(), 1.0E-3F);
        assertEquals(before.diskBrightnessScale(), after.diskBrightnessScale(), 1.0E-3F);
        assertEquals(before.ringStrengthScale(), after.ringStrengthScale(), 1.0E-3F);
        assertEquals(before.turbulenceScale(), after.turbulenceScale(), 1.0E-3F);
        assertEquals(before.ageScale(), after.ageScale(), 1.0E-3F);
        assertEquals(before.vortexStrength(), after.vortexStrength(), 1.0E-3F);
        assertEquals(before.shockAbsorb(), after.shockAbsorb(), 1.0E-3F);
        assertEquals(before.darkness(), after.darkness(), 1.0E-3F);
        assertEquals(before.fade(), after.fade(), 1.0E-3F);
    }

    private static Frame frame(float progress) {
        EffectParameterSet parameters = ContentBlackHoleCinematics.parameters();
        float ring = parameters.sampleFloat("RingFormation", progress);
        return new Frame(
            parameters.sampleFloat("HorizonScale", progress),
            parameters.sampleFloat("DiskScale", progress),
            parameters.sampleFloat("LensWarpScale", progress),
            ring,
            ring,
            ring,
            parameters.sampleFloat("TurbulenceScale", progress),
            parameters.sampleFloat("AgeScale", progress),
            parameters.sampleFloat("VortexStrength", progress),
            parameters.sampleFloat("VortexRadiusScale", progress),
            parameters.sampleFloat("ShockRadius", progress),
            parameters.sampleFloat("ShockStrength", progress),
            parameters.sampleFloat("ShockAbsorb", progress),
            parameters.sampleFloat("CoreFlash", progress),
            parameters.sampleFloat("OutroJet", progress),
            parameters.sampleFloat("Darkness", progress),
            parameters.sampleFloat("Fade", progress)
        );
    }

    private record Frame(
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
