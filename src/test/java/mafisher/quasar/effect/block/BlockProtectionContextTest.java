package mafisher.quasar.effect.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.block.BlockProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class BlockProtectionContextTest {
    private static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "protected_effect");

    @Test
    void storesChangeMetadataAndAllowsAMissingOwner() {
        ServerLevel level = mock(ServerLevel.class);
        BlockChange change = new BlockChange(new BlockPos(1, 64, -2), mock(BlockState.class));
        BlockState current = mock(BlockState.class);
        UUID sessionId = UUID.randomUUID();

        BlockProtectionContext context = new BlockProtectionContext(
            level, change, current, TYPE, sessionId, null
        );

        assertEquals(level, context.level());
        assertEquals(change, context.change());
        assertEquals(change.position(), context.position());
        assertEquals(change.state(), context.newState());
        assertEquals(current, context.currentState());
        assertEquals(TYPE, context.effectTypeId());
        assertEquals(sessionId, context.effectSessionId());
        assertNull(context.ownerId());
    }

    @Test
    void rejectsMissingRequiredFields() {
        ServerLevel level = mock(ServerLevel.class);
        BlockChange change = new BlockChange(BlockPos.ZERO, mock(BlockState.class));
        BlockState current = mock(BlockState.class);
        UUID sessionId = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> new BlockProtectionContext(
            null, change, current, TYPE, sessionId, null
        ));
        assertThrows(NullPointerException.class, () -> new BlockProtectionContext(
            level, null, current, TYPE, sessionId, null
        ));
        assertThrows(NullPointerException.class, () -> new BlockProtectionContext(
            level, change, null, TYPE, sessionId, null
        ));
        assertThrows(NullPointerException.class, () -> new BlockProtectionContext(
            level, change, current, null, sessionId, null
        ));
        assertThrows(NullPointerException.class, () -> new BlockProtectionContext(
            level, change, current, TYPE, null, null
        ));
    }
}
