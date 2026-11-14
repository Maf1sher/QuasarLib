package mafisher.quasar.api.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class SceneDepthFrameTest {
    private static final Matrix4f PROJECTION = new Matrix4f()
        .perspective((float)Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1024.0F);

    @Test
    void copiesTheProjectionOnConstructionAndAccess() {
        Matrix4f source = new Matrix4f(PROJECTION);
        SceneDepthFrame frame = new SceneDepthFrame(new FakeTarget(), SceneDepthSource.HORIZON_LOD, source);

        source.identity();
        assertNotEquals(new Matrix4f(), frame.projection());

        Matrix4f returned = frame.projection();
        returned.identity();
        assertNotEquals(new Matrix4f(), frame.projection());
        assertEquals(SceneDepthSource.HORIZON_LOD, frame.source());
        assertTrue(frame.target() instanceof FakeTarget);
    }

    @Test
    void rejectsMissingOrUnusableInputs() {
        FakeTarget target = new FakeTarget();
        assertThrows(NullPointerException.class,
            () -> new SceneDepthFrame(null, SceneDepthSource.MAIN_TARGET, PROJECTION));
        assertThrows(NullPointerException.class,
            () -> new SceneDepthFrame(target, null, PROJECTION));
        assertThrows(NullPointerException.class,
            () -> new SceneDepthFrame(target, SceneDepthSource.MAIN_TARGET, null));
        assertThrows(IllegalArgumentException.class,
            () -> new SceneDepthFrame(target, SceneDepthSource.MAIN_TARGET, new Matrix4f().zero()));
    }

    @Test
    void detectsFiniteInvertibleProjections() {
        assertTrue(SceneDepthFrame.isUsableProjection(PROJECTION));
        assertFalse(SceneDepthFrame.isUsableProjection(new Matrix4f().zero()));
        assertFalse(SceneDepthFrame.isUsableProjection(null));

        Matrix4f nonFinite = new Matrix4f(PROJECTION);
        nonFinite.m00(Float.NaN);
        assertFalse(SceneDepthFrame.isUsableProjection(nonFinite));
    }

    private static final class FakeTarget extends RenderTarget {
        private FakeTarget() {
            super(true);
            this.width = 1920;
            this.height = 1080;
            this.depthBufferId = 1;
        }
    }
}
