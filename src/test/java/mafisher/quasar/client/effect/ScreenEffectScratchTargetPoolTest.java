package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.pipeline.TextureTarget;
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
}
