package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import mafisher.quasar.api.client.ScreenEffectCameraState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ScreenEffectCameraStateTest {
    @Test
    void copiesMatricesOnConstructionAndAccess() {
        Matrix4f modelView = new Matrix4f().translation(1.0F, 2.0F, 3.0F);
        Matrix4f projection = new Matrix4f().scale(2.0F);
        ScreenEffectCameraState camera = new ScreenEffectCameraState(
            new Vec3(1.0, 2.0, 3.0), modelView, projection, 800, 600, 0.5F
        );

        Matrix4f returnedModelView = camera.modelViewMatrix();
        returnedModelView.identity();
        Matrix4f returnedProjection = camera.projectionMatrix();
        returnedProjection.identity();

        assertEquals(1.0F, camera.modelViewMatrix().m30(), 0.0001F);
        assertEquals(2.0F, camera.projectionMatrix().m11(), 0.0001F);
        assertEquals(800, camera.viewportWidth());
        assertEquals(600, camera.viewportHeight());
        assertEquals(0.5F, camera.partialTick(), 0.0001F);
        assertNotNull(camera.position());
    }

    @Test
    void exposesInverseMatricesThatRoundTrip() {
        Matrix4f modelView = new Matrix4f().translate(-10.0F, 4.0F, 7.0F)
            .rotateY(0.5F).rotateX(-0.2F);
        Matrix4f projection = new Matrix4f().perspective(
            (float) Math.toRadians(70.0), 1.5F, 0.05F, 1000.0F);
        ScreenEffectCameraState camera = new ScreenEffectCameraState(
            Vec3.ZERO, modelView, projection, 800, 600, 0.0F
        );

        assertIdentity(new Matrix4f(modelView).mul(camera.inverseViewMatrix()));
        assertIdentity(new Matrix4f(projection).mul(camera.inverseProjectionMatrix()));

        Matrix4f returned = camera.inverseViewMatrix();
        returned.identity();
        assertIdentity(new Matrix4f(modelView).mul(camera.inverseViewMatrix()));
    }

    @Test
    void reconstructsTheForwardRayAtScreenCentre() {
        Matrix4f projection = new Matrix4f().perspective(
            (float)Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1000.0F);
        ScreenEffectCameraState camera = new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), projection, 800, 600, 0.0F
        );

        Vector3f ray = camera.worldRay(0.5F, 0.5F);

        assertEquals(0.0F, ray.x(), 1.0E-5F);
        assertEquals(0.0F, ray.y(), 1.0E-5F);
        assertEquals(-1.0F, ray.z(), 1.0E-5F);
    }

    @Test
    void projectsTheForwardDirectionToScreenCentre() {
        Matrix4f projection = new Matrix4f().perspective(
            (float)Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1000.0F);
        ScreenEffectCameraState camera = new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), projection, 800, 600, 0.0F
        );

        Optional<Vector2f> uv = camera.screenUv(new Vector3f(0.0F, 0.0F, -1.0F));

        assertTrue(uv.isPresent());
        assertEquals(0.5F, uv.get().x, 1.0E-5F);
        assertEquals(0.5F, uv.get().y, 1.0E-5F);
        assertTrue(camera.screenUv(new Vector3f(0.0F, 0.0F, 1.0F)).isEmpty());
    }

    @Test
    void worldRayAndScreenUvRoundTrip() {
        Matrix4f modelView = new Matrix4f().translate(-4.0F, 2.0F, 9.0F)
            .rotateY(0.7F).rotateX(-0.3F).rotateZ(0.15F);
        Matrix4f projection = new Matrix4f().perspective(
            (float)Math.toRadians(80.0), 1.6F, 0.05F, 1500.0F);
        ScreenEffectCameraState camera = new ScreenEffectCameraState(
            Vec3.ZERO, modelView, projection, 1280, 720, 0.0F
        );

        for (float u : new float[] { 0.2F, 0.5F, 0.8F }) {
            for (float v : new float[] { 0.2F, 0.5F, 0.8F }) {
                Vector3f ray = camera.worldRay(u, v);
                Optional<Vector2f> projected = camera.screenUv(ray);
                assertTrue(projected.isPresent());
                assertEquals(u, projected.get().x, 1.0E-4F);
                assertEquals(v, projected.get().y, 1.0E-4F);
            }
        }
    }

    @Test
    void detectsUsableMatricesWithoutThrowing() {
        assertTrue(ScreenEffectCameraState.isUsable(new Matrix4f(), new Matrix4f()));
        assertTrue(ScreenEffectCameraState.isUsable(
            new Matrix4f().translation(1.0F, 2.0F, 3.0F),
            new Matrix4f().perspective((float)Math.toRadians(70.0), 1.5F, 0.05F, 1000.0F)
        ));
        assertFalse(ScreenEffectCameraState.isUsable(new Matrix4f().zero(), new Matrix4f()));
        assertFalse(ScreenEffectCameraState.isUsable(new Matrix4f(), new Matrix4f().zero()));
        assertFalse(ScreenEffectCameraState.isUsable(null, new Matrix4f()));
        assertFalse(ScreenEffectCameraState.isUsable(new Matrix4f(), null));

        Matrix4f notFinite = new Matrix4f();
        notFinite.m00(Float.NaN);
        assertFalse(ScreenEffectCameraState.isUsable(new Matrix4f(), notFinite));
    }

    @Test
    void rejectsSingularMatrices() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f().zero(), new Matrix4f(), 1, 1, 0.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), new Matrix4f().zero(), 1, 1, 0.0F
        ));
    }

    @Test
    void rejectsInvalidDimensionsOrPartialTick() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), new Matrix4f(), 0, 1, 0.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), new Matrix4f(), 1, 0, 0.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), new Matrix4f(), 1, 1, 1.5F
        ));
    }

    @Test
    void rejectsNullComponents() {
        assertThrows(NullPointerException.class, () -> new ScreenEffectCameraState(
            null, new Matrix4f(), new Matrix4f(), 1, 1, 0.0F
        ));
        assertThrows(NullPointerException.class, () -> new ScreenEffectCameraState(
            Vec3.ZERO, null, new Matrix4f(), 1, 1, 0.0F
        ));
        assertThrows(NullPointerException.class, () -> new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), null, 1, 1, 0.0F
        ));
    }

    private static void assertIdentity(Matrix4f matrix) {
        assertEquals(1.0F, matrix.m00(), 0.0001F);
        assertEquals(0.0F, matrix.m01(), 0.0001F);
        assertEquals(0.0F, matrix.m02(), 0.0001F);
        assertEquals(0.0F, matrix.m03(), 0.0001F);
        assertEquals(0.0F, matrix.m10(), 0.0001F);
        assertEquals(1.0F, matrix.m11(), 0.0001F);
        assertEquals(0.0F, matrix.m12(), 0.0001F);
        assertEquals(0.0F, matrix.m13(), 0.0001F);
        assertEquals(0.0F, matrix.m20(), 0.0001F);
        assertEquals(0.0F, matrix.m21(), 0.0001F);
        assertEquals(1.0F, matrix.m22(), 0.0001F);
        assertEquals(0.0F, matrix.m23(), 0.0001F);
        assertEquals(0.0F, matrix.m30(), 0.0001F);
        assertEquals(0.0F, matrix.m31(), 0.0001F);
        assertEquals(0.0F, matrix.m32(), 0.0001F);
        assertEquals(1.0F, matrix.m33(), 0.0001F);
    }
}
