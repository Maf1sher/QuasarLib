package mafisher.quasar.api.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.Objects;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * One resolved provider-depth target for the current frame.
 *
 * <p>{@code projection} is the projection that produced {@code target} together with the current
 * frame camera view. The engine uses it to rasterize a conservative occlusion proxy against the
 * provider's depth. A provider that cannot guarantee the pairing must not return a frame, so
 * occlusion is disabled instead of guessing.</p>
 *
 * <p>The target and projection are valid only for the current frame. The projection is copied on
 * construction and on access; callers cannot mutate the shared frame.</p>
 */
public record SceneDepthFrame(RenderTarget target, SceneDepthSource source, Matrix4f projection) {
    public SceneDepthFrame {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(projection, "projection");
        if (!isUsableProjection(projection)) {
            throw new IllegalArgumentException("Depth frame projection must be finite and invertible");
        }
        projection = new Matrix4f(projection);
    }

    @Override
    public Matrix4f projection() {
        return new Matrix4f(this.projection);
    }

    /** Returns whether a projection can be used to rasterize an occlusion proxy. */
    public static boolean isUsableProjection(@Nullable Matrix4f projection) {
        if (projection == null || !projection.isFinite()) {
            return false;
        }
        float determinant = projection.determinant();
        return Float.isFinite(determinant) && determinant != 0.0F;
    }
}
