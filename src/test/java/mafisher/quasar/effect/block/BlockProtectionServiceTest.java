package mafisher.quasar.effect.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.block.BlockProtectionContext;
import mafisher.quasar.api.block.RegisterBlockProtectionsEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockProtectionServiceTest {
    private static final ResourceLocation PROTECTION =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_protection");
    private static final ResourceLocation SECOND_PROTECTION =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "second_protection");

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        BlockProtectionService.clearForTesting();
    }

    @Test
    void allowsWhenNoProtectionsAreRegistered() {
        assertTrue(BlockProtectionService.allows(context()));
    }

    @Test
    void requiresEveryProtectionToAllow() {
        BlockProtectionService.register(PROTECTION, ignored -> true);
        BlockProtectionService.register(SECOND_PROTECTION, ignored -> false);

        assertFalse(BlockProtectionService.allows(context()));
    }

    @Test
    void vetoesWhenAProtectionThrows() {
        BlockProtectionService.register(PROTECTION, ignored -> {
            throw new RuntimeException("test failure");
        });

        assertFalse(BlockProtectionService.allows(context()));
    }

    @Test
    void rejectsDuplicateIds() {
        BlockProtectionService.register(PROTECTION, ignored -> true);

        assertThrows(
            IllegalStateException.class,
            () -> BlockProtectionService.register(PROTECTION, ignored -> true)
        );
    }

    @Test
    void freezesRegistration() {
        BlockProtectionService.freeze();

        assertThrows(
            IllegalStateException.class,
            () -> BlockProtectionService.register(PROTECTION, ignored -> true)
        );
    }

    @Test
    void registersThroughTheEvent() {
        new RegisterBlockProtectionsEvent().register(PROTECTION, ignored -> false);

        assertEquals(1, BlockProtectionService.size());
        assertFalse(BlockProtectionService.allows(context()));
    }

    private static BlockProtectionContext context() {
        return new BlockProtectionContext(
            mock(ServerLevel.class),
            new BlockChange(BlockPos.ZERO, mock(BlockState.class)),
            mock(BlockState.class),
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "protected_effect"),
            UUID.randomUUID(),
            null
        );
    }
}
