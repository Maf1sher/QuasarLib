package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;

/**
 * GL state helpers shared by the screen compositor and other screen passes.
 *
 * <p>Screen composition binds render targets and sets viewports outside the
 * vanilla render-type pipeline. Binding through this helper keeps those binds
 * independent of mod-specific render-target wrappers and of Sodium's cached
 * {@code GlStateManager} viewport.</p>
 */
@ApiStatus.Internal
public final class EffectRenderState {
    private static final int FRAMEBUFFER = 0x8D40;

    private EffectRenderState() {
    }

    /** Binds {@code target}'s framebuffer and sets the viewport to its full size. */
    public static void bindTarget(RenderTarget target) {
        Objects.requireNonNull(target, "target");
        GlStateManager._glBindFramebuffer(FRAMEBUFFER, target.frameBufferId);
        forceViewport(target.width, target.height);
    }

    /**
     * Sets the GL viewport and guarantees that a real {@code glViewport} call
     * reaches the driver.
     *
     * <p>Sodium 0.8.11 and newer cache the last
     * {@code GlStateManager#_viewport} call and skip a duplicate
     * {@code glViewport}. If another mod changes the viewport through raw GL,
     * Sodium's cache becomes stale and a matching call can be skipped entirely.
     * Bouncing through a different height makes the final call differ from any
     * cached value, so both the driver state and Sodium's cache end up correct.
     * This is one path for every mod; no Sodium-specific branch is needed.</p>
     */
    static void forceViewport(int width, int height) {
        forceViewport(0, 0, width, height);
    }

    /** Forces a full viewport rectangle through the same Sodium-cache-safe path. */
    public static void forceViewport(int x, int y, int width, int height) {
        forceViewport(x, y, width, height, GlStateManager::_viewport);
    }

    /**
     * Forces the viewport through two valid rectangles. At least one of the two calls differs from any cached
     * viewport, so Sodium's duplicate suppression cannot leave a stale driver viewport. Both sizes stay positive;
     * the temporary rectangle is clipped by the driver and is immediately replaced by the requested one.
     */
    static void forceViewport(int x, int y, int width, int height, ViewportSink sink) {
        Objects.requireNonNull(sink, "sink");
        if (width <= 0 || height <= 0) {
            return;
        }
        int probeHeight = height == Integer.MAX_VALUE ? height - 1 : height + 1;
        sink.viewport(x, y, width, probeHeight);
        sink.viewport(x, y, width, height);
    }

    /** Internal boundary around the viewport call; production passes {@code GlStateManager::_viewport}. */
    @FunctionalInterface
    interface ViewportSink {
        void viewport(int x, int y, int width, int height);
    }
}
