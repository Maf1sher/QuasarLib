package mafisher.quasar.content.blackhole;

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
    void foregroundEntityHidesTheHorizonShadow() {
        double horizon = 6.0;
        double distance = 160.0;
        ContentBlackHoleGeodesic.Trace trace = ContentBlackHoleGeodesic.trace(
            new Vector3d(distance, 0.0, 0.0), new Vector3d(-1.0, 0.0, 0.0), horizon, STEPS
        );

        assertTrue(trace.captured());
        double entry = ContentBlackHoleGeodesic.nearestSurfaceEntry(trace, Double.NaN, false);
        assertTrue(entry > distance - 2.0 * horizon,
            "the traced entry must sit at the horizon, not at a bounding sphere: " + entry);
        assertFalse(ContentBlackHoleGeodesic.isVisibleAtDepth(distance - 10.0, entry, 0.1),
            "an entity ten blocks in front of the hole must hide the shadow");
        assertTrue(ContentBlackHoleGeodesic.isVisibleAtDepth(distance + 10.0, entry, 0.1),
            "geometry behind the hole must not clip the shadow");
    }

    @Test
    void foregroundEntityHidesTheLensShell() {
        ContentBlackHoleGeodesic.Trace trace = ContentBlackHoleGeodesic.trace(
            new Vector3d(160.0, 20.0, 0.0), new Vector3d(-1.0, 0.0, 0.0), HORIZON * 6.0, STEPS
        );

        assertFalse(trace.captured(), "the ray passes outside the shadow");
        double entry = ContentBlackHoleGeodesic.nearestSurfaceEntry(trace, Double.NaN, false);
        assertTrue(entry > 150.0,
            "a lens-only pixel must use the closest traced approach, not the lens shell: " + entry);
        assertFalse(ContentBlackHoleGeodesic.isVisibleAtDepth(150.0, entry, 0.1),
            "an entity in front of the hole must hide the lens warp");
    }

    @Test
    void nearDiskCrossingIsTheEffectSurface() {
        ContentBlackHoleGeodesic.DiskTrace diskTrace = ContentBlackHoleGeodesic.traceWithDisk(
            new Vector3d(160.0, 60.0, 0.0),
            new Vector3d(-120.0, -60.0, 0.0),
            6.0,
            new Vector3d(0.0, 1.0, 0.0),
            18.0,
            54.0,
            STEPS
        );

        assertTrue(diskTrace.crossings() > 0, "the ray must cross the disk annulus");
        double entry = ContentBlackHoleGeodesic.nearestSurfaceEntry(
            diskTrace.trace(), diskTrace.nearestDiskEntry(), true);
        assertTrue(entry < 150.0, "the near disk crossing is in front of the hole: " + entry);
        assertTrue(ContentBlackHoleGeodesic.isVisibleAtDepth(155.0, entry, 0.1),
            "an entity behind the near disk may still show the disk");
        assertFalse(ContentBlackHoleGeodesic.isVisibleAtDepth(entry - 20.0, entry, 0.1),
            "an entity in front of the near disk must hide it");
    }

    @Test
    void farSideDiskOwnsTheDepthOutsideTheShadow() {
        ContentBlackHoleGeodesic.DiskTrace diskTrace = ContentBlackHoleGeodesic.traceWithDisk(
            new Vector3d(160.0, 100.0, 0.0),
            new Vector3d(-1.0, -100.0 / 210.0, 0.0),
            6.0,
            new Vector3d(0.0, 1.0, 0.0),
            18.0,
            54.0,
            STEPS
        );

        assertFalse(diskTrace.trace().captured(), "the ray passes outside the shadow");
        assertTrue(diskTrace.crossings() > 0, "the ray must cross the far side of the disk");
        double closest = diskTrace.trace().nearestEntry();
        double disk = diskTrace.nearestDiskEntry();
        assertTrue(disk > closest,
            "the far-side disk must sit behind the closest approach: " + disk + " > " + closest);
        double entry = ContentBlackHoleGeodesic.nearestSurfaceEntry(
            diskTrace.trace(), disk, true);
        assertEquals(disk, entry, 1.0E-6,
            "a visible far-side disk must own the effect depth");
        assertFalse(ContentBlackHoleGeodesic.isVisibleAtDepth(closest + 4.0, entry, 0.1),
            "terrain between the hole and the far-side disk must hide the disk");
        assertTrue(ContentBlackHoleGeodesic.isVisibleAtDepth(disk + 10.0, entry, 0.1),
            "terrain beyond the far-side disk must not clip it");
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
