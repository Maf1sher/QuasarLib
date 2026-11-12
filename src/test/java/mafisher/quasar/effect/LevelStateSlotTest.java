package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LevelStateSlotTest {
    private final LevelStateSlot<State> first = LevelStateSlot.create(State::new);
    private final LevelStateSlot<State> second = LevelStateSlot.create(State::new);
    private final ServerLevel level = mock(ServerLevel.class);

    @AfterEach
    void tearDown() {
        when(this.level.getServer()).thenReturn(null);
        LevelStateSlot.clearLevel(this.level);
    }

    @Test
    void peekDoesNotCreateState() {
        assertNull(this.first.peek(this.level));
        assertFalse(LevelStateSlot.isTracked(this.level));

        assertNotNull(this.first.get(this.level));
        assertTrue(LevelStateSlot.isTracked(this.level));
    }

    @Test
    void clearLevelDropsEverySlot() {
        this.first.get(this.level).value = "first";
        this.second.get(this.level).value = "second";

        LevelStateSlot.clearLevel(this.level);

        assertNull(this.first.peek(this.level));
        assertNull(this.second.peek(this.level));
        assertFalse(LevelStateSlot.isTracked(this.level));
    }

    @Test
    void levelAwareFactoryReceivesTheOwningLevel() {
        LevelStateSlot<ServerLevel> slot = LevelStateSlot.create(level -> level);

        assertSame(this.level, slot.get(this.level));
    }

    @Test
    void setBindsAnExistingValueWithoutCallingTheFactory() {
        LevelStateSlot<State> slot = LevelStateSlot.create(() -> {
            throw new AssertionError("factory should not run");
        });
        State state = new State();

        slot.set(this.level, state);

        assertSame(state, slot.get(this.level));
    }

    @Test
    void rejectsAccessFromAnotherThread() {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getRunningThread()).thenReturn(new Thread("effect-test"));
        when(this.level.getServer()).thenReturn(server);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);

        assertThrows(IllegalStateException.class, () -> this.first.get(this.level));
    }

    private static final class State {
        private String value;
    }
}
