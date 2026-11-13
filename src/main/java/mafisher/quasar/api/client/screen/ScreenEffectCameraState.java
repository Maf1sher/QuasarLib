package mafisher.quasar.api.client.screen;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Immutable camera snapshot for a screen operation.
 *
 * <p>Matrices are copied on construction and on access so an operation cannot
 * mutate compositor state. Dimensions are the main render target size for the
 * current frame.</p>
 */
public record ScreenEffectCameraState(
    Vec3 position,
    Matrix4f modelViewMatrix,
    Matrix4f projectionMatrix,
    int viewportWidth,
    int viewportHeight,
    float partialTick
) {
    public ScreenEffectCameraState {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(modelViewMatrix, "modelViewMatrix");
        Objects.requireNonNull(projectionMatrix, "projectionMatrix");
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        if (!Float.isFinite(partialTick) || partialTick < 0.0F || partialTick > 1.0F) {
            throw new IllegalArgumentException("Partial tick must be finite and in [0, 1]");
        }
        modelViewMatrix = new Matrix4f(modelViewMatrix);
        projectionMatrix = new Matrix4f(projectionMatrix);
        requireInvertible(modelViewMatrix, "modelViewMatrix");
        requireInvertible(projectionMatrix, "projectionMatrix");
    }

    @Override
    public Matrix4f modelViewMatrix() {
        return new Matrix4f(this.modelViewMatrix);
    }

    @Override
    public Matrix4f projectionMatrix() {
        return new Matrix4f(this.projectionMatrix);
    }

    /**
     * Returns the inverse view matrix in a fresh copy.
     *
     * <p>Screen operations use it with {@link #inverseProjectionMatrix()} to
     * reconstruct world-space positions from the sampled scene depth.</p>
     */
    public Matrix4f inverseViewMatrix() {
        return new Matrix4f(this.modelViewMatrix).invert();
    }

    /** Returns the inverse projection matrix in a fresh copy. */
    public Matrix4f inverseProjectionMatrix() {
        return new Matrix4f(this.projectionMatrix).invert();
    }

    /**
     * Reconstructs the world-space camera ray for normalized screen coordinates.
     *
     * <p>The ray is sampled on the far plane. Minecraft folds the first-person
     * view bob into the projection as a view-space translation; inverting that on
     * the near plane turns a small bob into a large angular error.</p>
     *
     * @param u normalized horizontal coordinate in {@code [0, 1]}
     * @param v normalized vertical coordinate in {@code [0, 1]}
     * @return a normalized world-space direction
     */
    public Vector3f worldRay(float u, float v) {
        Vector4f clip = new Vector4f(u * 2.0F - 1.0F, v * 2.0F - 1.0F, 1.0F, 1.0F);
        Vector4f view = new Vector4f(clip).mul(this.inverseProjectionMatrix());
        if (Math.abs(view.w) < 1.0E-6F || !Float.isFinite(view.w)) {
            throw new IllegalArgumentException("Cannot reconstruct a camera ray from a degenerate projection");
        }
        view.div(view.w);
        Vector3f direction = new Vector3f(view.x, view.y, view.z);
        this.inverseViewMatrix().transformDirection(direction);
        return direction.normalize();
    }

    /**
     * Projects a world-space direction to normalized screen coordinates.
     *
     * @param direction a world-space direction; it does not need to be normalized
     * @return the screen position, or empty when the direction is behind the camera
     */
    public Optional<Vector2f> screenUv(Vector3f direction) {
        Objects.requireNonNull(direction, "direction");
        Vector4f view = new Vector4f(direction, 0.0F).mul(this.modelViewMatrix());
        Vector4f clip = new Vector4f(view).mul(this.projectionMatrix());
        if (!(clip.w > 1.0E-5F) || !Float.isFinite(clip.w)
            || !Float.isFinite(clip.x) || !Float.isFinite(clip.y)) {
            return Optional.empty();
        }
        float u = clip.x / clip.w * 0.5F + 0.5F;
        float v = clip.y / clip.w * 0.5F + 0.5F;
        if (!Float.isFinite(u) || !Float.isFinite(v)) {
            return Optional.empty();
        }
        return Optional.of(new Vector2f(u, v));
    }

    /**
     * Returns whether both matrices can be inverted. The compositor calls this
     * before constructing a camera snapshot so a single degenerate frame does
     * not disable screen effects for the rest of the session.
     */
    public static boolean isUsable(Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return isInvertible(modelViewMatrix) && isInvertible(projectionMatrix);
    }

    private static boolean isInvertible(Matrix4f matrix) {
        if (matrix == null) {
            return false;
        }
        float determinant = matrix.determinant();
        return Float.isFinite(determinant) && determinant != 0.0F;
    }

    private static void requireInvertible(Matrix4f matrix, String name) {
        if (!isInvertible(matrix)) {
            throw new IllegalArgumentException(name + " must be finite and invertible");
        }
    }
}
