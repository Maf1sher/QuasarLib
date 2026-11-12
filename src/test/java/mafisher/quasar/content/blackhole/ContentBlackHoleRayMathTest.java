package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Headless regression checks for the content black-hole geodesic. The shader
 * mirrors the same impact-parameter and disk-crossing rules; GPU output remains
 * an in-game check.
 */
class ContentBlackHoleRayMathTest {
    private static final int STEPS = 512;

    @Test
    void captureRegionIsScaleInvariant() {
        for (double scale : new double[] { 3.0, 6.0, 12.0 }) {
            ContentBlackHoleGeodesic.Trace inner = ContentBlackHoleGeodesic.trace(
                new Vector3d(200.0, 2.2 * scale, 0.0), new Vector3d(-1.0, 0.0, 0.0), scale, STEPS
            );
            ContentBlackHoleGeodesic.Trace outer = ContentBlackHoleGeodesic.trace(
                new Vector3d(200.0, 3.2 * scale, 0.0), new Vector3d(-1.0, 0.0, 0.0), scale, STEPS
            );

            assertTrue(inner.captured(), "capture at 2.2R for scale " + scale);
            assertFalse(outer.captured(), "escape at 3.2R for scale " + scale);
        }
    }

    @Test
    void diskIsCrossedAtAllDistances() {
        for (double distance : new double[] { 160.0, 500.0, 1000.0 }) {
            double scale = 6.0;
            double impact = 5.0 * scale;
            double angle = Math.asin(Math.min(1.0, impact / distance));
            ContentBlackHoleGeodesic.DiskTrace trace = ContentBlackHoleGeodesic.traceWithDisk(
                new Vector3d(0.0, distance, 0.0),
                new Vector3d(Math.sin(angle), -Math.cos(angle), 0.0),
                scale,
                new Vector3d(0.0, 1.0, 0.0),
                3.0 * scale,
                9.0 * scale,
                STEPS
            );

            assertTrue(trace.crossings() > 0, "disk must be crossed at distance " + distance);
        }
    }

    @Test
    void tiltedEdgeOnRayCrossesTheDisk() {
        ContentBlackHoleGeodesic.DiskTrace trace = ContentBlackHoleGeodesic.traceWithDisk(
            new Vector3d(0.0, 4.0, 100.0),
            new Vector3d(0.0, -0.05, -1.0),
            6.0,
            new Vector3d(0.0, 1.0, 0.0),
            18.0,
            54.0,
            STEPS
        );

        assertTrue(trace.crossings() > 0);
    }
}
