package mafisher.quasar.content;

import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pure orientation, timeline, and quality math for the content black hole. */
final class ContentBlackHoleGeometry {
    static final int VARIANT_AMBER = 0;
    static final int VARIANT_GOLD = 1;
    static final int VARIANT_NEON = 2;
    private static final int VARIANT_SHIFT = 60;
    private static final long SEED_PAYLOAD_MASK = 0x0FFF_FFFF_FFFF_FFFFL;
    private static final float FADE_IN_FRACTION = 0.08F;
    private static final float FADE_OUT_FRACTION = 0.12F;

    private ContentBlackHoleGeometry() {
    }

    /**
     * Content-owned palette selector carried in the top seed bits. The low bits remain free for the
     * procedural disk, so the same visual seed can be reused for each palette.
     */
    static long withVariant(long seed, int variant) {
        if (variant < VARIANT_AMBER || variant > VARIANT_NEON) {
            throw new IllegalArgumentException("Unknown black-hole variant: " + variant);
        }
        return (seed & SEED_PAYLOAD_MASK) | ((long)variant << VARIANT_SHIFT);
    }

    static int variant(long seed) {
        return (int)((seed >>> VARIANT_SHIFT) & 0x3L);
    }

    /** Returns the shader charge for the seed variant, falling back to the renderer setting. */
    static float chargeForSeed(long seed, float fallback) {
        return switch (variant(seed)) {
            case VARIANT_GOLD -> 0.0F;
            case VARIANT_NEON -> 1.0F;
            default -> fallback;
        };
    }

    /**
     * Returns the disk basis in the same yaw/pitch/roll order as the engine's visual transform. The disk lies in the
     * plane spanned by {@code right} and {@code forward}; {@code normal} points along the disk axis.
     */
    static Basis basis(float yaw, float pitch, float roll) {
        Matrix4f rotation = rotation(yaw, pitch, roll);
        return new Basis(
            rotation.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)),
            rotation.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)),
            rotation.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F))
        );
    }

    /**
     * Returns the effect rotation in the engine's yaw/pitch/roll order.
     *
     * <p>Use the composing {@code rotate*} methods, not the {@code rotation*} setters: those replace the whole
     * matrix, so chaining them would keep only the final roll and silently drop yaw and pitch.</p>
     */
    static Matrix4f rotation(float yaw, float pitch, float roll) {
        return new Matrix4f()
            .rotateY((float)Math.toRadians(yaw))
            .rotateX((float)Math.toRadians(pitch))
            .rotateZ((float)Math.toRadians(roll));
    }

    /**
     * Returns the effect-local facing that projects a mask quad as a screen-aligned rectangle.
     *
     * <p>The mask pose stack already contains {@link #rotation(float, float, float)}, so composing that rotation
     * with this facing yields the camera-to-world rotation: the quad's local X/Y map to the camera's right/up axes
     * and its normal points at the camera. A look-vector {@code rotationTo} only pins the normal and leaves an
     * arbitrary pitch-dependent roll, which lets a hard coverage edge cut through the screen-space effect.</p>
     */
    static Matrix4f maskFacing(float yaw, float pitch, float roll, Quaternionf cameraRotation) {
        Objects.requireNonNull(cameraRotation, "cameraRotation");
        Matrix4f cameraToWorld = new Matrix4f().rotation(cameraRotation);
        return rotation(yaw, pitch, roll).invert().mul(cameraToWorld);
    }

    /**
     * Selects a raymarch step budget from the quality tier and engine LOD. The march starts at the
     * black-hole bounding sphere, so traversal no longer scales with camera distance; LOD may trim
     * cosmetic work but never starves the disk/shadow traversal.
     */
    static int stepCount(int lodLevel, int qualityLevel) {
        int base = switch (qualityLevel) {
            case 0 -> 64;
            case 1 -> 80;
            case 2 -> 96;
            default -> 112;
        };
        return Math.max(64, base - Math.max(0, lodLevel) * 8);
    }

    /** Matches the engine's visual fade envelope: short fade in and slightly longer fade out. */
    static float fadeEnvelope(float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        float fadeIn = Math.min(1.0F, clamped / FADE_IN_FRACTION);
        float fadeOut = Math.min(1.0F, (1.0F - clamped) / FADE_OUT_FRACTION);
        return Math.min(fadeIn, fadeOut);
    }

    /** Immutable orthonormal disk frame. */
    record Basis(Vector3f right, Vector3f forward, Vector3f normal) {
        Basis {
            right = new Vector3f(right).normalize();
            forward = new Vector3f(forward).normalize();
            normal = new Vector3f(normal).normalize();
        }

        @Override
        public Vector3f right() {
            return new Vector3f(this.right);
        }

        @Override
        public Vector3f forward() {
            return new Vector3f(this.forward);
        }

        @Override
        public Vector3f normal() {
            return new Vector3f(this.normal);
        }
    }
}
