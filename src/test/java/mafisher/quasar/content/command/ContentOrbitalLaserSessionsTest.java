package mafisher.quasar.content.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.VisualEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserSessionsTest {
    private final ServerLevel level = mock(ServerLevel.class);

    @Test
    void stopsOnlyTrackedSessions() {
        GameplayEffectHandle first = new GameplayEffectHandle(UUID.randomUUID());
        GameplayEffectHandle second = new GameplayEffectHandle(UUID.randomUUID());
        GameplayEffectHandle otherGameplay = new GameplayEffectHandle(UUID.randomUUID());
        ContentOrbitalLaserSessions.trackSession(this.level, first);
        ContentOrbitalLaserSessions.trackSession(this.level, second);
        ContentCommandSessions.track(this.level, otherGameplay);
        List<GameplayEffectHandle> stopped = new ArrayList<>();

        int count = ContentOrbitalLaserSessions.stopSessions(
            this.level,
            (ignored, handle) -> stopped.add(handle)
        );

        assertEquals(2, count);
        assertTrue(stopped.contains(first));
        assertTrue(stopped.contains(second));
        assertFalse(stopped.contains(otherGameplay));
        assertEquals(
            1,
            ContentCommandSessions.stopGameplay(this.level, (ignored, handle) -> { })
        );
    }

    @Test
    void stopsOnlyTrackedPreviews() {
        VisualEffectHandle first = new VisualEffectHandle(UUID.randomUUID());
        VisualEffectHandle second = new VisualEffectHandle(UUID.randomUUID());
        ContentOrbitalLaserSessions.trackPreview(this.level, first);
        ContentOrbitalLaserSessions.trackPreview(this.level, second);
        List<VisualEffectHandle> stopped = new ArrayList<>();

        int count = ContentOrbitalLaserSessions.stopPreviews(
            this.level,
            (ignored, handle) -> stopped.add(handle)
        );

        assertEquals(2, count);
        assertTrue(stopped.contains(first));
        assertTrue(stopped.contains(second));
        assertEquals(
            0,
            ContentOrbitalLaserSessions.stopPreviews(this.level, (ignored, handle) -> { })
        );
    }

    @Test
    void clearDropsOrbitalSessionsAndPreviews() {
        ContentOrbitalLaserSessions.trackSession(
            this.level, new GameplayEffectHandle(UUID.randomUUID())
        );
        ContentOrbitalLaserSessions.trackPreview(
            this.level, new VisualEffectHandle(UUID.randomUUID())
        );

        ContentCommandSessions.clear(this.level);

        assertEquals(
            0,
            ContentOrbitalLaserSessions.stopSessions(this.level, (ignored, handle) -> { })
        );
        assertEquals(
            0,
            ContentOrbitalLaserSessions.stopPreviews(this.level, (ignored, handle) -> { })
        );
    }
}
