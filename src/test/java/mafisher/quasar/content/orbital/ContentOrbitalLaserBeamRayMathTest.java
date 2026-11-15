package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Headless mirror of the beam cylinder and ground-ring intersection used by the shader. */
class ContentOrbitalLaserBeamRayMathTest {
    private static final double PLANCK = 1.0E-9;

    @Test
    void rayThroughTheCylinderReturnsTheInsideSpan() {
        double[] span = span(
            0.0, 0.0, -10.0, 0.0, 0.0, 1.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
            2.0, 0.0, 256.0, 100.0
        );

        assertTrue(span != null);
        assertEquals(10.0 - Math.sqrt(4.0), span[0], PLANCK);
        assertEquals(10.0 + Math.sqrt(4.0), span[1], PLANCK);
    }

    @Test
    void cameraInsideAndParallelToTheAxisStartsAtZero() {
        double[] span = span(
            0.0, 10.0, 0.0, 0.0, -1.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
            2.0, 0.0, 256.0, 50.0
        );

        assertTrue(span != null);
        assertEquals(0.0, span[0], PLANCK);
        assertEquals(10.0, span[1], PLANCK);
    }

    @Test
    void tipAndDepthClipTheVolume() {
        double[] tip = span(
            0.0, 10.0, 0.0, 0.0, -1.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
            2.0, 5.0, 256.0, 50.0
        );
        double[] depth = span(
            0.0, 10.0, 0.0, 0.0, -1.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
            2.0, 0.0, 256.0, 5.0
        );

        assertTrue(tip != null);
        assertEquals(0.0, tip[0], PLANCK);
        assertEquals(5.0, tip[1], PLANCK);
        assertTrue(depth != null);
        assertEquals(0.0, depth[0], PLANCK);
        assertEquals(5.0, depth[1], PLANCK);
    }

    @Test
    void missAndBehindCameraReturnNoSpan() {
        assertNull(span(
            10.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
            2.0, 0.0, 256.0, 100.0
        ));
        assertNull(span(
            0.0, 0.0, 10.0, 0.0, 0.0, 1.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
            2.0, 0.0, 256.0, 100.0
        ));
    }

    @Test
    void ringPlaneHitRespectsDepthAndParallelRays() {
        assertEquals(10.0, ringDistance(
            0.0, 10.0, 0.0, 0.0, -1.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 50.0
        ), PLANCK);
        assertTrue(Double.isNaN(ringDistance(
            0.0, 10.0, 0.0, 0.0, -1.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 5.0
        )));
        assertTrue(Double.isNaN(ringDistance(
            0.0, 10.0, -5.0, 0.0, 0.0, 1.0,
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 50.0
        )));
    }

    private static double[] span(
        double ox, double oy, double oz,
        double dx, double dy, double dz,
        double cx, double cy, double cz,
        double ax, double ay, double az,
        double radius, double tip, double top, double maxDistance
    ) {
        double axisLength = Math.sqrt(ax * ax + ay * ay + az * az);
        double ux = ax / axisLength;
        double uy = ay / axisLength;
        double uz = az / axisLength;
        double offsetX = ox - cx;
        double offsetY = oy - cy;
        double offsetZ = oz - cz;
        double offsetAxis = offsetX * ux + offsetY * uy + offsetZ * uz;
        double directionAxis = dx * ux + dy * uy + dz * uz;
        double offsetRadialX = offsetX - ux * offsetAxis;
        double offsetRadialY = offsetY - uy * offsetAxis;
        double offsetRadialZ = offsetZ - uz * offsetAxis;
        double directionRadialX = dx - ux * directionAxis;
        double directionRadialY = dy - uy * directionAxis;
        double directionRadialZ = dz - uz * directionAxis;
        double a = directionRadialX * directionRadialX + directionRadialY * directionRadialY
            + directionRadialZ * directionRadialZ;
        double b = 2.0 * (offsetRadialX * directionRadialX + offsetRadialY * directionRadialY
            + offsetRadialZ * directionRadialZ);
        double c = offsetRadialX * offsetRadialX + offsetRadialY * offsetRadialY
            + offsetRadialZ * offsetRadialZ - radius * radius;
        double near;
        double far;
        if (a < 1.0E-6) {
            if (c > 0.0) {
                return null;
            }
            near = -1.0E6;
            far = 1.0E6;
        } else {
            double discriminant = b * b - 4.0 * a * c;
            if (discriminant < 0.0) {
                return null;
            }
            double root = Math.sqrt(discriminant);
            near = (-b - root) / (2.0 * a);
            far = (-b + root) / (2.0 * a);
        }
        if (Math.abs(directionAxis) < 1.0E-6) {
            if (offsetAxis < tip || offsetAxis > top) {
                return null;
            }
        } else {
            double first = (tip - offsetAxis) / directionAxis;
            double second = (top - offsetAxis) / directionAxis;
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
        }
        near = Math.max(near, 0.0);
        far = Math.min(far, maxDistance);
        return far > near ? new double[] {near, far} : null;
    }

    private static double ringDistance(
        double ox, double oy, double oz,
        double dx, double dy, double dz,
        double cx, double cy, double cz,
        double ax, double ay, double az,
        double maxDistance
    ) {
        double axisLength = Math.sqrt(ax * ax + ay * ay + az * az);
        double ux = ax / axisLength;
        double uy = ay / axisLength;
        double uz = az / axisLength;
        double denominator = dx * ux + dy * uy + dz * uz;
        if (Math.abs(denominator) < 1.0E-6) {
            return Double.NaN;
        }
        double planeDistance = (cx * ux + cy * uy + cz * uz
            - (ox * ux + oy * uy + oz * uz)) / denominator;
        return planeDistance > 0.0 && planeDistance < maxDistance
            ? planeDistance
            : Double.NaN;
    }
}
