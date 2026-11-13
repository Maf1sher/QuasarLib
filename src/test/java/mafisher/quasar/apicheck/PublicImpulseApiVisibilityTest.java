package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.impulse.CameraShakeImpulse;
import mafisher.quasar.api.client.impulse.ClientEffectImpulses;
import mafisher.quasar.api.client.screen.ScreenEffectCameraState;
import mafisher.quasar.api.client.impulse.ScreenFlashImpulse;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import mafisher.quasar.testing.GlobalStateTest;

/** Compiles from outside the implementation package to pin the public impulse surface. */
@GlobalStateTest
class PublicImpulseApiVisibilityTest {
    @Test
    void exposesInverseMatricesAndImpulseEntryPoints() {
        ScreenEffectCameraState camera = new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), new Matrix4f(), 1, 1, 0.0F
        );
        CameraShakeImpulse shake = new CameraShakeImpulse(
            Vec3.ZERO, 32.0, 0.5F, 20, 4.0F, 5.0F
        );
        ScreenFlashImpulse flash = new ScreenFlashImpulse(
            Vec3.ZERO, 32.0, 0.5F, 20, 1.0F, 0.5F, 0.25F
        );

        assertNotNull(camera.inverseProjectionMatrix());
        assertNotNull(camera.inverseViewMatrix());
        assertNotNull(shake.angleOffsetsDegrees(Vec3.ZERO, 1.0F));
        assertTrue(flash.strengthAt(Vec3.ZERO, 0.0F) > 0.0F);

        ClientEffectImpulses.cameraShake(shake);
        ClientEffectImpulses.screenFlash(flash);
        ClientEffectImpulses.clear();
    }
}
