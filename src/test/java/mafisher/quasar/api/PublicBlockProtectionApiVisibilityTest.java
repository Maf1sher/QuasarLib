package mafisher.quasar.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.block.BlockProtection;
import mafisher.quasar.effect.block.BlockProtectionContext;
import mafisher.quasar.effect.block.BlockProtectionService;
import mafisher.quasar.effect.block.RegisterBlockProtectionsEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public protection surface. */
class PublicBlockProtectionApiVisibilityTest {
    @Test
    void exposesTheBlockProtectionContract() {
        BlockChange change = new BlockChange(BlockPos.ZERO, mock(BlockState.class));
        BlockProtectionContext context = new BlockProtectionContext(
            mock(ServerLevel.class),
            change,
            mock(BlockState.class),
            ResourceLocation.fromNamespaceAndPath("test", "protected_effect"),
            UUID.randomUUID(),
            null
        );
        BlockProtection protection = ignored -> true;

        assertTrue(protection.allows(context));
        assertTrue(BlockProtectionService.allows(context));
        assertNotNull(RegisterBlockProtectionsEvent.class);
        assertEquals(6, BlockProtectionContext.class.getRecordComponents().length);
        assertEquals(BlockPos.ZERO, context.position());
        assertEquals(change.state(), context.newState());
    }
}
