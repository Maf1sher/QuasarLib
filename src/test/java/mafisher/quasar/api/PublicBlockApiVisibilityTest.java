package mafisher.quasar.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.gameplay.EffectTickBudget;
import mafisher.quasar.effect.gameplay.EffectTickCounters;
import mafisher.quasar.effect.gameplay.GameplayEffectContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public block-change surface. */
class PublicBlockApiVisibilityTest {
    @Test
    void exposesBlockChangesAndBudgetComponents() {
        BlockPos position = new BlockPos(1, 64, 2);
        BlockState state = mock(BlockState.class);

        BlockChange change = new BlockChange(position, state);

        assertEquals(position, change.position());
        assertEquals(state, change.state());
        assertEquals(4, EffectTickBudget.class.getRecordComponents().length);
        assertEquals(6, EffectTickCounters.class.getRecordComponents().length);
    }

    @Test
    void keepsSourceCompatibleBudgetConstructors() throws NoSuchMethodException {
        assertNotNull(EffectTickBudget.class.getConstructor(int.class, int.class, int.class));
        assertNotNull(EffectTickCounters.class.getConstructor(int.class, int.class, int.class, int.class));
    }

    @Test
    void exposesContextBlockMethods() throws NoSuchMethodException {
        assertNotNull(GameplayEffectContext.class.getMethod(
            "queueBlockChange", BlockPos.class, BlockState.class
        ));
        assertNotNull(GameplayEffectContext.class.getMethod("queueBlockChange", BlockChange.class));
        assertNotNull(GameplayEffectContext.class.getMethod("capabilities"));
        assertNotNull(GameplayEffectContext.class.getMethod("pendingBlockChanges"));
    }
}
