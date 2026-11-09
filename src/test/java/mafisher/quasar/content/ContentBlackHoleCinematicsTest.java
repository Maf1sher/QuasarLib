package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        ContentBlackHoleCinematics.Frame start = ContentBlackHoleCinematics.frame(0.005F);

        assertTrue(start.lensWarpScale() > 0.0F);
        assertEquals(0.0F, start.diskBrightnessScale(), 1.0E-6F);
        assertEquals(0.0F, start.ringStrengthScale(), 1.0E-6F);
        assertTrue(start.horizonScale() < 0.6F);
        assertEquals(0.0F, ContentBlackHoleCinematics.frame(0.0F).fade(), 1.0E-6F);
    }

    @Test
    void horizonSnapsAndOvershootsAtIgnition() {
        ContentBlackHoleCinematics.Frame ignition = ContentBlackHoleCinematics.frame(0.05F);
        ContentBlackHoleCinematics.Frame settled = ContentBlackHoleCinematics.frame(0.13F);

        assertTrue(ignition.horizonScale() > 1.05F);
        assertTrue(ignition.coreFlash() > 0.9F);
        assertTrue(ignition.diskBrightnessScale() > 1.5F);
        assertEquals(1.0F, settled.horizonScale(), 1.0E-4F);
        assertEquals(1.0F, settled.diskBrightnessScale(), 1.0E-4F);
        assertEquals(1.0F, settled.fade(), 1.0E-4F);
    }

    @Test
    void shockwaveExpandsThenDisappears() {
        ContentBlackHoleCinematics.Frame early = ContentBlackHoleCinematics.frame(0.050F);
        ContentBlackHoleCinematics.Frame late = ContentBlackHoleCinematics.frame(0.060F);
        ContentBlackHoleCinematics.Frame settled = ContentBlackHoleCinematics.frame(0.130F);

        assertTrue(early.shockStrength() > 0.0F);
        assertTrue(early.shockRadius() > 0.0F);
        assertTrue(late.shockRadius() > early.shockRadius());
        assertEquals(0.0F, settled.shockStrength(), 1.0E-6F);
        assertEquals(0.0F, settled.shockRadius(), 1.0E-6F);
    }

    @Test
    void outroCollapsesIntoAnImplosionAndEcho() {
        ContentBlackHoleCinematics.Frame collapse = ContentBlackHoleCinematics.frame(0.952F);
        ContentBlackHoleCinematics.Frame flash = ContentBlackHoleCinematics.frame(0.969F);
        ContentBlackHoleCinematics.Frame gone = ContentBlackHoleCinematics.frame(0.978F);
        ContentBlackHoleCinematics.Frame echo = ContentBlackHoleCinematics.frame(0.990F);

        assertTrue(collapse.horizonScale() < 0.8F);
        assertTrue(collapse.outroJet() > 0.0F);
        assertTrue(collapse.shockStrength() > 0.0F);
        assertTrue(flash.coreFlash() > 0.9F);
        assertEquals(0.0F, gone.horizonScale(), 1.0E-4F);
        assertEquals(0.0F, gone.diskScale(), 1.0E-4F);
        assertTrue(echo.shockRadius() > 0.0F);
        assertTrue(echo.shockStrength() > 0.0F);
        assertTrue(echo.fade() > 0.0F);
        assertEquals(0.0F, ContentBlackHoleCinematics.frame(1.0F).fade(), 1.0E-6F);
    }

    @Test
    void cinematicAgeAcceleratesThroughTheOutro() {
        float early = ContentBlackHoleCinematics.cinematicAge(100.0F, 0.5F);
        float late = ContentBlackHoleCinematics.cinematicAge(100.0F, 0.95F);

        assertEquals(100.0F, early, 1.0E-4F);
        assertTrue(late > early * 2.0F);
        assertEquals(0.0F, ContentBlackHoleCinematics.cinematicAge(0.0F, 0.95F), 1.0E-6F);
    }

    private static void assertContinuous(float progress) {
        ContentBlackHoleCinematics.Frame before =
            ContentBlackHoleCinematics.frame(progress - EPSILON);
        ContentBlackHoleCinematics.Frame after =
            ContentBlackHoleCinematics.frame(progress + EPSILON);

        assertEquals(before.horizonScale(), after.horizonScale(), 1.0E-3F);
        assertEquals(before.diskScale(), after.diskScale(), 1.0E-3F);
        assertEquals(before.lensWarpScale(), after.lensWarpScale(), 1.0E-3F);
        assertEquals(before.diskBrightnessScale(), after.diskBrightnessScale(), 1.0E-3F);
        assertEquals(before.ringStrengthScale(), after.ringStrengthScale(), 1.0E-3F);
        assertEquals(before.turbulenceScale(), after.turbulenceScale(), 1.0E-3F);
        assertEquals(before.ageScale(), after.ageScale(), 1.0E-3F);
        assertEquals(before.fade(), after.fade(), 1.0E-3F);
    }
}
