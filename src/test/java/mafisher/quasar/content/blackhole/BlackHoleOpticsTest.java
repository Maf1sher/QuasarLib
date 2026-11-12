package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Verifies that the CPU oracle reads and uses the shipped optics constants. */
class BlackHoleOpticsTest {
    @Test
    void readsFinitePositiveConstantsFromTheShippedInclude() {
        assertTrue(BlackHoleOptics.B_CRITICAL > 2.0 && BlackHoleOptics.B_CRITICAL < 3.0);
        assertTrue(BlackHoleOptics.HORIZON_EPSILON >= 1.0);
        assertTrue(BlackHoleOptics.WATER_IOR > 1.0);
        assertTrue(BlackHoleOptics.RIPPLE_SLOPE > 0.0);
        assertTrue(BlackHoleOptics.FRESNEL_FLOOR > 0.0);
        assertTrue(BlackHoleOptics.STEP_NEAR_LIMIT > 0.0);
        assertTrue(BlackHoleOptics.STEP_NEAR_MIN <= BlackHoleOptics.STEP_NEAR_MAX);
        assertTrue(BlackHoleOptics.STEP_FAR_MIN <= BlackHoleOptics.STEP_FAR_MAX);
        assertTrue(BlackHoleOptics.ESCAPE_FACTOR > 1.0);
    }

    @Test
    void horizonCaptureUsesTheShippedEpsilon() {
        double horizon = 4.0;
        double inside = horizon * (BlackHoleOptics.HORIZON_EPSILON - 0.001);
        double outside = horizon * (BlackHoleOptics.HORIZON_EPSILON + 0.001);

        assertTrue(ContentBlackHoleGeodesic.trace(
            new Vector3d(inside, 0.0, 0.0), new Vector3d(1.0, 0.0, 0.0), horizon, 1
        ).captured());
        assertFalse(ContentBlackHoleGeodesic.trace(
            new Vector3d(outside, 0.0, 0.0), new Vector3d(1.0, 0.0, 0.0), horizon, 1
        ).captured());
    }

    @Test
    void waterIorDrivesTheCriticalAngle() {
        double criticalCosine = Math.sqrt(1.0 - 1.0
            / (BlackHoleOptics.WATER_IOR * BlackHoleOptics.WATER_IOR));

        assertTrue(ContentBlackHoleGeodesic.fresnelReflectance(
            criticalCosine, BlackHoleOptics.WATER_IOR, 1.0) >= 0.999);
        assertTrue(ContentBlackHoleGeodesic.fresnelReflectance(
            1.0, BlackHoleOptics.WATER_IOR, 1.0) < 0.1);
    }

    @Test
    void rippleSlopeBoundsTheSurfaceNormal() {
        Vector3d normal = ContentBlackHoleGeodesic.waterSurfaceNormal(12.5, -7.25, 300.0);

        assertTrue(normal.y() > 0.99, "the synthetic water surface stays almost flat");
        assertTrue(Math.hypot(normal.x(), normal.z()) <= 0.05,
            "the shared ripple slope keeps the normal subtle");
    }

    @Test
    void stepLengthStaysWithinTheSharedNearAndFarBounds() {
        double horizon = 10.0;
        double near = ContentBlackHoleGeodesic.stepLength(
            BlackHoleOptics.STEP_NEAR_LIMIT * horizon * 0.5, horizon);
        double far = ContentBlackHoleGeodesic.stepLength(
            BlackHoleOptics.STEP_NEAR_LIMIT * horizon * 1.1, horizon);

        assertTrue(near >= BlackHoleOptics.STEP_NEAR_MIN * horizon);
        assertTrue(near <= BlackHoleOptics.STEP_NEAR_MAX * horizon);
        assertTrue(far > BlackHoleOptics.STEP_NEAR_MAX * horizon,
            "the far branch must not use the near-step cap");
        assertTrue(far >= BlackHoleOptics.STEP_FAR_MIN * horizon);
        assertTrue(far <= BlackHoleOptics.STEP_FAR_MAX * horizon);
    }
}
