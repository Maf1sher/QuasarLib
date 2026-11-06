package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ScreenEffectOperationRegistryTest {
    private static final ScreenEffectOperation CUSTOM = ScreenEffectOperation.create(
        ResourceLocation.fromNamespaceAndPath("test", "custom_operation")
    );

    @Test
    void registersBuiltIns() {
        ScreenEffectOperationRegistry registry = new ScreenEffectOperationRegistry();
        registry.registerBuiltIns();

        assertTrue(registry.find(ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW).isPresent());
        assertTrue(registry.find(ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION).isPresent());
        assertTrue(registry.find(ScreenEffectOperation.MASK_SCOPED_SCENE_BLUR).isPresent());
        assertTrue(registry.find(ScreenEffectOperation.MASK_SCOPED_BLOOM).isPresent());
        assertTrue(registry.find(ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE).isPresent());
    }

    @Test
    void registersCustomOperation() {
        ScreenEffectOperationRegistry registry = new ScreenEffectOperationRegistry();

        registry.register(CUSTOM, context -> { });

        assertTrue(registry.find(CUSTOM).isPresent());
    }

    @Test
    void rejectsDuplicateRegistration() {
        ScreenEffectOperationRegistry registry = new ScreenEffectOperationRegistry();
        registry.register(CUSTOM, context -> { });

        assertThrows(IllegalStateException.class, () -> registry.register(CUSTOM, context -> { }));
    }

    @Test
    void rejectsRegistrationAfterFreeze() {
        ScreenEffectOperationRegistry registry = new ScreenEffectOperationRegistry();
        registry.freeze();

        assertThrows(IllegalStateException.class, () -> registry.register(CUSTOM, context -> { }));
    }

    @Test
    void returnsEmptyForUnknownOperation() {
        ScreenEffectOperationRegistry registry = new ScreenEffectOperationRegistry();

        assertFalse(registry.find(CUSTOM).isPresent());
    }
}
