package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
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
}
