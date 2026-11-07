package mafisher.quasar.client.effect;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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

    private static void requireInvertible(Matrix4f matrix, String name) {
        float determinant = matrix.determinant();
        if (!Float.isFinite(determinant) || determinant == 0.0F) {
            throw new IllegalArgumentException(name + " must be finite and invertible");
        }
    }
}
