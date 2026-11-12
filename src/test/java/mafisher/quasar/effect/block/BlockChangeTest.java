package mafisher.quasar.effect.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import mafisher.quasar.api.block.BlockChange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class BlockChangeTest {
    @Test
    void storesPositionAndState() {
        BlockPos position = new BlockPos(1, 64, -3);
        BlockState state = mock(BlockState.class);

        BlockChange change = new BlockChange(position, state);

        assertEquals(position, change.position());
        assertEquals(state, change.state());
    }

    @Test
    void rejectsMissingFields() {
        BlockState state = mock(BlockState.class);

        assertThrows(NullPointerException.class, () -> new BlockChange(null, state));
        assertThrows(NullPointerException.class, () -> new BlockChange(BlockPos.ZERO, null));
    }
}
