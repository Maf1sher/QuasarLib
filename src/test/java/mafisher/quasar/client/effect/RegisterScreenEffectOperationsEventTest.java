package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RegisterScreenEffectOperationsEventTest {
    private static final ScreenEffectOperation CUSTOM = ScreenEffectOperation.create(
        ResourceLocation.fromNamespaceAndPath("test", "event_operation")
    );

    @Test
    void registersThroughEvent() {
        ScreenEffectOperationRegistry registry = new ScreenEffectOperationRegistry();
        RegisterScreenEffectOperationsEvent event = new RegisterScreenEffectOperationsEvent(registry);

        event.register(CUSTOM, context -> { });

        assertTrue(registry.find(CUSTOM).isPresent());
    }

    @Test
    void surfacesDuplicateRegistration() {
        ScreenEffectOperationRegistry registry = new ScreenEffectOperationRegistry();
        RegisterScreenEffectOperationsEvent event = new RegisterScreenEffectOperationsEvent(registry);
        event.register(CUSTOM, context -> { });

        assertThrows(IllegalStateException.class, () -> event.register(CUSTOM, context -> { }));
    }

    @Test
    void surfacesFrozenRegistry() {
        ScreenEffectOperationRegistry registry = new ScreenEffectOperationRegistry();
        registry.freeze();
        RegisterScreenEffectOperationsEvent event = new RegisterScreenEffectOperationsEvent(registry);

        assertThrows(IllegalStateException.class, () -> event.register(CUSTOM, context -> { }));
    }
}
