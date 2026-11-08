package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ContentBlackHoleGeodesicTest {
    private static final double HORIZON = 1.0;
    private static final int STEPS = 768;

    @Test
    void capturesRaysBelowTheCriticalImpactParameter() {
        ContentBlackHoleGeodesic.Trace trace = ContentBlackHoleGeodesic.trace(
            new Vector3d(200.0, 2.0, 0.0), new Vector3d(-1.0, 0.0, 0.0), HORIZON, STEPS
        );

        assertTrue(trace.captured(), "b=2r is inside the photon-capture cross-section");
        assertTrue(Double.isFinite(trace.closestApproach()));
    }

    @Test
    void escapesRaysAboveTheCriticalImpactParameter() {
        ContentBlackHoleGeodesic.Trace trace = ContentBlackHoleGeodesic.trace(
            new Vector3d(200.0, 3.0, 0.0), new Vector3d(-1.0, 0.0, 0.0), HORIZON, STEPS
        );

        assertFalse(trace.captured(), "b=3r is outside the shadow");
        assertTrue(Double.isFinite(trace.impact()));
        assertEquals(1.0, trace.outgoingDirection().length(), 1.0E-6);
    }

    @Test
    void capturesHeadOnRaysWithoutProducingNaN() {
        ContentBlackHoleGeodesic.Trace trace = ContentBlackHoleGeodesic.trace(
            new Vector3d(40.0, 0.0, 0.0), new Vector3d(-1.0, 0.0, 0.0), HORIZON, STEPS
        );

        assertTrue(trace.captured());
        assertTrue(Double.isFinite(trace.outgoingDirection().x()));
    }

    @Test
    void capturesRaysThatStartInsideTheHorizon() {
        ContentBlackHoleGeodesic.Trace trace = ContentBlackHoleGeodesic.trace(
            new Vector3d(0.5, 0.0, 0.0), new Vector3d(1.0, 0.0, 0.0), HORIZON, STEPS
        );

        assertTrue(trace.captured());
    }

    @Test
    void bendsEscapingRaysTowardTheHole() {
        ContentBlackHoleGeodesic.Trace trace = ContentBlackHoleGeodesic.trace(
            new Vector3d(200.0, 10.0, 0.0), new Vector3d(-1.0, 0.0, 0.0), HORIZON, STEPS
        );

        assertFalse(trace.captured());
        assertTrue(trace.outgoingDirection().y() < -0.0001,
            "the ray above the hole must bend downward toward it");
    }

    @Test
    void foregroundGeometryHidesTheEffectPerPixel() {
        double ringEntry = 145.0;
        double diskEntry = 106.0;
        double bias = 0.1;

        assertFalse(ContentBlackHoleGeodesic.isVisibleAtDepth(140.0, ringEntry, bias),
            "a tree in front of the ring must stay untouched");
        assertTrue(ContentBlackHoleGeodesic.isVisibleAtDepth(150.0, ringEntry, bias),
            "a tree behind the ring may still show the ring");
        assertTrue(ContentBlackHoleGeodesic.isVisibleAtDepth(1000.0, ringEntry, bias),
            "terrain beyond the hole must not clip the ring");
        assertFalse(ContentBlackHoleGeodesic.isVisibleAtDepth(105.0, diskEntry, bias));
        assertTrue(ContentBlackHoleGeodesic.isVisibleAtDepth(110.0, diskEntry, bias));
    }

    @Test
    void partialCoverageOnlyHidesCoveredPixels() {
        assertFalse(ContentBlackHoleGeodesic.isVisibleAtDepth(40.0, 145.0, 0.1));
        assertTrue(ContentBlackHoleGeodesic.isVisibleAtDepth(900.0, 145.0, 0.1));
    }

    @Test
    void cameraInsideTheHoleStillSeesTheEffect() {
        assertTrue(ContentBlackHoleGeodesic.isVisibleAtDepth(0.5, 0.0, 0.1));
    }

    @Test
    void findsDiskCrossingsForAnObliqueRay() {
        ContentBlackHoleGeodesic.DiskTrace trace = ContentBlackHoleGeodesic.traceWithDisk(
            new Vector3d(120.0, 20.0, 0.0),
            new Vector3d(-1.0, -0.2, 0.0),
            HORIZON,
            new Vector3d(0.0, 1.0, 0.0),
            3.0,
            30.0,
            STEPS
        );

        assertTrue(trace.crossings() > 0, "the ray must cross the disk plane");
        assertTrue(trace.firstDiskRadius() >= 3.0 && trace.firstDiskRadius() <= 30.0);
    }
}
