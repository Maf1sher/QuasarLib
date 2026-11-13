package mafisher.quasar.api.effect;

import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One effect frame in world space and in camera-relative space.
 *
 * <p>The basis is always built with the engine order {@code Y(yaw) * X(pitch) * Z(roll)}.
 * {@code right}, {@code up}, and {@code forward} are the rotated local {@code +X}, {@code +Y},
 * and {@code +Z} axes. The camera-relative origin is {@code origin - cameraPosition}; it is
 * computed in double precision and only narrowed when a shader uniform is bound.</p>
 *
 * <p>This type is Minecraft-independent and safe to use from the server. Public render APIs
 * expose it through their contexts.</p>
 */
public record EffectSpace(
    EffectPoint origin,
    EffectPoint cameraPosition,
    Basis basis,
    float scale
) {
    public EffectSpace {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        Objects.requireNonNull(basis, "basis");
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            throw new IllegalArgumentException("Effect scale must be finite and positive");
        }
    }

    /** Returns the effect origin relative to the camera, in blocks. */
    public EffectPoint cameraRelativeOrigin() {
        return new EffectPoint(
            this.origin.x() - this.cameraPosition.x(),
            this.origin.y() - this.cameraPosition.y(),
            this.origin.z() - this.cameraPosition.z()
        );
    }

    /** Converts a world-space point to the camera-relative frame. */
    public EffectPoint toCameraRelative(EffectPoint world) {
        Objects.requireNonNull(world, "world");
        return new EffectPoint(
            world.x() - this.cameraPosition.x(),
            world.y() - this.cameraPosition.y(),
            world.z() - this.cameraPosition.z()
        );
    }

    /** Converts a camera-relative point back to world space. */
    public EffectPoint toWorld(EffectPoint cameraRelative) {
        Objects.requireNonNull(cameraRelative, "cameraRelative");
        return new EffectPoint(
            cameraRelative.x() + this.cameraPosition.x(),
            cameraRelative.y() + this.cameraPosition.y(),
            cameraRelative.z() + this.cameraPosition.z()
        );
    }

    /** Rotates a world-space direction into the effect's local basis. */
    public Vector3f worldDirectionToLocal(Vector3f worldDirection) {
        return this.basis.inverseTransformDirection(worldDirection);
    }

    /** Rotates an effect-local direction into world space. */
    public Vector3f localDirectionToWorld(Vector3f localDirection) {
        return this.basis.transformDirection(localDirection);
    }

    /**
     * Builds the frame for one effect transform and camera position.
     *
     * @param transform the interpolated effect transform
     * @param scale the positive effect scale
     * @param cameraPosition the camera's world position
     * @return an immutable effect space
     */
    public static EffectSpace from(EffectTransform transform, float scale, EffectPoint cameraPosition) {
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        return new EffectSpace(
            transform.position(),
            cameraPosition,
            Basis.fromEuler(transform.yaw(), transform.pitch(), transform.roll()),
            scale
        );
    }

    /**
     * Orthonormal effect basis. The only way to build one is {@link #fromEuler(float, float, float)},
     * so the engine rotation order cannot be bypassed by a hand-built matrix.
     */
    public static final class Basis {
        private final Vector3f right;
        private final Vector3f up;
        private final Vector3f forward;
        private final Matrix4f rotation;

        private Basis(Vector3f right, Vector3f up, Vector3f forward, Matrix4f rotation) {
            this.right = right;
            this.up = up;
            this.forward = forward;
            this.rotation = rotation;
        }

        /**
         * Builds the engine orientation from yaw, pitch, and roll in degrees.
         *
         * <p>The composition order is {@code Y(yaw) * X(pitch) * Z(roll)}. Use the composing
         * {@code rotate*} methods, not the replacing {@code rotation*} setters.</p>
         */
        public static Basis fromEuler(float yaw, float pitch, float roll) {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
                throw new IllegalArgumentException("Effect angles must be finite");
            }
            Matrix4f matrix = new Matrix4f()
                .rotateY((float)Math.toRadians(yaw))
                .rotateX((float)Math.toRadians(pitch))
                .rotateZ((float)Math.toRadians(roll));
            Vector3f right = matrix.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
            Vector3f up = matrix.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
            Vector3f forward = matrix.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
            return new Basis(right, up, forward, new Matrix4f(matrix));
        }

        /** Returns a copy of the rotated local +X axis. */
        public Vector3f right() {
            return new Vector3f(this.right);
        }

        /** Returns a copy of the rotated local +Y axis. */
        public Vector3f up() {
            return new Vector3f(this.up);
        }

        /** Returns a copy of the rotated local +Z axis. */
        public Vector3f forward() {
            return new Vector3f(this.forward);
        }

        /** Returns a copy of the pure rotation matrix. */
        public Matrix4f rotation() {
            return new Matrix4f(this.rotation);
        }

        /** Rotates a local direction into world space. */
        public Vector3f transformDirection(Vector3f direction) {
            Objects.requireNonNull(direction, "direction");
            return this.rotation.transformDirection(new Vector3f(direction));
        }

        /** Rotates a world direction into local space. */
        public Vector3f inverseTransformDirection(Vector3f direction) {
            Objects.requireNonNull(direction, "direction");
            return new Matrix4f(this.rotation).transpose().transformDirection(new Vector3f(direction));
        }
    }
}
