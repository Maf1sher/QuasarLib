package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.List;
import mafisher.quasar.api.compat.FogPolicy;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.compat.SceneDepthFrame;
import mafisher.quasar.api.compat.SceneDepthSource;
import mafisher.quasar.api.compat.ShaderPackState;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ClientSceneDepthTest {
    private static final Matrix4f PROJECTION = new Matrix4f()
        .perspective((float)Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1024.0F);

    @Test
    void prefersTheProviderFrame() {
        FakeTarget providerTarget = new FakeTarget(true);
        FakeTarget opaqueTarget = new FakeTarget(true);
        SceneDepthFrame provided = new SceneDepthFrame(
            providerTarget, SceneDepthSource.HORIZON_LOD, PROJECTION);

        SceneDepthFrame result = ClientSceneDepth.resolve(
            environment(SceneDepthSource.HORIZON_LOD, ShaderPackState.INACTIVE),
            PROJECTION, opaqueTarget, ignored -> provided);

        assertSame(provided, result);
    }

    @Test
    void fallsBackToThePreTranslucentCaptureWithoutAProvider() {
        FakeTarget opaqueTarget = new FakeTarget(true);

        SceneDepthFrame result = ClientSceneDepth.resolve(
            environment(SceneDepthSource.MAIN_TARGET, ShaderPackState.INACTIVE),
            PROJECTION, opaqueTarget, ignored -> {
                throw new AssertionError("No provider should be queried");
            });

        assertSame(opaqueTarget, result.target());
        assertEquals(SceneDepthSource.MAIN_TARGET, result.source());
        assertEquals(PROJECTION, result.projection());
    }

    @Test
    void unsafeProviderTargetFallsBackToTheCapture() {
        FakeTarget providerTarget = new FakeTarget(false);
        FakeTarget opaqueTarget = new FakeTarget(true);
        SceneDepthFrame provided = new SceneDepthFrame(
            providerTarget, SceneDepthSource.HORIZON_LOD, PROJECTION);

        SceneDepthFrame result = ClientSceneDepth.resolve(
            environment(SceneDepthSource.HORIZON_LOD, ShaderPackState.INACTIVE),
            PROJECTION, opaqueTarget, ignored -> provided);

        assertSame(opaqueTarget, result.target());
    }

    @Test
    void missingProviderProjectionFallsBackOrDraws() {
        FakeTarget opaqueTarget = new FakeTarget(true);

        SceneDepthFrame fallback = ClientSceneDepth.resolve(
            environment(SceneDepthSource.HORIZON_LOD, ShaderPackState.INACTIVE),
            PROJECTION, opaqueTarget, ignored -> null);
        assertSame(opaqueTarget, fallback.target());

        assertNull(ClientSceneDepth.resolve(
            environment(SceneDepthSource.HORIZON_LOD, ShaderPackState.INACTIVE),
            PROJECTION, null, ignored -> null));
    }

    @Test
    void shadowPassAndExternalShaderpackDisableOcclusion() {
        FakeTarget opaqueTarget = new FakeTarget(true);

        assertNull(ClientSceneDepth.resolve(
            environment(SceneDepthSource.HORIZON_LOD, ShaderPackState.SHADOW_PASS),
            PROJECTION, opaqueTarget, ignored -> null));
        assertNull(ClientSceneDepth.resolve(
            environment(SceneDepthSource.EXTERNAL_SHADERPACK, ShaderPackState.ACTIVE),
            PROJECTION, opaqueTarget, ignored -> null));
        assertNull(ClientSceneDepth.resolve(
            environment(SceneDepthSource.MAIN_TARGET, ShaderPackState.ACTIVE),
            PROJECTION, opaqueTarget, ignored -> null));
    }

    @Test
    void invalidProjectionDisablesOcclusion() {
        FakeTarget opaqueTarget = new FakeTarget(true);

        assertNull(ClientSceneDepth.resolve(
            environment(SceneDepthSource.MAIN_TARGET, ShaderPackState.INACTIVE),
            new Matrix4f().zero(), opaqueTarget, ignored -> null));
    }

    private static RenderEnvironment environment(SceneDepthSource source, ShaderPackState shaderPack) {
        return new RenderEnvironment(256.0, 256.0, false, source, shaderPack,
            FogPolicy.RESPECT, false, false, List.of());
    }

    private static final class FakeTarget extends RenderTarget {
        private FakeTarget(boolean useDepth) {
            super(useDepth);
            this.width = 1920;
            this.height = 1080;
            this.depthBufferId = useDepth ? 7 : -1;
        }
    }
}
