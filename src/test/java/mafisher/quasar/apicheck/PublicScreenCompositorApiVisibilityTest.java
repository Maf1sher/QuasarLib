package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import mafisher.quasar.api.client.screen.EmissiveComposite;
import mafisher.quasar.api.client.screen.RegisterScreenEffectOperationsEvent;
import mafisher.quasar.api.client.screen.ScreenEffectCameraState;
import mafisher.quasar.api.client.screen.ScreenEffectInputs;
import mafisher.quasar.api.client.screen.ScreenEffectOperation;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.client.screen.ScreenEffectOperationRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectSettings;
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
        assertNotNull(EmissiveComposite.STANDARD_SHADER);
        assertNotNull(EmissiveComposite.builder().build());
    }
}
