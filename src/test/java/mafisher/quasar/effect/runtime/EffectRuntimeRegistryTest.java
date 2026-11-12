package mafisher.quasar.effect.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EffectRuntimeRegistryTest {
    private final ServerLevel level = mock(ServerLevel.class);
    private final ServerLevel other = mock(ServerLevel.class);

    @AfterEach
    void tearDown() {
        EffectRuntimeRegistry.close(this.level);
        EffectRuntimeRegistry.close(this.other);
    }

    @Test
    void createsOneRuntimePerLevel() {
        LevelEffectRuntime first = EffectRuntimeRegistry.get(this.level);
        LevelEffectRuntime second = EffectRuntimeRegistry.get(this.level);
        LevelEffectRuntime otherLevel = EffectRuntimeRegistry.get(this.other);

        assertSame(first, second);
        assertNotSame(first, otherLevel);
    }

    @Test
    void installsAndDetachesRuntime() {
        LevelEffectRuntime runtime = TestEffectRuntime.install(this.level, new RecordingPacketSink());

        assertSame(runtime, EffectRuntimeRegistry.get(this.level));

        EffectRuntimeRegistry.close(this.level);

        assertNull(EffectRuntimeRegistry.peek(this.level));
    }

    @Test
    void rejectsDuplicateInstall() {
        LevelEffectRuntime runtime = TestEffectRuntime.install(this.level);

        assertThrows(
            IllegalStateException.class,
            () -> EffectRuntimeRegistry.install(this.level, runtime)
        );
    }

    @Test
    void rejectsRuntimeFromAnotherLevel() {
        LevelEffectRuntime runtime = TestEffectRuntime.install(this.level);

        assertThrows(
            IllegalArgumentException.class,
            () -> EffectRuntimeRegistry.install(this.other, runtime)
        );
    }

    @Test
    void resetDropsRuntime() {
        TestEffectRuntime.install(this.level);

        EffectRuntimeRegistry.reset(this.level);

        assertNull(EffectRuntimeRegistry.peek(this.level));
    }

    @Test
    void closeIsIdempotent() {
        TestEffectRuntime.install(this.level);

        EffectRuntimeRegistry.close(this.level);

        assertDoesNotThrow(() -> EffectRuntimeRegistry.close(this.level));
        assertNull(EffectRuntimeRegistry.peek(this.level));
    }
}
