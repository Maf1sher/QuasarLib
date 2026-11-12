package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.cinematic.CinematicEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.scale.SegmentedVisualEffectHandle;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class ContentCommandSessionsTest {
    private final ServerLevel level = mock(ServerLevel.class);

    @Test
    void stopsOnlyTrackedGameplayHandles() {
        GameplayEffectHandle first = new GameplayEffectHandle(UUID.randomUUID());
        GameplayEffectHandle second = new GameplayEffectHandle(UUID.randomUUID());
        GameplayEffectHandle untracked = new GameplayEffectHandle(UUID.randomUUID());
        ContentCommandSessions.track(this.level, first);
        ContentCommandSessions.track(this.level, second);
        List<GameplayEffectHandle> stopped = new ArrayList<>();

        int count = ContentCommandSessions.stopGameplay(
            this.level,
            (ignored, handle) -> stopped.add(handle)
        );

        assertEquals(2, count);
        assertTrue(stopped.contains(first));
        assertTrue(stopped.contains(second));
        assertFalse(stopped.contains(untracked));
        assertEquals(0, ContentCommandSessions.stopGameplay(this.level, (ignored, handle) -> { }));
    }

    @Test
    void stopsOnlyTrackedSegmentedHandles() {
        SegmentedVisualEffectHandle first = new SegmentedVisualEffectHandle(UUID.randomUUID());
        SegmentedVisualEffectHandle second = new SegmentedVisualEffectHandle(UUID.randomUUID());
        ContentCommandSessions.track(this.level, first);
        ContentCommandSessions.track(this.level, second);
        List<SegmentedVisualEffectHandle> stopped = new ArrayList<>();

        int count = ContentCommandSessions.stopSegmented(
            this.level,
            (ignored, handle) -> stopped.add(handle)
        );

        assertEquals(2, count);
        assertTrue(stopped.contains(first));
        assertTrue(stopped.contains(second));
    }

    @Test
    void stopsOnlyTrackedCinematicHandles() {
        CinematicEffectHandle first = new CinematicEffectHandle(UUID.randomUUID());
        CinematicEffectHandle second = new CinematicEffectHandle(UUID.randomUUID());
        ContentCommandSessions.track(this.level, first);
        ContentCommandSessions.track(this.level, second);
        List<CinematicEffectHandle> stopped = new ArrayList<>();

        int count = ContentCommandSessions.stopCinematic(
            this.level,
            (ignored, handle) -> stopped.add(handle)
        );

        assertEquals(2, count);
        assertTrue(stopped.contains(first));
        assertTrue(stopped.contains(second));
    }

    @Test
    void clearDropsEveryTrackedKind() {
        ContentCommandSessions.track(this.level, new GameplayEffectHandle(UUID.randomUUID()));
        ContentCommandSessions.track(this.level, new SegmentedVisualEffectHandle(UUID.randomUUID()));
        ContentCommandSessions.track(this.level, new CinematicEffectHandle(UUID.randomUUID()));

        ContentCommandSessions.clear(this.level);

        assertEquals(0, ContentCommandSessions.stopGameplay(this.level, (ignored, handle) -> { }));
        assertEquals(0, ContentCommandSessions.stopSegmented(this.level, (ignored, handle) -> { }));
        assertEquals(0, ContentCommandSessions.stopCinematic(this.level, (ignored, handle) -> { }));
    }
}
