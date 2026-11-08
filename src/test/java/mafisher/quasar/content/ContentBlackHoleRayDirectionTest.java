package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/**
 * Headless mirror of the content black-hole camera-ray reconstruction. The shader must sample the far plane:
 * Minecraft folds the first-person bob into the projection as a view-space translation, and inverting that on the
 * 0.05-block near plane turns a 0.1-block bob into tens of degrees of ray error. Keep {@code black_hole.fsh} in sync.
 */
class ContentBlackHoleRayDirectionTest {
    private static final float FOV = (float)Math.toRadians(70.0);
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float NEAR = 0.05F;
    private static final float FAR = 1024.0F;

    @Test
    void bobTranslationIsAmplifiedByNearPlaneRayReconstruction() {
        Matrix4f plainProjection = new Matrix4f().perspective(FOV, ASPECT, NEAR, FAR);
        Matrix4f bob = new Matrix4f()
            .translate(0.05F, 0.0F, 0.0F)
            .rotateX((float)Math.toRadians(0.404));
        Matrix4f bobbedProjection = new Matrix4f(plainProjection).mul(bob);

        double nearPlaneError = centerRayOffset(bobbedProjection, -1.0F);
        double farPlaneError = centerRayOffset(bobbedProjection, 1.0F);
        double plainError = centerRayOffset(plainProjection, 1.0F);

        assertTrue(nearPlaneError > 30.0, "near-plane reconstruction must expose the bob amplification");
        assertTrue(farPlaneError < 1.0, "far-plane reconstruction must keep the true camera direction");
        assertEquals(0.0, plainError, 1.0E-4);
    }

    private static double centerRayOffset(Matrix4f projection, float clipZ) {
        Vector4f clip = new Vector4f(0.0F, 0.0F, clipZ, 1.0F);
        Vector4f view = new Vector4f(clip).mul(projection.invert(new Matrix4f()));
        view.div(view.w);
        Vector3f direction = new Vector3f(view.x, view.y, view.z).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, -direction.z))));
    }
}
