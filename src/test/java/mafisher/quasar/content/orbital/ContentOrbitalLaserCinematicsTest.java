package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectParameterSet;
import mafisher.quasar.api.effect.EffectParameterState;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserCinematicsTest {
    private static final EffectParameterSet PARAMETERS = ContentOrbitalLaserCinematics.parameters();
    private static final int TARGET_TICK = phaseStart(ContentOrbitalLaserSettings.PHASE_TARGET);
    private static final int ARRIVAL_TICK = phaseStart(ContentOrbitalLaserSettings.PHASE_ARRIVAL);
    private static final int CHARGE_TICK = phaseStart(ContentOrbitalLaserSettings.PHASE_CHARGE);
    private static final int DESCENT_TICK = phaseStart(ContentOrbitalLaserSettings.PHASE_DESCENT);
    private static final int IMPACT_TICK = phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT);
    private static final int SUSTAIN_TICK = phaseStart(ContentOrbitalLaserSettings.PHASE_SUSTAIN);
    private static final int COLLAPSE_TICK = phaseStart(ContentOrbitalLaserSettings.PHASE_COLLAPSE);
    private static final int AFTERMATH_TICK = phaseStart(ContentOrbitalLaserSettings.PHASE_AFTERMATH);
    private static final float ARRIVAL = progress(ARRIVAL_TICK);
    private static final float CHARGE = progress(CHARGE_TICK);
    private static final float DESCENT = progress(DESCENT_TICK);
    private static final float IMPACT = progress(IMPACT_TICK);
    private static final float SUSTAIN = progress(SUSTAIN_TICK);
    private static final float COLLAPSE = progress(COLLAPSE_TICK);
    private static final float AFTERMATH = progress(AFTERMATH_TICK);

    @Test
    void targetPhaseShowsOnlyTheMarker() {
        assertFalse(ContentOrbitalLaserCinematics.screenVisible(0.0F));
        assertFalse(ContentOrbitalLaserCinematics.platformVisible(0.0F));
        assertEquals(1.0F, sample("BeamTip", 0.0F), 1.0E-6F);
        assertEquals(0.0F, sample("BeamStrength", 0.0F), 1.0E-6F);
        assertEquals(1.0F, sample("MarkerAlpha", 0.0F), 1.0E-6F);
        assertEquals(0.0F, sample("ImpactFlash", 0.0F), 1.0E-6F);
    }

    @Test
    void arrivalAndChargeKeepTheBeamOff() {
        for (int tick = 0; tick < DESCENT_TICK; tick++) {
            float progress = progress(tick);
            assertFalse(ContentOrbitalLaserCinematics.beamVisible(progress),
                "beam visible at tick " + tick);
            assertEquals(0.0F, sample("BeamStrength", progress), 1.0E-6F,
                "beam strength at tick " + tick);
            assertEquals(0.0F, sample("BeamWidth", progress), 1.0E-6F,
                "beam width at tick " + tick);
        }
        assertEquals(1.0F, sample("MarkerAlpha", ARRIVAL), 1.0E-6F);
        assertEquals(1.0F, sample("MarkerAlpha", CHARGE), 1.0E-6F);
        assertFalse(ContentOrbitalLaserCinematics.platformVisible(
            progress(TARGET_TICK)));
        assertTrue(ContentOrbitalLaserCinematics.platformVisible(
            progress(ARRIVAL_TICK + 1)));
        assertTrue(ContentOrbitalLaserCinematics.platformVisible(CHARGE));
        assertTrue(ContentOrbitalLaserCinematics.screenVisible(
            progress(ARRIVAL_TICK + 1)));
        assertEquals(1.0F, sample(ContentOrbitalLaserCinematics.PLATFORM_VISIBLE, CHARGE),
            1.0E-6F);
    }

    @Test
    void platformCurvesRevealChargeFireAndExitInOrder() {
        String visible = ContentOrbitalLaserCinematics.PLATFORM_VISIBLE;
        String reveal = ContentOrbitalLaserCinematics.PLATFORM_REVEAL;
        String spin = ContentOrbitalLaserCinematics.PLATFORM_SPIN;
        String charge = ContentOrbitalLaserCinematics.PLATFORM_CHARGE;
        String recoil = ContentOrbitalLaserCinematics.PLATFORM_RECOIL;
        String muzzleFlash = ContentOrbitalLaserCinematics.MUZZLE_FLASH;
        String exit = ContentOrbitalLaserCinematics.PLATFORM_EXIT;

        assertEquals(0.0F, sample(reveal, ARRIVAL), 1.0E-6F);
        assertEquals(1.0F, sample(reveal, CHARGE), 1.0E-6F);
        assertEquals(0.0F, sample(spin, ARRIVAL), 1.0E-6F);
        assertEquals(1.0F, sample(spin,
            progress(COLLAPSE_TICK + ContentOrbitalLaserSettings.COLLAPSE_TICKS)), 1.0E-6F);
        float previousSpin = sample(spin, ARRIVAL);
        for (float progress = ARRIVAL; progress <= COLLAPSE; progress += 0.002F) {
            float value = sample(spin, progress);
            assertTrue(value >= previousSpin - 1.0E-6F, "platform spin reversed at " + progress);
            previousSpin = value;
        }

        assertEquals(0.0F, sample(charge, ARRIVAL), 1.0E-6F);
        assertEquals(1.0F, sample(charge, DESCENT), 1.0E-6F);
        assertEquals(0.0F, sample(charge, progress(DESCENT_TICK + 4)), 1.0E-5F);

        assertEquals(0.0F, sample(recoil, CHARGE), 1.0E-6F);
        assertEquals(1.0F, sample(recoil, DESCENT), 1.0E-6F);
        assertEquals(0.0F, sample(recoil, progress(DESCENT_TICK + 8)), 1.0E-5F);
        assertEquals(1.0F, sample(muzzleFlash, DESCENT), 1.0E-6F);
        assertEquals(0.0F, sample(muzzleFlash, progress(DESCENT_TICK + 6)), 1.0E-5F);

        assertEquals(0.0F, sample(exit, COLLAPSE), 1.0E-6F);
        assertEquals(0.0F,
            sample(exit, ContentOrbitalLaserCinematics.collapseSnapEndProgress()), 1.0E-6F);
        assertEquals(1.0F, sample(visible, AFTERMATH), 1.0E-6F);
    }

    @Test
    void platformRevealCompletesInsideTheArrivalAndHolds() {
        String reveal = ContentOrbitalLaserCinematics.PLATFORM_REVEAL;
        float completed = progress(
            ARRIVAL_TICK + ContentOrbitalLaserSettings.ARRIVAL_TICKS / 2
        );

        assertEquals(0.0F, sample(reveal, ARRIVAL), 1.0E-6F);
        assertEquals(1.0F, sample(reveal, completed), 1.0E-6F);
        assertEquals(1.0F, sample(reveal, AFTERMATH), 1.0E-6F);
        assertEquals(1.0F, sample(reveal, 1.0F), 1.0E-6F);

        float previous = 0.0F;
        for (float progress = ARRIVAL; progress <= completed; progress += 0.002F) {
            float value = sample(reveal, progress);
            assertTrue(value >= previous - 1.0E-6F, "reveal reversed at " + progress);
            previous = value;
        }
    }

    @Test
    void platformChargePeaksAtTheShotAndReleasesWithTheBarrel() {
        assertEquals(1.0F,
            ContentOrbitalLaserCinematics.platformCharge(DESCENT), 1.0E-6F);
        assertEquals(0.0F,
            ContentOrbitalLaserCinematics.platformCharge(ARRIVAL), 1.0E-6F);
        assertEquals(0.0F,
            ContentOrbitalLaserCinematics.platformCharge(progress(DESCENT_TICK + 4)),
            1.0E-5F);
        assertEquals(ContentOrbitalLaserSettings.BEAM_TOP_HEIGHT,
            ContentOrbitalLaserPlatformSettings.muzzleHeight(
                ContentOrbitalLaserCinematics.platformCharge(ARRIVAL)), 0.0F);
        assertTrue(ContentOrbitalLaserPlatformSettings.muzzleHeight(
            ContentOrbitalLaserCinematics.platformCharge(DESCENT))
            < ContentOrbitalLaserSettings.BEAM_TOP_HEIGHT);

        float previous = sample(ContentOrbitalLaserCinematics.PLATFORM_CHARGE, CHARGE);
        for (float progress = CHARGE; progress <= DESCENT; progress += 0.002F) {
            float value = sample(ContentOrbitalLaserCinematics.PLATFORM_CHARGE, progress);
            assertTrue(value >= previous - 1.0E-6F, "charge fell before the shot");
            previous = value;
        }
    }

    @Test
    void platformDoesNotExitWhileTheBeamHasStrength() {
        String exit = ContentOrbitalLaserCinematics.PLATFORM_EXIT;

        for (int tick = 0; tick <= ContentOrbitalLaserSettings.TOTAL_TICKS; tick++) {
            float at = progress(tick);
            if (sample("BeamStrength", at) > 0.001F) {
                assertEquals(0.0F, sample(exit, at), 1.0E-6F,
                    "platform exit at tick " + tick);
            }
        }
    }

    @Test
    void platformExitStartsAfterTheBeamSnapsAndCompletesInTheAftermath() {
        String exit = ContentOrbitalLaserCinematics.PLATFORM_EXIT;
        float beamGone = ContentOrbitalLaserCinematics.collapseSnapEndProgress();
        int beamGoneTick = Math.round(beamGone * ContentOrbitalLaserSettings.TOTAL_TICKS);

        assertEquals(0.0F, sample(exit, progress(COLLAPSE_TICK - 1)), 1.0E-6F);
        assertEquals(0.0F, sample(exit, COLLAPSE), 1.0E-6F);
        assertEquals(0.0F, sample(exit, beamGone), 1.0E-6F);
        assertTrue(ContentOrbitalLaserCinematics.platformVisible(beamGone));
        assertTrue(ContentOrbitalLaserCinematics.screenVisible(beamGone));

        int firstExitTick = -1;
        int completedTick = -1;
        for (int tick = 0; tick <= ContentOrbitalLaserSettings.TOTAL_TICKS; tick++) {
            float value = sample(exit, progress(tick));
            if (firstExitTick < 0 && value > 0.001F) {
                firstExitTick = tick;
            }
            if (value >= 1.0F - 1.0E-6F) {
                completedTick = tick;
                break;
            }
        }

        assertTrue(firstExitTick > beamGoneTick,
            "platform exit started at tick " + firstExitTick
                + " before the beam was gone at tick " + beamGoneTick);
        assertTrue(firstExitTick >= beamGoneTick + 2,
            "platform exit needs a short beat after the beam, started at tick "
                + firstExitTick);
        assertTrue(completedTick > firstExitTick, "platform exit did not complete");
        assertTrue(completedTick < ContentOrbitalLaserSettings.TOTAL_TICKS,
            "platform exit completed at the end of the effect");
        assertTrue(ContentOrbitalLaserCinematics.platformVisible(progress(completedTick)),
            "platform visibility ended before the exit completed");
        assertTrue(ContentOrbitalLaserCinematics.screenVisible(progress(completedTick)),
            "screen operation stopped before the platform exit completed");

        float previous = 0.0F;
        for (float at = beamGone; at <= progress(completedTick); at += 0.002F) {
            float value = sample(exit, at);
            assertTrue(value >= previous - 1.0E-6F, "exit reversed at " + at);
            previous = value;
        }
    }

    @Test
    void descentMovesTheTipAndKeepsTheMarkerUntilLate() {
        assertEquals(1.0F, sample("BeamStrength", DESCENT), 1.0E-6F);
        assertEquals(1.0F, sample("BeamTip", DESCENT), 1.0E-6F);
        assertTrue(ContentOrbitalLaserCinematics.screenVisible(DESCENT));
        assertTrue(sample("MarkerAlpha", DESCENT + 0.02F) >= 0.0F);
        assertTrue(sample("MarkerAlpha", (DESCENT + IMPACT) * 0.5F) > 0.0F);
    }

    @Test
    void beamTipDescendsMonotonicallyToTheGround() {
        float previous = 1.0F;
        for (float progress = DESCENT; progress <= IMPACT; progress += 0.002F) {
            float tip = sample("BeamTip", progress);
            assertTrue(tip <= previous + 1.0E-6F, "tip must not rise at " + progress);
            assertTrue(tip >= 0.0F && tip <= 1.0F);
            previous = tip;
        }
        assertEquals(0.0F, sample("BeamTip", IMPACT), 1.0E-6F);
    }

    @Test
    void markerHandoffCompletesAtImpact() {
        float previous = 1.0F;
        for (float progress = DESCENT; progress <= IMPACT; progress += 0.002F) {
            float alpha = sample("MarkerAlpha", progress);
            assertTrue(alpha <= previous + 1.0E-6F);
            previous = alpha;
        }
        assertEquals(0.0F, sample("MarkerAlpha", IMPACT), 1.0E-6F);
    }

    @Test
    void impactStartsTheFlashAndShockRing() {
        assertEquals(1.0F, sample("ImpactFlash", IMPACT), 1.0E-6F);
        assertEquals(1.0F, sample("ShockStrength", IMPACT), 1.0E-6F);
        assertTrue(sample("ShockRadiusScale", IMPACT) > 0.0F);
        assertEquals(0.0F, sample("ImpactFlash", SUSTAIN + 0.1F), 1.0E-6F);
        assertEquals(0.0F, sample("ShockStrength", SUSTAIN + 0.1F), 1.0E-6F);
    }

    @Test
    void shockRingExpandsThenFades() {
        float expanded = progress(IMPACT_TICK + 16);
        float previous = sample("ShockRadiusScale", IMPACT);
        for (float progress = IMPACT; progress <= expanded; progress += 0.002F) {
            float radius = sample("ShockRadiusScale", progress);
            assertTrue(radius >= previous - 1.0E-6F);
            previous = radius;
        }
        assertEquals(1.0F, sample("ShockRadiusScale", expanded), 1.0E-6F);
        assertEquals(0.0F, sample("ShockRadiusScale", AFTERMATH), 1.0E-6F);
    }

    @Test
    void beamNarrowsMonotonicallyThroughTheCollapse() {
        assertEquals(1.0F, sample("BeamWidth", COLLAPSE), 1.0E-6F);
        assertTrue(sample("BeamWidth", ContentOrbitalLaserCinematics.collapseSnapProgress())
            < 0.2F);

        float previous = 1.0F;
        for (float progress = COLLAPSE; progress <= AFTERMATH; progress += 0.002F) {
            float width = sample("BeamWidth", progress);
            assertTrue(width <= previous + 1.0E-6F, "beam widened at " + progress);
            assertTrue(width >= 0.0F && width <= 1.0F);
            previous = width;
        }
        assertTrue(sample("BeamWidth", AFTERMATH) < 0.05F);
    }

    @Test
    void beamHoldsItsStrengthThroughSustainThenSnapsOff() {
        float snap = ContentOrbitalLaserCinematics.collapseSnapProgress();
        float snapped = ContentOrbitalLaserCinematics.collapseSnapEndProgress();

        assertEquals(1.0F, sample("BeamStrength", COLLAPSE), 1.0E-6F);
        assertEquals(1.0F, sample("BeamStrength", snap), 1.0E-6F);
        assertEquals(0.0F, sample("BeamStrength", snapped), 1.0E-6F);
        assertTrue(sample("BeamStrength", (COLLAPSE + snap) * 0.5F) > 0.99F);

        float previous = 1.0F;
        for (float progress = COLLAPSE; progress <= AFTERMATH; progress += 0.002F) {
            float strength = sample("BeamStrength", progress);
            assertTrue(strength <= previous + 1.0E-6F, "beam strength rose at " + progress);
            previous = strength;
        }
    }

    @Test
    void finalFlashPeaksAtTheSnapAndFadesIntoTheAftermath() {
        float snap = ContentOrbitalLaserCinematics.collapseSnapProgress();
        float snapped = ContentOrbitalLaserCinematics.collapseSnapEndProgress();

        assertEquals(0.0F, sample("FinalFlash", snap - 0.01F), 1.0E-6F);
        assertEquals(1.0F, sample("FinalFlash", snap), 1.0E-6F);
        assertTrue(sample("FinalFlash", snapped) > 0.0F);
        assertEquals(0.0F, sample("FinalFlash", AFTERMATH + 0.03F), 1.0E-6F);
    }

    @Test
    void rimGlowOutlivesTheBeamAndFadesThroughTheAftermath() {
        assertEquals(1.0F, sample("RimGlow", AFTERMATH), 1.0E-6F);
        assertTrue(ContentOrbitalLaserCinematics.screenVisible(AFTERMATH));

        float previous = sample("RimGlow", AFTERMATH);
        for (float progress = AFTERMATH; progress <= 1.0F; progress += 0.002F) {
            float glow = sample("RimGlow", progress);
            assertTrue(glow <= previous + 1.0E-6F, "rim glow rose at " + progress);
            previous = glow;
        }
        assertEquals(0.0F, sample("RimGlow", 1.0F), 1.0E-6F);
        assertEquals(0.0F, sample("ContactGlow", AFTERMATH), 1.0E-6F);
    }

    @Test
    void screenOperationStopsOnlyNearTheEndOfTheAftermath() {
        assertTrue(ContentOrbitalLaserCinematics.screenVisible(AFTERMATH));
        assertFalse(ContentOrbitalLaserCinematics.screenVisible(
            progress(ContentOrbitalLaserSettings.TOTAL_TICKS)
        ));
    }

    @Test
    void serverOverridesDriveTheEffectiveRadiusAndFrontDepth() {
        EffectParameterState state = EffectParameterState.builder()
            .putFloat(ContentOrbitalLaserCinematics.BEAM_RADIUS_SCALE, 0.5F)
            .putFloat(ContentOrbitalLaserCinematics.TIP_DEPTH, 24.0F)
            .build();

        assertEquals(0.5F,
            ContentOrbitalLaserCinematics.beamRadiusScale(0.5F, state), 1.0E-6F);
        assertEquals(24.0F,
            ContentOrbitalLaserCinematics.tipDepth(0.5F, state), 1.0E-6F);
        assertEquals(1.0F,
            ContentOrbitalLaserCinematics.beamRadiusScale(0.5F, EffectParameterState.EMPTY),
            1.0E-6F);
        assertEquals(0.0F,
            ContentOrbitalLaserCinematics.tipDepth(0.5F, EffectParameterState.EMPTY), 1.0E-6F);
    }

    @Test
    void everySampledValueStaysFiniteAndBounded() {
        for (float progress = 0.0F; progress <= 1.0F; progress += 0.01F) {
            for (String name : PARAMETERS.parameters().stream()
                .map(parameter -> parameter.name()).toList()) {
                float value = PARAMETERS.sampleFloat(name, progress);
                assertTrue(Float.isFinite(value), name + " is not finite at " + progress);
                assertTrue(value >= 0.0F && value <= 1.0F,
                    name + " is out of range at " + progress + ": " + value);
            }
        }
    }

    private static float sample(String name, float progress) {
        return PARAMETERS.sampleFloat(name, progress);
    }

    private static float progress(int ticks) {
        return ticks / (float)ContentOrbitalLaserSettings.TOTAL_TICKS;
    }

    private static int phaseStart(int phaseIndex) {
        return ContentOrbitalLaserSettings.phasePlan().phaseStartTick(phaseIndex);
    }
}
