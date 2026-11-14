package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import mafisher.quasar.api.compat.SceneDepthFrame;
import mafisher.quasar.api.compat.SceneDepthSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Conservative, asynchronous provider-depth occlusion for effect owners.
 *
 * <p>One query renders a tiny box that encloses the owner's visible bounds against the active
 * scene depth. A zero result means the whole proxy is behind the scene, so every pass of the owner
 * may be skipped. Any other state, including a missing result, a changed camera, a changed
 * projection, an unsupported driver, or a backend failure, draws the effect.</p>
 *
 * <p>This type owns no GL calls; the backend boundary keeps the policy and the query lifecycle
 * headlessly testable. It never blocks on a query result and never reuses a result after it may
 * have become stale.</p>
 */
@SuppressWarnings({
    "PMD.GodClass", "PMD.TooManyMethods", "PMD.TooManyFields", "PMD.CognitiveComplexity",
    "checkstyle:CyclomaticComplexity", "checkstyle:BooleanExpressionComplexity"
})
final class ClientDepthOcclusion {
    static final int MAX_PROBES_PER_FRAME = 16;
    static final int MAX_IN_FLIGHT = 32;
    static final int MAX_RESULT_AGE_FRAMES = 2;
    static final int MAX_QUERY_AGE_FRAMES = 4;
    static final int MAX_OWNERS = 512;
    private static final double CAMERA_ROTATION_COS = Math.cos(Math.toRadians(2.0));
    private static final double BOUNDS_EPSILON = 1.0e-3;
    private static final long OWNER_IDLE_FRAMES = 120L;
    private static final long CLEANUP_INTERVAL_FRAMES = 128L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientDepthOcclusion.class);

    private final DepthProbeBackend backend;
    private final DepthProbeRequest request = new DepthProbeRequest();
    private final Map<UUID, OwnerState> owners = new LinkedHashMap<>();
    private long frameId;
    private long lastCleanupFrame;
    private int probesThisFrame;
    private int probesInFlight;
    private boolean active;
    private boolean supportChecked;
    private boolean supported;
    @Nullable
    private String disabledReason = "not initialized";
    @Nullable
    private RenderTarget depthTarget;
    @Nullable
    private SceneDepthSource source;
    @Nullable
    private Matrix4f projection;
    @Nullable
    private Matrix4f viewRotation;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private double forwardX;
    private double forwardY = -1.0;
    private double forwardZ;

    ClientDepthOcclusion(DepthProbeBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /**
     * Starts one frame and polls results issued by earlier frames.
     *
     * @param frame resolved provider/opaque depth, or {@code null} when no safe depth is available
     * @param camera current frame camera, or {@code null} for a degenerate frame
     */
    void beginFrame(@Nullable SceneDepthFrame frame, @Nullable EffectCameraSnapshot camera) {
        this.frameId++;
        this.probesThisFrame = 0;
        this.poll();
        if (frame == null || camera == null) {
            this.deactivate(frame == null ? "no safe scene depth" : "no camera snapshot", null);
            return;
        }
        if (frame.source() == SceneDepthSource.EXTERNAL_SHADERPACK) {
            this.deactivate("external shaderpack depth", frame.source());
            return;
        }
        if (!this.supportChecked) {
            this.supportChecked = true;
            this.supported = this.backend.supported();
            if (!this.supported) {
                this.disabledReason = "GPU occlusion queries unavailable";
                LOGGER.info("Provider-depth occlusion is disabled: GPU occlusion queries are unavailable");
            }
        }
        if (!this.supported) {
            this.active = false;
            return;
        }

        Matrix4f nextProjection = frame.projection();
        Matrix4f view = camera.modelViewMatrix();
        boolean changed = !sameTarget(this.depthTarget, frame.target())
            || this.source != frame.source()
            || this.projection == null
            || !this.projection.equals(nextProjection);
        if (changed) {
            this.invalidateAll();
        }
        this.depthTarget = frame.target();
        this.source = frame.source();
        this.projection = nextProjection;
        this.viewRotation = new Matrix4f(view).setTranslation(0.0F, 0.0F, 0.0F);
        Vec3 position = camera.position();
        this.cameraX = position.x;
        this.cameraY = position.y;
        this.cameraZ = position.z;
        double fx = -view.m20();
        double fy = -view.m21();
        double fz = -view.m22();
        double length = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (length < 1.0e-6) {
            this.forwardX = 0.0;
            this.forwardY = 0.0;
            this.forwardZ = -1.0;
        } else {
            this.forwardX = fx / length;
            this.forwardY = fy / length;
            this.forwardZ = fz / length;
        }
        this.active = true;
        this.disabledReason = null;
        this.cleanup();
    }

    /**
     * Returns whether one owner should be drawn this frame and schedules a refresh probe when the
     * result is old enough to need one.
     */
    boolean shouldDraw(UUID ownerId, @Nullable AABB bounds, boolean enabled) {
        if (!this.active || !enabled || this.depthTarget == null || bounds == null
            || !usableBounds(bounds)) {
            return true;
        }
        if (bounds.contains(this.cameraX, this.cameraY, this.cameraZ)) {
            return true;
        }
        OwnerState state = this.owner(ownerId);
        state.lastSeenFrame = this.frameId;
        boolean valid = state.occluded
            && this.frameId - state.testedFrame <= MAX_RESULT_AGE_FRAMES
            && this.sameCamera(state)
            && this.sameBounds(state, bounds);
        if (!valid && state.occluded) {
            state.occluded = false;
        }
        if (state.query == 0 && this.probesThisFrame < MAX_PROBES_PER_FRAME
            && this.probesInFlight < MAX_IN_FLIGHT) {
            this.issue(state, bounds);
        }
        return !(valid && state.occluded);
    }

    /** Returns a short diagnostic line for the client diagnostics command. */
    String status() {
        int occluded = 0;
        for (OwnerState state : this.owners.values()) {
            if (state.occluded) {
                occluded++;
            }
        }
        String depth = this.source == null ? "none" : this.source.toString();
        String state = this.active ? "active" : "inactive";
        String reason = this.disabledReason == null ? "" : " reason=" + this.disabledReason;
        return state + " depth=" + depth + " probes=" + this.probesThisFrame
            + " pending=" + this.probesInFlight + " occluded=" + occluded + reason;
    }

    /** Drops one owner's cached result and cancels its in-flight query. */
    void forget(UUID ownerId) {
        OwnerState state = this.owners.remove(ownerId);
        if (state != null) {
            this.cancel(state);
        }
    }

    /** Releases every query and resets lifecycle-scoped state. */
    void reset() {
        for (OwnerState state : this.owners.values()) {
            this.cancel(state);
        }
        this.owners.clear();
        this.frameId = 0L;
        this.lastCleanupFrame = 0L;
        this.probesThisFrame = 0;
        this.probesInFlight = 0;
        this.active = false;
        this.supportChecked = false;
        this.supported = false;
        this.disabledReason = "not initialized";
        this.depthTarget = null;
        this.source = null;
        this.projection = null;
        this.viewRotation = null;
        this.backend.reset();
    }

    private void deactivate(String reason, @Nullable SceneDepthSource source) {
        this.active = false;
        this.disabledReason = reason;
        this.depthTarget = null;
        this.source = source;
        this.projection = null;
        this.viewRotation = null;
    }

    private OwnerState owner(UUID ownerId) {
        OwnerState state = this.owners.get(ownerId);
        if (state != null) {
            return state;
        }
        if (this.owners.size() >= MAX_OWNERS) {
            this.evictOne();
        }
        state = new OwnerState();
        this.owners.put(ownerId, state);
        return state;
    }

    private void issue(OwnerState state, AABB bounds) {
        if (this.depthTarget == null || this.projection == null || this.viewRotation == null) {
            return;
        }
        this.probesThisFrame++;
        int query;
        try {
            query = this.backend.issue(this.request.set(bounds, this.viewRotation, this.projection,
                this.depthTarget, this.cameraX, this.cameraY, this.cameraZ));
        } catch (RuntimeException exception) {
            this.disable("probe failed: " + exception.getMessage(), exception);
            return;
        }
        if (query == 0) {
            return;
        }
        state.query = query;
        state.queryFrame = this.frameId;
        state.queryRadius = radius(bounds);
        state.queryCameraX = this.cameraX;
        state.queryCameraY = this.cameraY;
        state.queryCameraZ = this.cameraZ;
        state.queryForwardX = this.forwardX;
        state.queryForwardY = this.forwardY;
        state.queryForwardZ = this.forwardZ;
        state.queryMinX = bounds.minX;
        state.queryMinY = bounds.minY;
        state.queryMinZ = bounds.minZ;
        state.queryMaxX = bounds.maxX;
        state.queryMaxY = bounds.maxY;
        state.queryMaxZ = bounds.maxZ;
        this.probesInFlight++;
    }

    private void poll() {
        Iterator<OwnerState> iterator = this.owners.values().iterator();
        while (iterator.hasNext()) {
            OwnerState state = iterator.next();
            if (state.query == 0) {
                continue;
            }
            try {
                if (this.backend.isResultAvailable(state.query)) {
                    boolean passed = this.backend.anySamplesPassed(state.query);
                    this.finish(state, !passed);
                } else if (this.frameId - state.queryFrame > MAX_QUERY_AGE_FRAMES) {
                    this.cancel(state);
                }
            } catch (RuntimeException exception) {
                this.cancel(state);
                this.disable("probe result failed: " + exception.getMessage(), exception);
                return;
            }
        }
    }

    private void finish(OwnerState state, boolean occluded) {
        int query = state.query;
        state.query = 0;
        this.probesInFlight = Math.max(0, this.probesInFlight - 1);
        this.backend.deleteQuery(query);
        state.occluded = occluded;
        state.testedFrame = state.queryFrame;
        state.testRadius = state.queryRadius;
        state.testCameraX = state.queryCameraX;
        state.testCameraY = state.queryCameraY;
        state.testCameraZ = state.queryCameraZ;
        state.testForwardX = state.queryForwardX;
        state.testForwardY = state.queryForwardY;
        state.testForwardZ = state.queryForwardZ;
        state.testMinX = state.queryMinX;
        state.testMinY = state.queryMinY;
        state.testMinZ = state.queryMinZ;
        state.testMaxX = state.queryMaxX;
        state.testMaxY = state.queryMaxY;
        state.testMaxZ = state.queryMaxZ;
    }

    private void cancel(OwnerState state) {
        if (state.query == 0) {
            return;
        }
        int query = state.query;
        state.query = 0;
        state.queryFrame = 0L;
        this.probesInFlight = Math.max(0, this.probesInFlight - 1);
        try {
            this.backend.deleteQuery(query);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not release a depth occlusion query", exception);
        }
    }

    private void invalidateAll() {
        for (OwnerState state : this.owners.values()) {
            this.cancel(state);
            state.occluded = false;
            state.testedFrame = -1L;
        }
    }

    private void disable(String reason, RuntimeException exception) {
        this.active = false;
        this.supported = false;
        this.disabledReason = reason;
        for (OwnerState state : this.owners.values()) {
            this.cancel(state);
            state.occluded = false;
            state.testedFrame = -1L;
        }
        LOGGER.warn("Provider-depth occlusion was disabled for this level: {}", reason, exception);
    }

    private boolean sameCamera(OwnerState state) {
        double dx = this.cameraX - state.testCameraX;
        double dy = this.cameraY - state.testCameraY;
        double dz = this.cameraZ - state.testCameraZ;
        double limit = Math.max(1.0, state.testRadius * 0.02);
        if (dx * dx + dy * dy + dz * dz > limit * limit) {
            return false;
        }
        double dot = this.forwardX * state.testForwardX
            + this.forwardY * state.testForwardY
            + this.forwardZ * state.testForwardZ;
        return dot >= CAMERA_ROTATION_COS;
    }

    private boolean sameBounds(OwnerState state, AABB bounds) {
        double epsilon = Math.max(BOUNDS_EPSILON, state.testRadius * 0.01);
        return Math.abs(bounds.minX - state.testMinX) <= epsilon
            && Math.abs(bounds.minY - state.testMinY) <= epsilon
            && Math.abs(bounds.minZ - state.testMinZ) <= epsilon
            && Math.abs(bounds.maxX - state.testMaxX) <= epsilon
            && Math.abs(bounds.maxY - state.testMaxY) <= epsilon
            && Math.abs(bounds.maxZ - state.testMaxZ) <= epsilon;
    }

    private void cleanup() {
        if (this.frameId - this.lastCleanupFrame < CLEANUP_INTERVAL_FRAMES) {
            return;
        }
        this.lastCleanupFrame = this.frameId;
        Iterator<OwnerState> iterator = this.owners.values().iterator();
        while (iterator.hasNext()) {
            OwnerState state = iterator.next();
            if (state.query == 0 && this.frameId - state.lastSeenFrame > OWNER_IDLE_FRAMES) {
                iterator.remove();
            }
        }
    }

    private void evictOne() {
        Iterator<OwnerState> iterator = this.owners.values().iterator();
        while (iterator.hasNext()) {
            OwnerState state = iterator.next();
            if (state.query == 0) {
                iterator.remove();
                return;
            }
        }
    }

    private static boolean sameTarget(@Nullable RenderTarget left, RenderTarget right) {
        return left == right || (left != null
            && left.frameBufferId == right.frameBufferId
            && left.getDepthTextureId() == right.getDepthTextureId());
    }

    private static boolean usableBounds(AABB bounds) {
        return Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
            && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ)
            && bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY && bounds.minZ <= bounds.maxZ;
    }

    private static double radius(AABB bounds) {
        return Math.max(
            Math.max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY),
            bounds.maxZ - bounds.minZ
        ) * 0.5;
    }

    @SuppressWarnings("PMD.TooManyFields")
    private static final class OwnerState {
        private long lastSeenFrame;
        private long testedFrame = -1L;
        private boolean occluded;
        private double testRadius;
        private double testCameraX;
        private double testCameraY;
        private double testCameraZ;
        private double testForwardX;
        private double testForwardY;
        private double testForwardZ;
        private double testMinX;
        private double testMinY;
        private double testMinZ;
        private double testMaxX;
        private double testMaxY;
        private double testMaxZ;
        private int query;
        private long queryFrame;
        private double queryRadius;
        private double queryCameraX;
        private double queryCameraY;
        private double queryCameraZ;
        private double queryForwardX;
        private double queryForwardY;
        private double queryForwardZ;
        private double queryMinX;
        private double queryMinY;
        private double queryMinZ;
        private double queryMaxX;
        private double queryMaxY;
        private double queryMaxZ;
    }
}
