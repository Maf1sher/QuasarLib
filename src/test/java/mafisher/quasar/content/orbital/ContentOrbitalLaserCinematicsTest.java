package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectParameterSet;
import mafisher.quasar.api.effect.EffectParameterState;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserCinematicsTest {
    private static final EffectParameterSet PARAMETERS = ContentOrbitalLaserCinematics.parameters();
    private static final float DESCENT = progress(
        ContentOrbitalLaserSettings.TARGET_TICKS
    );
    private static final float IMPACT = progress(
        ContentOrbitalLaserSettings.TARGET_TICKS + ContentOrbitalLaserSettings.DESCENT_TICKS
    );
    private static final float SUSTAIN = progress(
        ContentOrbitalLaserSettings.TARGET_TICKS + ContentOrbitalLaserSettings.DESCENT_TICKS
            + ContentOrbitalLaserSettings.IMPACT_TICKS
    );
    private static final float COLLAPSE = progress(
        ContentOrbitalLaserSettings.TARGET_TICKS + ContentOrbitalLaserSettings.DESCENT_TICKS
            + ContentOrbitalLaserSettings.IMPACT_TICKS + ContentOrbitalLaserSettings.SUSTAIN_TICKS
    );
    private static final float AFTERMATH = progress(
        ContentOrbitalLaserSettings.TOTAL_TICKS - ContentOrbitalLaserSettings.AFTERMATH_TICKS
    );

    @Test
    void targetPhaseShowsOnlyTheMarker() {
        assertFalse(ContentOrbitalLaserCinematics.screenVisible(0.0F));
        assertEquals(1.0F, sample("BeamTip", 0.0F), 1.0E-6F);
        assertEquals(0.0F, sample("BeamStrength", 0.0F), 1.0E-6F);
        assertEquals(1.0F, sample("MarkerAlpha", 0.0F), 1.0E-6F);
        assertEquals(0.0F, sample("ImpactFlash", 0.0F), 1.0E-6F);
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
        float expanded = progress(ContentOrbitalLaserSettings.TARGET_TICKS
            + ContentOrbitalLaserSettings.DESCENT_TICKS + ContentOrbitalLaserSettings.IMPACT_TICKS
            + 16);
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
    void beamFadesThroughCollapseAndStopsInTheAftermath() {
        assertEquals(1.0F, sample("BeamStrength", COLLAPSE), 1.0E-6F);
        assertTrue(sample("BeamStrength", (COLLAPSE + AFTERMATH) * 0.5F) < 1.0F);
        assertEquals(0.0F, sample("BeamStrength", AFTERMATH), 1.0E-6F);
        assertFalse(ContentOrbitalLaserCinematics.screenVisible(AFTERMATH));
        assertEquals(0.0F, sample("ContactGlow", AFTERMATH), 1.0E-6F);
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
}
