package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mafisher.quasar.api.client.ScreenEffectMedium;
import mafisher.quasar.api.client.ScreenEffectMediumPath;
import mafisher.quasar.api.client.ScreenEffectMediumSpan;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Headless mirror of the water-plane refraction added to {@code black_hole.fsh}. The
 * shader bends the view ray at the surface before tracing the effect, so these cases
 * guard the guards: frame consistency, passthrough conditions, Snell bending, total
 * internal reflection, Fresnel transmission, and the submerged fog leg. Keep the
 * shader helpers in sync.
 */
class ContentBlackHoleWaterRefractionTest {
    private static final Vector3d UP = new Vector3d(0.0, 1.0, 0.0);
    private static final Vector3d DOWN = new Vector3d(0.0, -1.0, 0.0);
    private static final double WATER_IOR = 1.333;

    @Test
    void noSurfaceLeavesRayUntouched() {
        Vector3d origin = new Vector3d(0.0, 30.0, 0.0);
        Vector3d direction = new Vector3d(0.0, -0.5, -0.866).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            origin, direction, -5.0, false, false, 0.0);

        assertEquals(-1.0, ray.surfaceDistance());
        assertFalse(ray.totalInternalReflection());
        assertEquals(0.0, ray.direction().dot(direction) - 1.0, 1.0E-9);
        assertEquals(0.0, ray.origin().distance(origin), 1.0E-9);
    }

    @Test
    void surfaceBehindTheEffectLeavesRayUntouched() {
        // Looking up at a hole above the camera while the surface is below it.
        Vector3d direction = new Vector3d(0.0, 0.4, -0.916).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, -5.0, true, false, 0.0);

        assertEquals(-1.0, ray.surfaceDistance());
        assertEquals(0.0, ray.direction().dot(direction) - 1.0, 1.0E-9);
    }

    @Test
    void horizontalRaySkipsTheSurface() {
        Vector3d direction = new Vector3d(0.0, 0.0, -1.0);

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, -5.0, true, false, 0.0);

        assertEquals(-1.0, ray.surfaceDistance());
    }

    @Test
    void surfacePointIsBuiltFromTheHoleRelativeCameraOrigin() {
        // The old shader used direction * surfaceDistance, i.e. a camera-relative
        // point, as the hole-relative trace origin. That shifted the effect by the
        // whole camera-to-hole distance.
        Vector3d origin = new Vector3d(0.0, -24.0, 160.0);
        Vector3d direction = new Vector3d(0.0, 0.5, -0.866).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            origin, direction, 20.0, true, false, 0.0);

        double surfaceDistance = 20.0 / direction.y;
        Vector3d expected = new Vector3d(origin).fma(surfaceDistance, direction);
        assertEquals(surfaceDistance, ray.surfaceDistance(), 1.0E-9);
        assertEquals(0.0, ray.origin().distance(expected), 1.0E-9);
        assertTrue(ray.origin().distance(new Vector3d(direction).mul(surfaceDistance)) > 1.0,
            "the trace origin must not be the old camera-relative hit");
    }

    @Test
    void airToWaterBendsTheRayTowardTheNormal() {
        Vector3d origin = new Vector3d(0.0, 30.0, 0.0);
        Vector3d direction = new Vector3d(0.0, -0.5, -0.866).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            origin, direction, -5.0, true, false, 0.0);

        assertEquals(10.0, ray.surfaceDistance(), 1.0E-3);
        assertEquals(25.0, ray.origin().y(), 1.0E-3);
        assertEquals(1.0, ray.direction().length(), 1.0E-9);
        assertTrue(ray.direction().dot(DOWN) > direction.dot(DOWN),
            "refraction into water must bend toward the surface normal");
    }

    @Test
    void waterToAirBendsTheRayTowardTheNormal() {
        Vector3d direction = new Vector3d(0.0, 0.8, -0.6);

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, 5.0, true, true, 0.0);

        assertTrue(ray.surfaceDistance() > 0.0);
        assertTrue(ray.direction().dot(UP) < direction.dot(UP),
            "refraction out of water must bend away from the surface normal");
    }

    @Test
    void grazingExitFromWaterIsTotalInternalReflection() {
        // ~77 degrees from the normal, well past the 48.6 degree critical angle.
        Vector3d direction = new Vector3d(0.0, 0.22, 0.975).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, 5.0, true, true, 0.0);

        assertTrue(ray.surfaceDistance() > 0.0);
        assertTrue(ray.totalInternalReflection(),
            "the above-water effect must be hidden in the mirror region");
        assertEquals(0.0, ray.direction().dot(direction) - 1.0, 1.0E-9);
    }

    @Test
    void normalIncidenceStaysCloseToTheNormal() {
        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), new Vector3d(0.0, -1.0, 0.0),
            -5.0, true, false, 425.0);

        assertEquals(5.0, ray.surfaceDistance(), 1.0E-9);
        double tilt = Math.toDegrees(Math.acos(Math.min(1.0, ray.direction().dot(DOWN))));
        assertTrue(tilt < 10.0, "near-normal ripples must stay subtle, got " + tilt);
    }

    @Test
    void waveNormalIsUnitAndGentlySloped() {
        for (double age = 0.0; age < 200.0; age += 37.0) {
            Vector3d normal = ContentBlackHoleGeodesic.waterSurfaceNormal(12.5, -7.25, age);
            assertEquals(1.0, normal.length(), 1.0E-9);
            double tilt = Math.toDegrees(Math.acos(Math.min(1.0, normal.dot(UP))));
            assertTrue(tilt > 0.0 && tilt < 4.0, "ripple tilt out of range: " + tilt);
        }
    }

    @Test
    void fresnelIsWeakAtNormalIncidenceAndOpaqueAtTheCriticalAngle() {
        double normalIncidence = ContentBlackHoleGeodesic.fresnelReflectance(1.0, WATER_IOR, 1.0);
        assertTrue(normalIncidence > 0.01 && normalIncidence < 0.03,
            "normal water reflection must stay near 2%, got " + normalIncidence);

        double criticalCosine = Math.sqrt(1.0 - 1.0 / (WATER_IOR * WATER_IOR));
        double critical = ContentBlackHoleGeodesic.fresnelReflectance(
            criticalCosine, WATER_IOR, 1.0);
        assertTrue(critical > 0.99,
            "Fresnel must approach total reflection at the critical angle, got " + critical);
    }

    @Test
    void surfaceTransmissionIsZeroForTotalInternalReflection() {
        Vector3d direction = new Vector3d(0.0, 0.22, 0.975).normalize();
        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, 5.0, true, true, 0.0);

        assertEquals(0.0, ContentBlackHoleGeodesic.surfaceTransmittance(ray, true), 1.0E-9);
    }

    @Test
    void fogUsesEverySubmergedLegOfThePath() {
        // Camera above water looking at a submerged effect: fog only surface -> effect.
        var effectSide = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 10.0, 100.0)
        ), List.of());
        assertEquals(90.0, ContentBlackHoleGeodesic.fogDistance(effectSide, 100.0), 1.0E-6);

        // Camera submerged looking at an above-water effect: fog only camera -> surface.
        var cameraSide = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 0.0, 5.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 5.0, 100.0)
        ), List.of());
        assertEquals(5.0, ContentBlackHoleGeodesic.fogDistance(cameraSide, 100.0), 1.0E-6);

        // A lake between two air endpoints still contributes its whole submerged leg.
        var lake = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 20.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 20.0, 30.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 30.0, 100.0)
        ), List.of());
        assertEquals(10.0, ContentBlackHoleGeodesic.fogDistance(lake, 100.0), 1.0E-6);

        // A dry path falls back to the traced entry distance.
        assertEquals(100.0, ContentBlackHoleGeodesic.fogDistance(
            ScreenEffectMediumPath.EMPTY, 100.0), 1.0E-6);
    }
}
