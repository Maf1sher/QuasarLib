package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class GameplayEffectTypeRegistryTest {
    private static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_gameplay_type");
    private static final ResourceLocation SECOND_TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "second_gameplay_type");

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        GameplayEffectTypeRegistry.clearForTesting();
    }

    @Test
    void rejectsDuplicateIds() {
        GameplayEffectTypeRegistry.register(TYPE, descriptor -> null);

        assertThrows(
            IllegalStateException.class,
            () -> GameplayEffectTypeRegistry.register(TYPE, descriptor -> null)
        );
    }

    @Test
    void freezesRegistrationAfterSetup() {
        GameplayEffectTypeRegistry.freeze();

        assertThrows(
            IllegalStateException.class,
            () -> GameplayEffectTypeRegistry.register(SECOND_TYPE, descriptor -> null)
        );
    }
}
