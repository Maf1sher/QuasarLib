package mafisher.quasar.client.effect;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Objects;
import net.neoforged.neoforge.client.GlStateBackup;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Captures the presentation GL state that screen composition changes and restores it as one unit.
 *
 * <p>{@link GlStateBackup} covers cached enable flags, blend factors, depth, cull, stencil, polygon
 * offset, scissor enable, and color mask. Framebuffer bindings and the viewport are read from GL
 * because NeoForge's backup does not include them.</p>
 */
final class ScreenGlState {
    private final GlStateBackup backup;
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int[] viewport;

    private ScreenGlState(GlStateBackup backup, int drawFramebuffer, int readFramebuffer, int[] viewport) {
        this.backup = Objects.requireNonNull(backup, "backup");
        this.drawFramebuffer = drawFramebuffer;
        this.readFramebuffer = readFramebuffer;
        this.viewport = Objects.requireNonNull(viewport, "viewport").clone();
        if (this.viewport.length < 4) {
            throw new IllegalArgumentException("Viewport must contain four components");
        }
    }

    static ScreenGlState capture() {
        return capture(GlStateAccess.INSTANCE);
    }

    static ScreenGlState capture(GlStateAccess access) {
        Objects.requireNonNull(access, "access");
        return new ScreenGlState(access.backup(), access.drawFramebuffer(), access.readFramebuffer(),
            access.viewport());
    }

    void restore() {
        restore(GlStateAccess.INSTANCE);
    }

    void restore(GlStateAccess access) {
        Objects.requireNonNull(access, "access");
        access.restore(this.backup);
        access.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
        access.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.readFramebuffer);
        access.viewport(this.viewport);
    }

    /** Testable boundary around the GL calls used by a {@link ScreenGlState} snapshot. */
    interface GlStateAccess {
        GlStateAccess INSTANCE = new GlStateAccess() {
            @Override
            public GlStateBackup backup() {
                GlStateBackup state = new GlStateBackup();
                GlStateManager._backupGlState(state);
                return state;
            }

            @Override
            public int drawFramebuffer() {
                return GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            }

            @Override
            public int readFramebuffer() {
                return GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            }

            @Override
            public int[] viewport() {
                int[] viewport = new int[4];
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                return viewport;
            }

            @Override
            public void restore(GlStateBackup state) {
                GlStateManager._restoreGlState(state);
            }

            @Override
            public void bindFramebuffer(int target, int framebuffer) {
                GlStateManager._glBindFramebuffer(target, framebuffer);
            }

            @Override
            public void viewport(int[] viewport) {
                EffectRenderState.forceViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            }
        };

        GlStateBackup backup();

        int drawFramebuffer();

        int readFramebuffer();

        int[] viewport();

        void restore(GlStateBackup state);

        void bindFramebuffer(int target, int framebuffer);

        void viewport(int[] viewport);
    }
}
