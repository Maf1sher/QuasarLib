package mafisher.quasar.api.client.render;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Immutable camera snapshot shared by every world effect in one frame.
 *
 * <p>The engine creates one snapshot per frame and exposes it through
 * {@link EffectRenderContext}. Matrices are copied on construction and on access, so a
 * renderer cannot mutate shared camera state.</p>
 */
public record EffectCameraSnapshot(
    Vec3 position,
    Matrix4f modelViewMatrix,
    Matrix4f projectionMatrix,
    int viewportWidth,
    int viewportHeight
) {
    public EffectCameraSnapshot {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(modelViewMatrix, "modelViewMatrix");
        Objects.requireNonNull(projectionMatrix, "projectionMatrix");
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("Camera position must be finite");
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
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

    /** Returns the inverse view matrix in a fresh copy. */
    public Matrix4f inverseViewMatrix() {
        return new Matrix4f(this.modelViewMatrix).invert();
    }

    /** Returns the inverse projection matrix in a fresh copy. */
    public Matrix4f inverseProjectionMatrix() {
        return new Matrix4f(this.projectionMatrix).invert();
    }

    /**
     * Returns whether both camera matrices can be inverted.
     *
     * <p>The render loop calls this before constructing a snapshot so a single degenerate
     * projection frame during world join is skipped instead of failing the whole client.</p>
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
