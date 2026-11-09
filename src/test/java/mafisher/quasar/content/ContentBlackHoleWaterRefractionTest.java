package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Headless mirror of the water-plane refraction added to {@code black_hole.fsh}. The
 * shader bends the view ray at the surface before tracing the effect, so these cases
 * guard the guards: passthrough conditions, Snell bending, and total internal
 * reflection. Keep the shader helper in sync.
 */
class ContentBlackHoleWaterRefractionTest {
    private static final Vector3d UP = new Vector3d(0.0, 1.0, 0.0);
    private static final Vector3d DOWN = new Vector3d(0.0, -1.0, 0.0);
    private static final Vector3d HOLE_OFFSET = new Vector3d(0.0, -30.0, -8.0);

    @Test
    void noSurfaceLeavesRayUntouched() {
        Vector3d direction = new Vector3d(0.0, -0.5, -0.866).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, -5.0, false, false, 0.0, HOLE_OFFSET);

        assertEquals(-1.0, ray.surfaceDistance());
        assertEquals(0.0, ray.direction().dot(direction) - 1.0, 1.0E-9);
    }

    @Test
    void surfaceBehindTheEffectLeavesRayUntouched() {
        // Looking up at a hole above the camera while the surface is below it.
        Vector3d direction = new Vector3d(0.0, 0.4, -0.916).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, -5.0, true, false, 0.0, HOLE_OFFSET);

        assertEquals(-1.0, ray.surfaceDistance());
        assertEquals(0.0, ray.direction().dot(direction) - 1.0, 1.0E-9);
    }

    @Test
    void horizontalRaySkipsTheSurface() {
        Vector3d direction = new Vector3d(0.0, 0.0, -1.0);

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, -5.0, true, false, 0.0, HOLE_OFFSET);

        assertEquals(-1.0, ray.surfaceDistance());
    }

    @Test
    void airToWaterBendsTheRayTowardTheNormal() {
        Vector3d direction = new Vector3d(0.0, -0.5, -0.866).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, -5.0, true, false, 0.0, HOLE_OFFSET);

        assertEquals(10.0, ray.surfaceDistance(), 1.0E-3);
        assertEquals(-5.0, ray.origin().y(), 1.0E-3);
        assertEquals(1.0, ray.direction().length(), 1.0E-9);
        assertTrue(ray.direction().dot(DOWN) > direction.dot(DOWN),
            "refraction into water must bend toward the surface normal");
    }

    @Test
    void waterToAirBendsTheRayTowardTheNormal() {
        Vector3d direction = new Vector3d(0.0, 0.8, -0.6);

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, 5.0, true, true, 0.0, HOLE_OFFSET);

        assertTrue(ray.surfaceDistance() > 0.0);
        assertTrue(ray.direction().dot(UP) < direction.dot(UP),
            "refraction out of water must bend away from the surface normal");
    }

    @Test
    void grazingExitFromWaterIsTotalInternalReflection() {
        // ~77 degrees from the normal, well past the 48.6 degree critical angle.
        Vector3d direction = new Vector3d(0.0, 0.22, 0.975).normalize();

        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), direction, 5.0, true, true, 0.0, HOLE_OFFSET);

        assertEquals(-1.0, ray.surfaceDistance());
        assertEquals(0.0, ray.direction().dot(direction) - 1.0, 1.0E-9);
    }

    @Test
    void normalIncidenceStaysCloseToTheNormal() {
        var ray = ContentBlackHoleGeodesic.refractedAtWater(
            new Vector3d(0.0, 30.0, 0.0), new Vector3d(0.0, -1.0, 0.0),
            -5.0, true, false, 425.0, HOLE_OFFSET);

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
            assertTrue(tilt > 0.0 && tilt < 12.0, "ripple tilt out of range: " + tilt);
        }
    }
}
