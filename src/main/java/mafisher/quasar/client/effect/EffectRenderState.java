package mafisher.quasar.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Objects;

/**
 * GL state helpers shared by the screen compositor and other screen passes.
 *
 * <p>Screen composition binds render targets and sets viewports outside the
 * vanilla render-type pipeline. Binding through this helper keeps those binds
 * independent of mod-specific render-target wrappers and of Sodium's cached
 * {@code GlStateManager} viewport.</p>
 */
final class EffectRenderState {
    private static final int FRAMEBUFFER = 0x8D40;

    private EffectRenderState() {
    }

    /** Binds {@code target}'s framebuffer and sets the viewport to its full size. */
    static void bindTarget(RenderTarget target) {
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
    static void forceViewport(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        GlStateManager._viewport(x, y, width, 0);
        GlStateManager._viewport(x, y, width, height);
    }
}
