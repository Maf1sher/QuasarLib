package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import mafisher.quasar.api.client.RegisterScreenEffectOperationsEvent;
import mafisher.quasar.api.client.ScreenEffectCameraState;
import mafisher.quasar.api.client.ScreenEffectInputs;
import mafisher.quasar.api.client.ScreenEffectOperation;
import mafisher.quasar.api.client.ScreenEffectOperationContext;
import mafisher.quasar.api.client.ScreenEffectOperationRenderer;
import mafisher.quasar.api.client.ScreenEffectSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public screen-operation surface. */
class PublicScreenCompositorApiVisibilityTest {
    @Test
    void exposesOperationRegistriesAndSettings() {
        ScreenEffectOperation operation = ScreenEffectOperation.create(
            ResourceLocation.fromNamespaceAndPath("test", "screen_operation")
        );
        ScreenEffectSettings settings = ScreenEffectSettings.custom(operation, new Object());
        ScreenEffectCameraState camera = new ScreenEffectCameraState(
            Vec3.ZERO, new Matrix4f(), new Matrix4f(), 1, 1, 0.0F
        );

        assertNotNull(operation.id());
        assertNotNull(settings.settings(Object.class));
        assertNotNull(camera.modelViewMatrix());
        assertNotNull(ScreenEffectOperationContext.class);
        assertNotNull(ScreenEffectOperationRenderer.class);
        assertNotNull(ScreenEffectInputs.ALL);
        assertNotNull(RegisterScreenEffectOperationsEvent.class);
    }
}
