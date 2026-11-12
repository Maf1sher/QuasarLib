package mafisher.quasar.client.effect.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Objects;

/** Copies a full render target with a nearest-filter blit and restores the surrounding GL state. */
final class ScreenTargetBlit {
    private static final int READ_FRAMEBUFFER = 0x8CA8;
    private static final int DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int COLOR_BUFFER_BIT = 0x00004000;
    private static final int DEPTH_BUFFER_BIT = 0x00000100;
    private static final int NEAREST_FILTER = 0x2600;

    private ScreenTargetBlit() {
    }

    /** Copies color from {@code source} into {@code destination}. */
    static void color(RenderTarget source, RenderTarget destination) {
        blit(source, destination, COLOR_BUFFER_BIT);
    }

    /** Copies depth from {@code source} into {@code destination}. */
    static void depth(RenderTarget source, RenderTarget destination) {
        blit(source, destination, DEPTH_BUFFER_BIT);
    }

    private static void blit(RenderTarget source, RenderTarget destination, int mask) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (source.width <= 0 || source.height <= 0 || destination.width <= 0 || destination.height <= 0) {
            return;
        }
        ScreenGlState state = ScreenGlState.capture();
        try {
            RenderSystem.disableScissor();
            GlStateManager._glBindFramebuffer(READ_FRAMEBUFFER, source.frameBufferId);
            GlStateManager._glBindFramebuffer(DRAW_FRAMEBUFFER, destination.frameBufferId);
            GlStateManager._glBlitFrameBuffer(
                0, 0, source.width, source.height,
                0, 0, destination.width, destination.height,
                mask, NEAREST_FILTER
            );
        } finally {
            state.restore();
        }
    }
}
