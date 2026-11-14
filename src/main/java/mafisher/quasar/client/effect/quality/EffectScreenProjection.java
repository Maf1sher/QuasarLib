package mafisher.quasar.client.effect.quality;

import java.util.Objects;
import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import mafisher.quasar.api.client.render.EffectScreenMetrics;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Per-frame camera projection used to derive screen-space effect metrics. One instance is created per render frame
 * and reused for every visible instance or segment cell; it never allocates per effect.
 *
 * <p>The projection is deliberately conservative: a sphere that surrounds the camera covers the full viewport, and
 * an unusable projection returns {@link EffectScreenMetrics#UNKNOWN} so the engine falls back to distance LOD.
 * Projected coverage ignores clipping against the viewport edges, which overestimates the visible area and keeps
 * detail rather than dropping it.</p>
 */
@ApiStatus.Internal
public final class EffectScreenProjection {
    private static final double EPSILON = 1.0e-6;
    private static final double HALF_DIAGONAL = Math.sqrt(2.0);

    private final float m00;
    private final float m01;
    private final float m02;
    private final float m03;
    private final float m10;
    private final float m11;
    private final float m12;
    private final float m13;
    private final float m20;
    private final float m21;
    private final float m22;
    private final float m23;
    private final float m30;
    private final float m31;
    private final float m32;
    private final float m33;
    private final float p00;
    private final float p01;
    private final float p02;
    private final float p03;
    private final float p10;
    private final float p11;
    private final float p12;
    private final float p13;
    private final float p20;
    private final float p21;
    private final float p22;
    private final float p23;
    private final float p30;
    private final float p31;
    private final float p32;
    private final float p33;

    private EffectScreenProjection(Matrix4f view, Matrix4f projection) {
        this.m00 = view.m00();
        this.m01 = view.m01();
        this.m02 = view.m02();
        this.m03 = view.m03();
        this.m10 = view.m10();
        this.m11 = view.m11();
        this.m12 = view.m12();
        this.m13 = view.m13();
        this.m20 = view.m20();
        this.m21 = view.m21();
        this.m22 = view.m22();
        this.m23 = view.m23();
        this.m30 = view.m30();
        this.m31 = view.m31();
        this.m32 = view.m32();
        this.m33 = view.m33();
        this.p00 = projection.m00();
        this.p01 = projection.m01();
        this.p02 = projection.m02();
        this.p03 = projection.m03();
        this.p10 = projection.m10();
        this.p11 = projection.m11();
        this.p12 = projection.m12();
        this.p13 = projection.m13();
        this.p20 = projection.m20();
        this.p21 = projection.m21();
        this.p22 = projection.m22();
        this.p23 = projection.m23();
        this.p30 = projection.m30();
        this.p31 = projection.m31();
        this.p32 = projection.m32();
        this.p33 = projection.m33();
    }

    /** Builds a projection for one frame, or returns {@code null} for unusable camera matrices. */
    @Nullable
    public static EffectScreenProjection from(EffectCameraSnapshot camera) {
        Objects.requireNonNull(camera, "camera");
        Matrix4f view = camera.modelViewMatrix();
        Matrix4f projection = camera.projectionMatrix();
        if (!isFinite(view) || !isFinite(projection)
            || Math.abs(projection.m00()) < EPSILON || Math.abs(projection.m11()) < EPSILON) {
            return null;
        }
        return new EffectScreenProjection(view, projection);
    }

    /** Projects one conservative bounding sphere into viewport coverage and centre distance. */
    public EffectScreenMetrics project(Vec3 position, double radius) {
        Objects.requireNonNull(position, "position");
        return this.project(position.x, position.y, position.z, radius);
    }

    /** Allocation-free variant used by the per-frame render path. */
    public EffectScreenMetrics project(double x, double y, double z, double radius) {
        if (!validSphere(x, y, z, radius)) {
            return EffectScreenMetrics.UNKNOWN;
        }

        double viewX = this.m00 * x + this.m10 * y + this.m20 * z + this.m30;
        double viewY = this.m01 * x + this.m11 * y + this.m21 * z + this.m31;
        double viewZ = this.m02 * x + this.m12 * y + this.m22 * z + this.m32;
        double viewW = this.m03 * x + this.m13 * y + this.m23 * z + this.m33;
        if (!validViewW(viewW)) {
            return EffectScreenMetrics.UNKNOWN;
        }
        viewX /= viewW;
        viewY /= viewW;
        viewZ /= viewW;

        double distance = Math.sqrt(viewX * viewX + viewY * viewY + viewZ * viewZ);
        if (!Double.isFinite(distance)) {
            return EffectScreenMetrics.UNKNOWN;
        }
        if (distance <= radius) {
            return EffectScreenMetrics.of(1.0, 0.0);
        }

        double clipX = this.p00 * viewX + this.p10 * viewY + this.p20 * viewZ + this.p30;
        double clipY = this.p01 * viewX + this.p11 * viewY + this.p21 * viewZ + this.p31;
        double clipW = this.p03 * viewX + this.p13 * viewY + this.p23 * viewZ + this.p33;
        if (!validClip(clipX, clipY, clipW)) {
            return EffectScreenMetrics.UNKNOWN;
        }

        double denominator = distance * distance - radius * radius;
        if (denominator <= 0.0) {
            return EffectScreenMetrics.of(1.0, 0.0);
        }
        double root = Math.sqrt(denominator);
        double radiusNdcX = radius * Math.abs(this.p00) / root;
        double radiusNdcY = radius * Math.abs(this.p11) / root;
        if (!validRadii(radiusNdcX, radiusNdcY)) {
            return EffectScreenMetrics.of(1.0, 0.0);
        }

        double coverage = clampUnit(Math.PI * radiusNdcX * radiusNdcY / 4.0);

        double ndcX = clipX / clipW;
        double ndcY = clipY / clipW;
        if (!validNdc(ndcX, ndcY)) {
            return EffectScreenMetrics.UNKNOWN;
        }
        double centreDistance = clampUnit(Math.hypot(ndcX, ndcY) / HALF_DIAGONAL);
        return EffectScreenMetrics.of(coverage, centreDistance);
    }

    private static boolean validSphere(double x, double y, double z, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return false;
        }
        return Double.isFinite(radius) && radius > 0.0;
    }

    private static boolean validViewW(double viewW) {
        return Double.isFinite(viewW) && Math.abs(viewW) >= EPSILON;
    }

    private static boolean validClip(double clipX, double clipY, double clipW) {
        return Double.isFinite(clipX) && Double.isFinite(clipY)
            && Double.isFinite(clipW) && clipW > EPSILON;
    }

    private static boolean validRadii(double radiusX, double radiusY) {
        return Double.isFinite(radiusX) && Double.isFinite(radiusY);
    }

    private static boolean validNdc(double ndcX, double ndcY) {
        return Double.isFinite(ndcX) && Double.isFinite(ndcY);
    }

    private static double clampUnit(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static boolean isFinite(Matrix4f matrix) {
        return rowIsFinite(matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03())
            && rowIsFinite(matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13())
            && rowIsFinite(matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23())
            && rowIsFinite(matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33());
    }

    private static boolean rowIsFinite(float m0, float m1, float m2, float m3) {
        return Float.isFinite(m0) && Float.isFinite(m1)
            && Float.isFinite(m2) && Float.isFinite(m3);
    }
}
