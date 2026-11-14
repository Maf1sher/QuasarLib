package mafisher.quasar.client.effect.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectRibbon;
import mafisher.quasar.api.effect.EffectCurve;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Reusable ring buffer and camera-facing emitter behind {@link EffectRibbon}.
 *
 * <p>The facade owns the public contract; this class owns the fixed arrays, ring state,
 * taper/UV mapping, and quad emission. It crosses the package boundary only because the facade and
 * this implementation live in different packages.</p>
 */
@ApiStatus.Internal
@SuppressWarnings({
    "PMD.GodClass",
    "PMD.TooManyMethods",
    "PMD.CognitiveComplexity",
    "PMD.TooManyFields",
    "checkstyle:ParameterNumber"
})
public final class EffectRibbonGeometry {
    private static final int RING_STRIDE = 5;
    private static final float EPSILON = 1.0E-6F;
    private static final float DUPLICATE_EPSILON_SQUARED = 1.0E-12F;
    private static final float UP_AXIS_THRESHOLD = 0.9F;

    private final EffectRibbonConfig config;
    private final float[] points;
    private final float[] controls;
    private final float[] rings;
    private final Matrix4f inversePose = new Matrix4f();
    private final Vector3f camera = new Vector3f();
    private final Cursor cursor = new Cursor();
    private int pointHead;
    private int pointCount;
    private int ringCount;
    private boolean dirty = true;

    public EffectRibbonGeometry(EffectRibbonConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.points = new float[config.maxPoints() * 3];
        this.controls = new float[config.maxPoints() * 3];
        this.rings = new float[EffectRibbon.MAX_RINGS * RING_STRIDE];
    }

    /** Clears the retained points and sampled rings; buffers are reused. */
    public void reset() {
        this.pointHead = 0;
        this.pointCount = 0;
        this.ringCount = 0;
        this.dirty = true;
    }

    /**
     * Appends one point in pose-local space.
     *
     * @return {@code true} when the point fits the history cap; {@code false} when the oldest point
     *     was dropped
     * @throws IllegalArgumentException when a coordinate is not finite
     */
    public boolean addPoint(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Ribbon points must be finite");
        }
        int maxPoints = this.config.maxPoints();
        int index;
        boolean retained = true;
        if (this.pointCount < maxPoints) {
            index = (this.pointHead + this.pointCount) % maxPoints;
            this.pointCount++;
        } else {
            index = this.pointHead;
            this.pointHead = (this.pointHead + 1) % maxPoints;
            retained = false;
        }
        int offset = index * 3;
        this.points[offset] = (float)x;
        this.points[offset + 1] = (float)y;
        this.points[offset + 2] = (float)z;
        this.dirty = true;
        return retained;
    }

    /** Number of retained points. */
    public int pointCount() {
        return this.pointCount;
    }

    /** Number of sampled rings after duplicate removal and cap reduction. */
    public int ringCount() {
        rebuildIfDirty();
        return this.ringCount;
    }

    /** Number of vertices the next {@code emit} writes. */
    public int vertexCount() {
        int rings = ringCount();
        return rings > 1 ? (rings - 1) * 4 : 0;
    }

    /** Conservative local-space bounds, including width. */
    public AABB localBounds() {
        rebuildIfDirty();
        if (this.ringCount == 0) {
            return new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int ring = 0; ring < this.ringCount; ring++) {
            float halfWidth = this.rings[ring * RING_STRIDE + 4];
            float x = this.rings[ring * RING_STRIDE];
            float y = this.rings[ring * RING_STRIDE + 1];
            float z = this.rings[ring * RING_STRIDE + 2];
            minX = Math.min(minX, x - halfWidth);
            minY = Math.min(minY, y - halfWidth);
            minZ = Math.min(minZ, z - halfWidth);
            maxX = Math.max(maxX, x + halfWidth);
            maxY = Math.max(maxY, y + halfWidth);
            maxZ = Math.max(maxZ, z + halfWidth);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Emits camera-facing quads for the current points. */
    public void emit(VertexConsumer consumer, PoseStack.Pose pose, float red, float green, float blue,
        float alpha, int overlay, int light) {
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(pose, "pose");
        rebuildIfDirty();
        if (this.ringCount < 2 || !prepareCamera(pose)) {
            return;
        }
        this.cursor.reset();
        for (int ring = 0; ring < this.ringCount; ring++) {
            emitRing(consumer, pose, ring, red, green, blue, alpha, overlay, light);
        }
    }

    private void emitRing(VertexConsumer consumer, PoseStack.Pose pose, int ring,
        float red, float green, float blue, float alpha, int overlay, int light) {
        float pointX = ringX(ring);
        float pointY = ringY(ring);
        float pointZ = ringZ(ring);
        updateTangent(ring);
        updateView(pointX, pointY, pointZ);
        updateSide();

        float halfWidth = this.rings[ring * RING_STRIDE + 4];
        float leftX = pointX - this.cursor.sideX * halfWidth;
        float leftY = pointY - this.cursor.sideY * halfWidth;
        float leftZ = pointZ - this.cursor.sideZ * halfWidth;
        float rightX = pointX + this.cursor.sideX * halfWidth;
        float rightY = pointY + this.cursor.sideY * halfWidth;
        float rightZ = pointZ + this.cursor.sideZ * halfWidth;
        float u = this.rings[ring * RING_STRIDE + 3];
        if (this.cursor.hasPrevious) {
            emitQuad(consumer, pose, this.cursor.previousLeftX, this.cursor.previousLeftY,
                this.cursor.previousLeftZ, this.cursor.previousRightX, this.cursor.previousRightY,
                this.cursor.previousRightZ, rightX, rightY, rightZ, leftX, leftY, leftZ,
                this.cursor.previousU, u, this.cursor.previousNormalX, this.cursor.previousNormalY,
                this.cursor.previousNormalZ, this.cursor.viewX, this.cursor.viewY, this.cursor.viewZ,
                red, green, blue, alpha, overlay, light);
        }
        this.cursor.previousLeftX = leftX;
        this.cursor.previousLeftY = leftY;
        this.cursor.previousLeftZ = leftZ;
        this.cursor.previousRightX = rightX;
        this.cursor.previousRightY = rightY;
        this.cursor.previousRightZ = rightZ;
        this.cursor.previousNormalX = this.cursor.viewX;
        this.cursor.previousNormalY = this.cursor.viewY;
        this.cursor.previousNormalZ = this.cursor.viewZ;
        this.cursor.previousSideX = this.cursor.sideX;
        this.cursor.previousSideY = this.cursor.sideY;
        this.cursor.previousSideZ = this.cursor.sideZ;
        this.cursor.previousU = u;
        this.cursor.hasPrevious = true;
    }

    private void updateTangent(int ring) {
        int before = Math.max(0, ring - 1);
        int after = Math.min(this.ringCount - 1, ring + 1);
        float tangentX = ringX(after) - ringX(before);
        float tangentY = ringY(after) - ringY(before);
        float tangentZ = ringZ(after) - ringZ(before);
        float tangentLength = EffectRibbonSpline.length(tangentX, tangentY, tangentZ);
        if (tangentLength <= EPSILON) {
            if (this.cursor.hasPrevious) {
                tangentX = this.cursor.tangentX;
                tangentY = this.cursor.tangentY;
                tangentZ = this.cursor.tangentZ;
            } else {
                tangentX = 1.0F;
                tangentY = 0.0F;
                tangentZ = 0.0F;
            }
        } else {
            tangentX /= tangentLength;
            tangentY /= tangentLength;
            tangentZ /= tangentLength;
        }
        this.cursor.tangentX = tangentX;
        this.cursor.tangentY = tangentY;
        this.cursor.tangentZ = tangentZ;
    }

    private void updateView(float pointX, float pointY, float pointZ) {
        float viewX = this.camera.x - pointX;
        float viewY = this.camera.y - pointY;
        float viewZ = this.camera.z - pointZ;
        float viewLength = EffectRibbonSpline.length(viewX, viewY, viewZ);
        if (viewLength <= EPSILON) {
            if (this.cursor.hasPrevious) {
                viewX = this.cursor.previousNormalX;
                viewY = this.cursor.previousNormalY;
                viewZ = this.cursor.previousNormalZ;
            } else {
                viewX = -this.cursor.tangentX;
                viewY = -this.cursor.tangentY;
                viewZ = -this.cursor.tangentZ;
            }
        } else {
            viewX /= viewLength;
            viewY /= viewLength;
            viewZ /= viewLength;
        }
        this.cursor.viewX = viewX;
        this.cursor.viewY = viewY;
        this.cursor.viewZ = viewZ;
    }

    private void updateSide() {
        float sideX = this.cursor.tangentY * this.cursor.viewZ
            - this.cursor.tangentZ * this.cursor.viewY;
        float sideY = this.cursor.tangentZ * this.cursor.viewX
            - this.cursor.tangentX * this.cursor.viewZ;
        float sideZ = this.cursor.tangentX * this.cursor.viewY
            - this.cursor.tangentY * this.cursor.viewX;
        float sideLength = EffectRibbonSpline.length(sideX, sideY, sideZ);
        if (sideLength <= EPSILON) {
            float axisX = 0.0F;
            float axisY = 1.0F;
            float axisZ = 0.0F;
            if (Math.abs(this.cursor.tangentY) > UP_AXIS_THRESHOLD) {
                axisX = 1.0F;
                axisY = 0.0F;
            }
            sideX = this.cursor.tangentY * axisZ - this.cursor.tangentZ * axisY;
            sideY = this.cursor.tangentZ * axisX - this.cursor.tangentX * axisZ;
            sideZ = this.cursor.tangentX * axisY - this.cursor.tangentY * axisX;
            sideLength = EffectRibbonSpline.length(sideX, sideY, sideZ);
        }
        if (sideLength <= EPSILON) {
            if (this.cursor.hasPrevious) {
                sideX = this.cursor.previousSideX;
                sideY = this.cursor.previousSideY;
                sideZ = this.cursor.previousSideZ;
            } else {
                sideX = 1.0F;
                sideY = 0.0F;
                sideZ = 0.0F;
            }
        } else {
            sideX /= sideLength;
            sideY /= sideLength;
            sideZ /= sideLength;
        }
        if (this.cursor.hasPrevious
            && sideX * this.cursor.previousSideX + sideY * this.cursor.previousSideY
                + sideZ * this.cursor.previousSideZ < 0.0F) {
            sideX = -sideX;
            sideY = -sideY;
            sideZ = -sideZ;
        }
        this.cursor.sideX = sideX;
        this.cursor.sideY = sideY;
        this.cursor.sideZ = sideZ;
    }

    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose,
        float previousLeftX, float previousLeftY, float previousLeftZ,
        float previousRightX, float previousRightY, float previousRightZ,
        float rightX, float rightY, float rightZ, float leftX, float leftY, float leftZ,
        float previousU, float currentU,
        float previousNormalX, float previousNormalY, float previousNormalZ,
        float currentNormalX, float currentNormalY, float currentNormalZ,
        float red, float green, float blue, float alpha, int overlay, int light) {
        emitVertex(consumer, pose, previousLeftX, previousLeftY, previousLeftZ, previousU, 0.0F,
            previousNormalX, previousNormalY, previousNormalZ, red, green, blue, alpha, overlay, light);
        emitVertex(consumer, pose, previousRightX, previousRightY, previousRightZ, previousU, 1.0F,
            previousNormalX, previousNormalY, previousNormalZ, red, green, blue, alpha, overlay, light);
        emitVertex(consumer, pose, rightX, rightY, rightZ, currentU, 1.0F,
            currentNormalX, currentNormalY, currentNormalZ, red, green, blue, alpha, overlay, light);
        emitVertex(consumer, pose, leftX, leftY, leftZ, currentU, 0.0F,
            currentNormalX, currentNormalY, currentNormalZ, red, green, blue, alpha, overlay, light);
    }

    private static void emitVertex(VertexConsumer consumer, PoseStack.Pose pose,
        float x, float y, float z, float u, float v,
        float normalX, float normalY, float normalZ,
        float red, float green, float blue, float alpha, int overlay, int light) {
        consumer.addVertex(pose, x, y, z)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private boolean prepareCamera(PoseStack.Pose pose) {
        Matrix4f matrix = pose.pose();
        float determinant = matrix.determinant();
        if (!Float.isFinite(determinant) || determinant == 0.0F) {
            return false;
        }
        this.inversePose.set(matrix).invert();
        this.camera.set(0.0F, 0.0F, 0.0F);
        this.inversePose.transformPosition(this.camera);
        return Float.isFinite(this.camera.x) && Float.isFinite(this.camera.y)
            && Float.isFinite(this.camera.z);
    }

    private void rebuildIfDirty() {
        if (!this.dirty) {
            return;
        }
        this.dirty = false;
        this.ringCount = 0;
        int count = compactPoints();
        if (count < 2) {
            return;
        }
        int subdivisions = effectiveSubdivisions(count);
        this.ringCount = subdivisions == 1
            ? EffectRibbonSpline.copyControls(this.controls, count, this.rings, RING_STRIDE)
            : EffectRibbonSpline.sampleControls(this.controls, count, subdivisions, this.rings,
                RING_STRIDE);
        applyTaperAndUv();
    }

    private int compactPoints() {
        int count = this.pointCount;
        if (count == 0) {
            return 0;
        }
        int maxPoints = this.config.maxPoints();
        int compacted = 0;
        boolean hasLast = false;
        float lastX = 0.0F;
        float lastY = 0.0F;
        float lastZ = 0.0F;
        for (int index = 0; index < count; index++) {
            int source = (this.pointHead + index) % maxPoints * 3;
            float x = this.points[source];
            float y = this.points[source + 1];
            float z = this.points[source + 2];
            if (hasLast) {
                float deltaX = x - lastX;
                float deltaY = y - lastY;
                float deltaZ = z - lastZ;
                if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                    <= DUPLICATE_EPSILON_SQUARED) {
                    continue;
                }
            }
            int target = compacted * 3;
            this.controls[target] = x;
            this.controls[target + 1] = y;
            this.controls[target + 2] = z;
            compacted++;
            lastX = x;
            lastY = y;
            lastZ = z;
            hasLast = true;
        }
        return compacted;
    }

    private int effectiveSubdivisions(int count) {
        int spans = count - 1;
        long requestedRings = (long)spans * this.config.subdivisions() + 1L;
        if (requestedRings <= EffectRibbon.MAX_RINGS) {
            return this.config.subdivisions();
        }
        int reduced = (int)((EffectRibbon.MAX_RINGS - 1L) / spans);
        return Math.max(1, Math.min(this.config.subdivisions(), reduced));
    }

    private void applyTaperAndUv() {
        if (this.ringCount == 0) {
            return;
        }
        float totalLength = 0.0F;
        for (int ring = 1; ring < this.ringCount; ring++) {
            totalLength += ringDistance(ring - 1, ring);
        }
        float cumulative = 0.0F;
        for (int ring = 0; ring < this.ringCount; ring++) {
            if (ring > 0) {
                cumulative += ringDistance(ring - 1, ring);
            }
            float progress = totalLength > EPSILON
                ? cumulative / totalLength
                : (float)ring / Math.max(1, this.ringCount - 1);
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            float width = taperWidth(progress);
            if (!Float.isFinite(width)) {
                width = 0.0F;
            }
            this.rings[ring * RING_STRIDE + 3] = this.config.uvMode() == EffectRibbon.UvMode.NORMALIZED
                ? progress
                : cumulative / this.config.textureLength();
            this.rings[ring * RING_STRIDE + 4] =
                Math.max(0.0F, Math.min(EffectRibbon.MAX_WIDTH, width)) * 0.5F;
        }
    }

    private float taperWidth(float progress) {
        EffectCurve curve = this.config.widthCurve();
        if (curve != null) {
            return curve.sampleFloat(progress);
        }
        return this.config.startWidth()
            + (this.config.endWidth() - this.config.startWidth()) * progress;
    }

    private float ringDistance(int first, int second) {
        return EffectRibbonSpline.length(ringX(second) - ringX(first),
            ringY(second) - ringY(first), ringZ(second) - ringZ(first));
    }

    private float ringX(int index) {
        return this.rings[index * RING_STRIDE];
    }

    private float ringY(int index) {
        return this.rings[index * RING_STRIDE + 1];
    }

    private float ringZ(int index) {
        return this.rings[index * RING_STRIDE + 2];
    }

    private static final class Cursor {
        private float tangentX;
        private float tangentY;
        private float tangentZ;
        private float viewX;
        private float viewY;
        private float viewZ;
        private float sideX;
        private float sideY;
        private float sideZ;
        private float previousLeftX;
        private float previousLeftY;
        private float previousLeftZ;
        private float previousRightX;
        private float previousRightY;
        private float previousRightZ;
        private float previousNormalX;
        private float previousNormalY;
        private float previousNormalZ;
        private float previousSideX;
        private float previousSideY;
        private float previousSideZ;
        private float previousU;
        private boolean hasPrevious;

        private void reset() {
            this.hasPrevious = false;
        }
    }
}
