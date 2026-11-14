package mafisher.quasar.client.effect.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import mafisher.quasar.api.client.render.EffectScreenMetrics;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class EffectScreenProjectionTest {
    private static final int VIEWPORT = 1920;

    @Test
    void projectsCoverageAtAKnownDistance() {
        EffectScreenMetrics metrics = projection(90.0F).project(0.0, 0.0, -10.0, 1.0);

        assertTrue(metrics.projected());
        double expectedRadius = 1.0 / Math.sqrt(99.0);
        double expectedCoverage = Math.PI * expectedRadius * expectedRadius / 4.0;
        assertEquals(expectedCoverage, metrics.coverage(), 0.000001);
        assertEquals(0.0, metrics.centreDistance(), 0.000001);
        assertTrue(metrics.score() > 0.0);
    }

    @Test
    void narrowerFieldOfViewIncreasesCoverage() {
        EffectScreenMetrics wide = projection(90.0F).project(0.0, 0.0, -10.0, 1.0);
        EffectScreenMetrics narrow = projection(30.0F).project(0.0, 0.0, -10.0, 1.0);

        assertTrue(narrow.coverage() > wide.coverage());
        assertTrue(narrow.score() > wide.score());
    }

    @Test
    void equalDistanceAndRadiusGiveEqualCoverageButCentrePriorityLowersTheEdgeScore() {
        EffectScreenProjection projection = projection(90.0F);
        EffectScreenMetrics centre = projection.project(0.0, 0.0, -10.0, 1.0);
        EffectScreenMetrics edge = projection.project(6.0, 0.0, -8.0, 1.0);

        assertEquals(centre.coverage(), edge.coverage(), 0.000001);
        assertTrue(edge.centreDistance() > centre.centreDistance());
        assertTrue(edge.score() < centre.score());
    }

    @Test
    void cameraInsideTheBoundingSphereCoversTheViewport() {
        EffectScreenMetrics metrics = projection(90.0F).project(0.0, 0.0, 0.0, 1.0);

        assertTrue(metrics.projected());
        assertEquals(1.0, metrics.coverage(), 0.0);
        assertEquals(0.0, metrics.centreDistance(), 0.0);
        assertEquals(1.0, metrics.score(), 0.0);
    }

    @Test
    void unusableProjectionFallsBackToUnknown() {
        EffectScreenProjection projection = projection(90.0F);

        assertFalse(projection.project(0.0, 0.0, 10.0, 1.0).projected());
        assertFalse(projection.project(0.0, 0.0, -10.0, 0.0).projected());
        assertFalse(projection.project(0.0, 0.0, -10.0, Double.NaN).projected());
        assertFalse(projection.project(Double.NaN, 0.0, -10.0, 1.0).projected());
        assertFalse(projection.project(0.0, 0.0, -10.0, Double.POSITIVE_INFINITY).projected());
        assertEquals(EffectScreenMetrics.UNKNOWN, projection.project(0.0, 0.0, 10.0, 1.0));
    }

    @Test
    void degenerateProjectionMatricesAreRejected() {
        Matrix4f projection = new Matrix4f().identity().m00(0.0F).m01(1.0F).m10(1.0F);
        EffectCameraSnapshot camera = new EffectCameraSnapshot(
            Vec3.ZERO, new Matrix4f(), projection, VIEWPORT, VIEWPORT
        );

        assertTrue(EffectScreenProjection.from(camera) == null);
    }

    @Test
    void rejectsNullCamera() {
        assertThrows(NullPointerException.class, () -> EffectScreenProjection.from(null));
    }

    private static EffectScreenProjection projection(float fovDegrees) {
        Matrix4f projection = new Matrix4f().perspective(
            (float)Math.toRadians(fovDegrees), 1.0F, 0.05F, 1000.0F
        );
        EffectCameraSnapshot camera = new EffectCameraSnapshot(
            Vec3.ZERO, new Matrix4f(), projection, VIEWPORT, VIEWPORT
        );
        EffectScreenProjection result = EffectScreenProjection.from(camera);
        assertTrue(result != null);
        return result;
    }
}
