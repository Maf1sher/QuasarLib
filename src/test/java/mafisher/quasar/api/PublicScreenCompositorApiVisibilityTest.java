package mafisher.quasar.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import mafisher.quasar.client.effect.RegisterScreenEffectOperationsEvent;
import mafisher.quasar.client.effect.ScreenEffectCameraState;
import mafisher.quasar.client.effect.ScreenEffectOperation;
import mafisher.quasar.client.effect.ScreenEffectOperationContext;
import mafisher.quasar.client.effect.ScreenEffectOperationRenderer;
import mafisher.quasar.client.effect.ScreenEffectSettings;
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
        assertNotNull(RegisterScreenEffectOperationsEvent.class);
    }
}
