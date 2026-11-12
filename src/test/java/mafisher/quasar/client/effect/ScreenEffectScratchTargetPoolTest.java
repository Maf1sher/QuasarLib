package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenEffectScratchTargetPoolTest {
    @Test
    void rejectsInvalidDimensions() {
        ScreenEffectScratchTargetPool pool = new ScreenEffectScratchTargetPool();

        assertThrows(IllegalArgumentException.class, () -> pool.acquire(0, 1));
        assertThrows(IllegalArgumentException.class, () -> pool.acquire(1, 0));
    }

    @Test
    void rejectsForeignTargetRelease() {
        ScreenEffectScratchTargetPool pool = new ScreenEffectScratchTargetPool();

        assertThrows(IllegalArgumentException.class, () -> pool.release(mock(TextureTarget.class)));
    }

    @Test
    void reusesATargetOfTheSameSize() {
        FakeTargets targets = new FakeTargets();
        targets.pool.beginFrame();
        TextureTarget first = targets.pool.acquire(64, 32);
        targets.pool.release(first);

        TextureTarget second = targets.pool.acquire(64, 32);

        assertSame(first, second);
        assertEquals(1, targets.created.size());
    }

    @Test
    void keepsATargetUsedInThePreviousFrame() {
        FakeTargets targets = new FakeTargets();
        targets.pool.beginFrame();
        TextureTarget first = targets.pool.acquire(64, 32);
        targets.pool.release(first);

        targets.pool.beginFrame();
        TextureTarget second = targets.pool.acquire(64, 32);

        assertSame(first, second);
        assertTrue(targets.destroyed.isEmpty());
    }

    @Test
    void destroysTargetsUnusedForMoreThanTheGraceFrame() {
        FakeTargets targets = new FakeTargets();
        targets.pool.beginFrame();
        TextureTarget stale = targets.pool.acquire(64, 32);
        targets.pool.release(stale);

        targets.pool.beginFrame();
        targets.pool.beginFrame();

        assertTrue(targets.destroyed.contains(stale));
    }

    @Test
    void releaseOperationMakesTargetsReusable() {
        FakeTargets targets = new FakeTargets();
        targets.pool.beginFrame();
        TextureTarget first = targets.pool.acquire(64, 32);

        targets.pool.releaseOperation();
        TextureTarget second = targets.pool.acquire(64, 32);

        assertSame(first, second);
    }

    @Test
    void releaseAllDestroysEveryEntryOnce() {
        FakeTargets targets = new FakeTargets();
        targets.pool.beginFrame();
        targets.pool.release(targets.pool.acquire(64, 32));
        targets.pool.release(targets.pool.acquire(32, 16));

        targets.pool.releaseAll();
        targets.pool.releaseAll();

        assertEquals(2, targets.destroyed.size());
    }

    @Test
    void evictionKeepsTheNewSizeAfterAQualityChange() {
        FakeTargets targets = new FakeTargets();
        targets.pool.beginFrame();
        TextureTarget oldSize = targets.pool.acquire(100, 100);
        targets.pool.release(oldSize);

        targets.pool.beginFrame();
        TextureTarget newSize = targets.pool.acquire(75, 75);
        targets.pool.release(newSize);

        targets.pool.beginFrame();
        assertTrue(targets.destroyed.contains(oldSize));
        assertFalse(targets.destroyed.contains(newSize));
        assertSame(newSize, targets.pool.acquire(75, 75));
    }

    private static final class FakeTargets {
        private final List<TextureTarget> created = new ArrayList<>();
        private final List<TextureTarget> destroyed = new ArrayList<>();
        private final ScreenEffectScratchTargetPool pool =
            new ScreenEffectScratchTargetPool(this::create, this.destroyed::add);

        private TextureTarget create(int width, int height) {
            TextureTarget target = mock(TextureTarget.class);
            this.created.add(target);
            return target;
        }
    }
}
