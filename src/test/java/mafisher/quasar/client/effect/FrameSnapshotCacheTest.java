package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameSnapshotCacheTest {
    @Test
    void buildsOncePerRenderTick() {
        FrameSnapshotCache cache = new FrameSnapshotCache();

        assertTrue(cache.shouldBuild(10));
        assertFalse(cache.shouldBuild(10));
        assertEquals(10, cache.tick());

        assertTrue(cache.shouldBuild(11));
        assertFalse(cache.shouldBuild(11));
    }

    @Test
    void invalidateForcesTheNextBuildForTheSameTick() {
        FrameSnapshotCache cache = new FrameSnapshotCache();
        assertTrue(cache.shouldBuild(10));
        assertFalse(cache.shouldBuild(10));

        cache.invalidate();

        assertTrue(cache.shouldBuild(10));
        assertFalse(cache.shouldBuild(10));
    }
}
