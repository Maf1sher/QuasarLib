package mafisher.quasar.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Objects;
import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.client.effect.mesh.EffectRibbonConfig;
import mafisher.quasar.client.effect.mesh.EffectRibbonGeometry;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Reusable camera-facing ribbon for splines and trails.
 *
 * <p>Points are expressed in the same local space as the {@link PoseStack.Pose} passed to
 * {@link #emit(VertexConsumer, PoseStack.Pose, float, float, float, float, int, int)}. That pose is
 * camera-relative during a world-effect render, so the camera sits at the pose origin. The ribbon
 * derives the camera from the inverse pose instead of asking the caller for another position.</p>
 *
 * <p>An instance owns fixed buffers and is not thread-safe. Build points with {@link #reset()} and
 * {@link #addPoint(double, double, double)}, then call {@code emit}. {@code emit} samples the spline,
 * applies the taper and UVs, and writes quads without allocating. Rebuild only when the points
 * change; a second {@code emit} reuses the sampled rings.</p>
 *
 * <p>Camera-facing extrusion is computed in pose-local space. Use a uniform pose scale; a
 * non-uniform scale changes the world-space cross-section.</p>
 *
 * <p>{@link #addPoint(double, double, double)} returns {@code false} when the oldest point was
 * dropped to stay inside the configured history cap. Cross-frame trail history is caller-owned:
 * keep one ribbon per renderer scratch or per active instance, never in a static engine table.</p>
 */
@SuppressWarnings("checkstyle:ParameterNumber")
public final class EffectRibbon {
    /** Maximum number of retained points for one ribbon. */
    public static final int MAX_POINTS = 512;
    /** Maximum spline subdivisions between two control points. */
    public static final int MAX_SUBDIVISIONS = 16;
    /** Maximum number of sampled rings for one ribbon. */
    public static final int MAX_RINGS = 4_096;
    /** Maximum number of emitted vertices for one ribbon. */
    public static final int MAX_VERTICES = (MAX_RINGS - 1) * 4;
    /** Maximum authored full width in local units. */
    public static final float MAX_WIDTH = 128.0F;

    private final EffectRibbonGeometry geometry;

    private EffectRibbon(EffectRibbonGeometry geometry) {
        this.geometry = geometry;
    }

    /** Starts a ribbon configuration. */
    public static Builder builder() {
        return new Builder();
    }

    /** Clears the retained points and sampled rings; buffers are reused. */
    public void reset() {
        this.geometry.reset();
    }

    /**
     * Appends one point in pose-local space.
     *
     * @return {@code true} when the point fits the history cap; {@code false} when the oldest point
     *     was dropped to make room
     * @throws IllegalArgumentException when a coordinate is not finite
     */
    public boolean addPoint(double x, double y, double z) {
        return this.geometry.addPoint(x, y, z);
    }

    /** Number of retained points, including points that will later collapse as duplicates. */
    public int pointCount() {
        return this.geometry.pointCount();
    }

    /** Number of sampled rings after duplicate removal and cap reduction. */
    public int ringCount() {
        return this.geometry.ringCount();
    }

    /** Number of vertices the next {@code emit} writes. */
    public int vertexCount() {
        return this.geometry.vertexCount();
    }

    /**
     * Conservative local-space bounds of the current ribbon, including width.
     *
     * <p>This is a culling helper and may allocate; do not call it while emitting vertices.</p>
     */
    public AABB localBounds() {
        return this.geometry.localBounds();
    }

    /**
     * Emits camera-facing quads for the current points.
     *
     * <p>The pose maps local points to the camera-relative world. The camera is read from the pose
     * origin through the inverse pose, so callers must pass the pose supplied to
     * {@link VisualEffectRenderer#render}. A singular pose emits nothing.</p>
     *
     * @param consumer quad consumer for the selected render pass
     * @param pose camera-relative local-to-world pose
     * @param red red tint
     * @param green green tint
     * @param blue blue tint
     * @param alpha alpha applied to every vertex
     * @param overlay overlay value, usually {@code OverlayTexture.NO_OVERLAY}
     * @param light packed light value
     */
    public void emit(VertexConsumer consumer, PoseStack.Pose pose, float red, float green, float blue,
        float alpha, int overlay, int light) {
        this.geometry.emit(consumer, pose, red, green, blue, alpha, overlay, light);
    }

    /** UV mapping along the ribbon. */
    public enum UvMode {
        /** U runs from zero at the first point to one at the last point. */
        NORMALIZED,
        /** U advances by arc length divided by the configured texture length. */
        TILED
    }

    /** Configures and creates reusable ribbons. */
    public static final class Builder {
        private int maxPoints = 256;
        private int subdivisions = 4;
        private float startWidth = 1.0F;
        private float endWidth = 1.0F;
        private @Nullable EffectCurve widthCurve;
        private UvMode uvMode = UvMode.NORMALIZED;
        private float textureLength = 1.0F;

        private Builder() {
        }

        /** Sets the history cap; overflow drops the oldest point. */
        public Builder maxPoints(int maxPoints) {
            this.maxPoints = maxPoints;
            return this;
        }

        /** Sets spline subdivisions between two control points. */
        public Builder subdivisions(int subdivisions) {
            this.subdivisions = subdivisions;
            return this;
        }

        /** Sets a linear full-width taper from the first point to the last point. */
        public Builder taper(float startWidth, float endWidth) {
            this.startWidth = startWidth;
            this.endWidth = endWidth;
            this.widthCurve = null;
            return this;
        }

        /** Sets a float curve that samples the full width over normalized ribbon progress. */
        public Builder taper(EffectCurve widthCurve) {
            this.widthCurve = Objects.requireNonNull(widthCurve, "widthCurve");
            if (widthCurve.type() != EffectCurve.ValueType.FLOAT) {
                throw new IllegalArgumentException("Ribbon taper curve must be a float curve");
            }
            return this;
        }

        /** Selects the UV mapping and the texture length used by {@link UvMode#TILED}. */
        public Builder uv(UvMode uvMode, float textureLength) {
            this.uvMode = Objects.requireNonNull(uvMode, "uvMode");
            this.textureLength = textureLength;
            return this;
        }

        /** Builds a reusable ribbon with its own fixed buffers. */
        public EffectRibbon build() {
            return new EffectRibbon(new EffectRibbonGeometry(new EffectRibbonConfig(
                this.maxPoints, this.subdivisions, this.startWidth, this.endWidth,
                this.widthCurve, this.uvMode, this.textureLength
            )));
        }
    }
}
