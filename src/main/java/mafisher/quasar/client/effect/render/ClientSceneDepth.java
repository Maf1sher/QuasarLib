package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.Objects;
import java.util.function.Function;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.compat.SceneDepthFrame;
import mafisher.quasar.api.compat.SceneDepthSource;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Resolves one safe scene-depth frame for provider-depth occlusion.
 *
 * <p>The preference is the active provider frame, then the existing pre-translucent opaque capture
 * from screen visibility. A missing target, a missing provider projection, a shaderpack shadow
 * pass, or an external shaderpack depth source means "draw everything" rather than guessing.</p>
 */
@ApiStatus.Internal
public final class ClientSceneDepth {
    private ClientSceneDepth() {
    }

    /** Resolves the occlusion depth for the current frame, or {@code null} when no safe source exists. */
    public static @Nullable SceneDepthFrame resolve(RenderEnvironment environment,
        Matrix4f frameProjection, @Nullable RenderTarget opaqueCapture) {
        return resolve(environment, frameProjection, opaqueCapture, CompatRegistry::sceneDepthFrame);
    }

    static @Nullable SceneDepthFrame resolve(RenderEnvironment environment, Matrix4f frameProjection,
        @Nullable RenderTarget opaqueCapture,
        Function<Matrix4f, @Nullable SceneDepthFrame> providerResolver) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(frameProjection, "frameProjection");
        Objects.requireNonNull(providerResolver, "providerResolver");
        if (!SceneDepthFrame.isUsableProjection(frameProjection)
            || environment.shadowPass()
            || environment.shaderPackActive()
            || environment.depthSource() == SceneDepthSource.EXTERNAL_SHADERPACK) {
            return null;
        }
        Matrix4f frame = new Matrix4f(frameProjection);
        if (environment.hasDepthProvider()) {
            SceneDepthFrame provided;
            try {
                provided = providerResolver.apply(new Matrix4f(frame));
            } catch (RuntimeException exception) {
                provided = null;
            }
            if (provided != null && usableTarget(provided.target())) {
                return provided;
            }
        }
        if (usableTarget(opaqueCapture)) {
            return new SceneDepthFrame(opaqueCapture, SceneDepthSource.MAIN_TARGET, frame);
        }
        return null;
    }

    private static boolean usableTarget(@Nullable RenderTarget target) {
        return target != null && target.useDepth && target.width > 0 && target.height > 0
            && target.getDepthTextureId() > 0;
    }
}
