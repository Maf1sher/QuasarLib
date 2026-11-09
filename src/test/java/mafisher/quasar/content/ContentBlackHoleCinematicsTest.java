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

        assertTrue(start.vortexStrength() > 0.0F);
        assertEquals(0.0F, start.diskBrightnessScale(), 1.0E-6F);
        assertEquals(0.0F, start.ringStrengthScale(), 1.0E-6F);
        assertEquals(0.0F, start.ringFormation(), 1.0E-6F);
        assertTrue(start.horizonScale() < 0.2F);
        assertEquals(1.0F, start.fade(), 1.0E-4F);
        assertEquals(0.0F, ContentBlackHoleCinematics.frame(0.0F).fade(), 1.0E-6F);
        assertTrue(ContentBlackHoleCinematics.frame(0.045F).vortexStrength() > 1.5F);
    }

    @Test
    void vortexSnapsBackAsTheDarkHorizonAppears() {
        ContentBlackHoleCinematics.Frame rebound =
            ContentBlackHoleCinematics.frame(ContentBlackHoleCinematics.IGNITION);
        ContentBlackHoleCinematics.Frame settled = ContentBlackHoleCinematics.frame(0.175F);

        assertTrue(rebound.vortexStrength() < 0.0F);
        assertTrue(rebound.horizonScale() > 1.0F);
        assertTrue(rebound.darkness() > 0.1F);
        assertTrue(rebound.coreFlash() < 0.3F);
        assertEquals(0.0F, rebound.ringFormation(), 1.0E-6F);
        assertTrue(rebound.diskBrightnessScale() <= 1.0F);
        assertTrue(rebound.ringStrengthScale() <= 1.0F);
        assertEquals(1.0F, settled.horizonScale(), 1.0E-4F);
        assertEquals(1.0F, settled.diskBrightnessScale(), 1.0E-4F);
        assertEquals(1.0F, settled.ringFormation(), 1.0E-4F);
        assertEquals(0.0F, settled.vortexStrength(), 1.0E-4F);
        assertEquals(1.0F, settled.fade(), 1.0E-4F);
    }

    @Test
    void darkShockwaveExpandsThenDisappears() {
        ContentBlackHoleCinematics.Frame early = ContentBlackHoleCinematics.frame(0.080F);
        ContentBlackHoleCinematics.Frame late = ContentBlackHoleCinematics.frame(0.095F);
        ContentBlackHoleCinematics.Frame settled = ContentBlackHoleCinematics.frame(0.175F);

        assertTrue(early.shockAbsorb() > 0.0F);
        assertTrue(early.shockRadius() > 0.0F);
        assertTrue(late.shockRadius() > early.shockRadius());
        assertEquals(0.0F, settled.shockAbsorb(), 1.0E-6F);
        assertEquals(0.0F, settled.shockRadius(), 1.0E-6F);
    }

    @Test
    void ringsFormAfterTheHorizonAndDrainBeforeItCollapses() {
        ContentBlackHoleCinematics.Frame beforeRing = ContentBlackHoleCinematics.frame(0.080F);
        ContentBlackHoleCinematics.Frame forming = ContentBlackHoleCinematics.frame(0.125F);
        ContentBlackHoleCinematics.Frame steady = ContentBlackHoleCinematics.frame(0.175F);
        ContentBlackHoleCinematics.Frame draining = ContentBlackHoleCinematics.frame(0.880F);
        ContentBlackHoleCinematics.Frame drained = ContentBlackHoleCinematics.frame(0.905F);

        assertEquals(0.0F, beforeRing.ringFormation(), 1.0E-6F);
        assertEquals(0.0F, beforeRing.diskBrightnessScale(), 1.0E-6F);
        assertEquals(0.0F, beforeRing.ringStrengthScale(), 1.0E-6F);
        assertTrue(forming.ringFormation() > 0.0F);
        assertTrue(forming.ringFormation() < 1.0F);
        assertEquals(1.0F, steady.ringFormation(), 1.0E-4F);
        assertTrue(draining.ringFormation() > 0.0F);
        assertTrue(draining.ringFormation() < 1.0F);
        assertEquals(1.0F, draining.horizonScale(), 1.0E-4F);
        assertEquals(0.0F, drained.ringFormation(), 1.0E-6F);
        assertEquals(1.0F, drained.horizonScale(), 1.0E-4F);
    }

    @Test
    void outroCollapsesIntoADarkImplosionAndRebound() {
        ContentBlackHoleCinematics.Frame collapse = ContentBlackHoleCinematics.frame(0.930F);
        ContentBlackHoleCinematics.Frame flash = ContentBlackHoleCinematics.frame(0.955F);
        ContentBlackHoleCinematics.Frame gone = ContentBlackHoleCinematics.frame(0.965F);
        ContentBlackHoleCinematics.Frame echo = ContentBlackHoleCinematics.frame(0.990F);

        assertTrue(collapse.vortexStrength() > 1.0F);
        assertTrue(collapse.shockAbsorb() > 0.0F);
        assertTrue(flash.coreFlash() > 0.1F);
        assertTrue(flash.coreFlash() < 0.3F);
        assertEquals(0.0F, flash.ringFormation(), 1.0E-6F);
        assertTrue(flash.darkness() > 0.4F);
        assertTrue(flash.horizonScale() < 0.2F);
        assertEquals(0.0F, gone.horizonScale(), 1.0E-4F);
        assertEquals(0.0F, gone.diskScale(), 1.0E-4F);
        assertTrue(gone.vortexStrength() < 0.0F);
        assertTrue(echo.shockRadius() > 0.0F);
        assertTrue(echo.shockAbsorb() > 0.0F);
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
}
