package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.neoforge.client.GlStateBackup;
import org.junit.jupiter.api.Test;

class ScreenGlStateTest {
    @Test
    void restoresCachedStateFramebuffersAndViewport() {
        FakeGlState state = new FakeGlState();
        state.backup.blendEnabled = true;
        state.backup.blendSrcRgb = 1;
        state.backup.blendDestRgb = 2;
        state.backup.depthEnabled = true;
        state.backup.depthMask = false;
        state.backup.cullEnabled = true;
        state.backup.scissorEnabled = true;
        state.backup.colorMaskRed = false;
        state.drawFramebuffer = 11;
        state.readFramebuffer = 12;
        state.viewport = new int[]{3, 4, 5, 6};

        ScreenGlState snapshot = ScreenGlState.capture(state);

        state.backup.blendEnabled = false;
        state.backup.depthEnabled = false;
        state.backup.cullEnabled = false;
        state.backup.scissorEnabled = false;
        state.backup.colorMaskRed = true;
        state.drawFramebuffer = 21;
        state.readFramebuffer = 22;
        state.viewport = new int[]{7, 8, 9, 10};

        snapshot.restore(state);

        assertTrue(state.backup.blendEnabled);
        assertEquals(1, state.backup.blendSrcRgb);
        assertEquals(2, state.backup.blendDestRgb);
        assertTrue(state.backup.depthEnabled);
        assertFalse(state.backup.depthMask);
        assertTrue(state.backup.cullEnabled);
        assertTrue(state.backup.scissorEnabled);
        assertFalse(state.backup.colorMaskRed);
        assertEquals(11, state.drawFramebuffer);
        assertEquals(12, state.readFramebuffer);
        assertArrayEquals(new int[]{3, 4, 5, 6}, state.viewport);
    }

    @Test
    void nestedSnapshotsRestoreInLifoOrder() {
        FakeGlState state = new FakeGlState();
        state.drawFramebuffer = 10;
        state.viewport = new int[]{0, 0, 100, 100};
        ScreenGlState first = ScreenGlState.capture(state);

        state.backup.blendEnabled = true;
        state.drawFramebuffer = 20;
        state.viewport = new int[]{0, 0, 50, 50};
        ScreenGlState second = ScreenGlState.capture(state);

        state.backup.blendEnabled = false;
        state.drawFramebuffer = 30;
        state.viewport = new int[]{0, 0, 25, 25};

        second.restore(state);
        assertTrue(state.backup.blendEnabled);
        assertEquals(20, state.drawFramebuffer);
        assertArrayEquals(new int[]{0, 0, 50, 50}, state.viewport);

        first.restore(state);
        assertFalse(state.backup.blendEnabled);
        assertEquals(10, state.drawFramebuffer);
        assertArrayEquals(new int[]{0, 0, 100, 100}, state.viewport);
    }

    @Test
    void rejectsAnIncompleteViewport() {
        FakeGlState state = new FakeGlState();
        state.viewport = new int[]{0, 0, 1};

        assertThrows(IllegalArgumentException.class, () -> ScreenGlState.capture(state));
    }

    private static final class FakeGlState implements ScreenGlState.GlStateAccess {
        private GlStateBackup backup = new GlStateBackup();
        private int drawFramebuffer;
        private int readFramebuffer;
        private int[] viewport = new int[4];

        @Override
        public GlStateBackup backup() {
            return copy(this.backup);
        }

        @Override
        public int drawFramebuffer() {
            return this.drawFramebuffer;
        }

        @Override
        public int readFramebuffer() {
            return this.readFramebuffer;
        }

        @Override
        public int[] viewport() {
            return this.viewport.clone();
        }

        @Override
        public void restore(GlStateBackup state) {
            this.backup = copy(state);
        }

        @Override
        public void bindFramebuffer(int target, int framebuffer) {
            if (target == org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER) {
                this.drawFramebuffer = framebuffer;
            } else if (target == org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER) {
                this.readFramebuffer = framebuffer;
            }
        }

        @Override
        public void viewport(int[] viewport) {
            this.viewport = viewport.clone();
        }

        private static GlStateBackup copy(GlStateBackup source) {
            GlStateBackup copy = new GlStateBackup();
            copy.blendEnabled = source.blendEnabled;
            copy.blendSrcRgb = source.blendSrcRgb;
            copy.blendDestRgb = source.blendDestRgb;
            copy.blendSrcAlpha = source.blendSrcAlpha;
            copy.blendDestAlpha = source.blendDestAlpha;
            copy.depthEnabled = source.depthEnabled;
            copy.depthMask = source.depthMask;
            copy.depthFunc = source.depthFunc;
            copy.cullEnabled = source.cullEnabled;
            copy.polyOffsetFillEnabled = source.polyOffsetFillEnabled;
            copy.polyOffsetLineEnabled = source.polyOffsetLineEnabled;
            copy.polyOffsetFactor = source.polyOffsetFactor;
            copy.polyOffsetUnits = source.polyOffsetUnits;
            copy.colorLogicEnabled = source.colorLogicEnabled;
            copy.colorLogicOp = source.colorLogicOp;
            copy.stencilFuncFunc = source.stencilFuncFunc;
            copy.stencilFuncRef = source.stencilFuncRef;
            copy.stencilFuncMask = source.stencilFuncMask;
            copy.stencilMask = source.stencilMask;
            copy.stencilFail = source.stencilFail;
            copy.stencilZFail = source.stencilZFail;
            copy.stencilZPass = source.stencilZPass;
            copy.scissorEnabled = source.scissorEnabled;
            copy.colorMaskRed = source.colorMaskRed;
            copy.colorMaskGreen = source.colorMaskGreen;
            copy.colorMaskBlue = source.colorMaskBlue;
            copy.colorMaskAlpha = source.colorMaskAlpha;
            return copy;
        }
    }
}
