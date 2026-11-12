package mafisher.quasar.client.effect.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import mafisher.quasar.api.client.VisualEffectInstance;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentHeightProfile;
import mafisher.quasar.api.scale.SegmentLink;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client-owned state for one segmented coordinator. The coordinator's base transform history is stored once; segment
 * transforms are derived only for grid cells that a frame actually needs.
 */
final class ClientSegmentGroup {
    private static final double RANGE_EPSILON = 1.0E-6;

    private final SegmentGridLayout layout;
    private final SegmentHeightProfile heights;
    private final double heightSpread;
    private final double verticalHalfExtent;
    private final SegmentMember[] cells;
    private final double halfDiagonal;
    private EffectTransform previousBase;
    private EffectTransform currentBase;
    private long previousBaseGameTime;
    private long currentBaseGameTime;
    private double maxSpatialRadius;
    private int memberCount;

    ClientSegmentGroup(SegmentGridLayout layout, EffectTransform initialBase, long initialGameTime) {
        this(layout, SegmentHeightProfile.NONE, initialBase, initialGameTime);
    }

    ClientSegmentGroup(
        SegmentGridLayout layout,
        SegmentHeightProfile heights,
        EffectTransform initialBase,
        long initialGameTime
    ) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.heights = Objects.requireNonNull(heights, "heights");
        if (!heights.isEmpty() && !heights.matches(layout.columns(), layout.rows())) {
            throw new IllegalArgumentException("Height profile does not match the grid columns");
        }
        this.heightSpread = heights.isEmpty() ? 0.0 : heights.spread();
        this.verticalHalfExtent = (layout.layers() - 1) * layout.layerSpacing() / 2.0;
        this.cells = new SegmentMember[layout.segmentCount()];
        this.previousBase = Objects.requireNonNull(initialBase, "initialBase");
        this.currentBase = initialBase;
        this.previousBaseGameTime = initialGameTime;
        this.currentBaseGameTime = initialGameTime;
        this.halfDiagonal = layout.halfDiagonal();
    }

    /**
     * Recovers the coordinator base from one absolute segment transform. The server sends segment transforms only, but
     * the layout and cell offset are enough to rebuild the shared base.
     */
    static EffectTransform baseFromSegment(SegmentLink link, EffectTransform segmentTransform) {
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(segmentTransform, "segmentTransform");
        Vec3 offset = link.layout().offset(
            link.column(), link.row(), link.layer(), segmentTransform.yaw()
        );
        EffectPoint position = segmentTransform.position();
        return new EffectTransform(
            new EffectPoint(position.x() - offset.x, position.y() - offset.y, position.z() - offset.z),
            segmentTransform.yaw(),
            segmentTransform.pitch(),
            segmentTransform.roll()
        );
    }

    SegmentGridLayout layout() {
        return this.layout;
    }

    boolean add(SegmentMember member) {
        Objects.requireNonNull(member, "member");
        if (!this.layout.equals(member.link().layout())) {
            throw new IllegalArgumentException("Segment link layout does not match its coordinator group");
        }
        int index = member.link().segmentIndex();
        if (this.cells[index] != null) {
            this.memberCount--;
        }
        this.cells[index] = member;
        this.memberCount++;
        if (Double.isFinite(this.maxSpatialRadius)) {
            this.maxSpatialRadius = Math.max(this.maxSpatialRadius, member.spatialRadius());
        }
        return this.memberCount > 0;
    }

    SegmentMember remove(SegmentLink link) {
        Objects.requireNonNull(link, "link");
        int index = link.segmentIndex();
        SegmentMember existing = this.cells[index];
        if (existing == null) {
            return null;
        }
        if (!existing.link().equals(link)) {
            return null;
        }
        this.cells[index] = null;
        this.memberCount--;
        this.recomputeMaxSpatialRadius();
        return existing;
    }

    private void recomputeMaxSpatialRadius() {
        double maximum = 0.0;
        for (SegmentMember member : this.cells) {
            if (member != null) {
                maximum = Math.max(maximum, member.spatialRadius());
            }
        }
        this.maxSpatialRadius = maximum;
    }

    boolean isEmpty() {
        return this.memberCount == 0;
    }

    /** Snapshot of every member instance ID, used when a whole coordinator is removed. */
    List<UUID> memberIds() {
        List<UUID> ids = new ArrayList<>(this.memberCount);
        for (SegmentMember member : this.cells) {
            if (member != null) {
                ids.add(member.instanceId());
            }
        }
        return ids;
    }

    int memberCount() {
        return this.memberCount;
    }

    double maxSpatialRadius() {
        return this.maxSpatialRadius;
    }

    /** Radius around the base position that contains every segment and every rotation of the grid. */
    double footprintRadius() {
        if (!Double.isFinite(this.maxSpatialRadius)) {
            return Double.POSITIVE_INFINITY;
        }
        return this.maxSpatialRadius + this.halfDiagonal;
    }

    boolean isIndexable() {
        return ClientEffectSpatialIndex.isIndexableRadius(this.footprintRadius());
    }

    /** Applies one aggregated coordinator update without touching any segment instance. */
    void applyBase(EffectTransform transform, long gameTime) {
        Objects.requireNonNull(transform, "transform");
        if (gameTime < this.currentBaseGameTime) {
            return;
        }
        if (gameTime == this.currentBaseGameTime) {
            this.currentBase = transform;
            return;
        }
        this.previousBase = this.currentBase;
        this.previousBaseGameTime = this.currentBaseGameTime;
        this.currentBase = transform;
        this.currentBaseGameTime = gameTime;
    }

    /** Interpolated coordinator base for one frame; audio uses this for transform-following cues. */
    EffectTransform interpolatedBase(long gameTime, float partialTick) {
        if (this.previousBaseGameTime == this.currentBaseGameTime) {
            return this.currentBase;
        }
        double renderGameTime = gameTime + (double)partialTick - 1.0;
        float progress = (float)((renderGameTime - this.previousBaseGameTime)
            / (this.currentBaseGameTime - this.previousBaseGameTime));
        return EffectTransform.interpolate(this.previousBase, this.currentBase, progress);
    }

    /** Conservative world bound for the index entry, covering both base endpoints and the interpolation path. */
    AABB indexBounds() {
        double radius = this.footprintRadius();
        if (!Double.isFinite(radius)) {
            return AABB.INFINITE;
        }
        double dx = this.currentBase.position().x() - this.previousBase.position().x();
        double dy = this.currentBase.position().y() - this.previousBase.position().y();
        double dz = this.currentBase.position().z() - this.previousBase.position().z();
        double movement = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double x = (this.previousBase.position().x() + this.currentBase.position().x()) / 2.0;
        double y = (this.previousBase.position().y() + this.currentBase.position().y()) / 2.0;
        double z = (this.previousBase.position().z() + this.currentBase.position().z()) / 2.0;
        double heightDeviation = this.heights.isEmpty() ? 0.0 : this.heights.maxAbsDeviation(y);
        double total = radius + heightDeviation + movement / 2.0;
        return new AABB(x - total, y - total, z - total, x + total, y + total, z + total);
    }

    /** Builds the render-time instance for one segment; the stored start instance remains a payload carrier. */
    VisualEffectInstance renderInstance(SegmentMember member) {
        Objects.requireNonNull(member, "member");
        SegmentLink link = member.link();
        EffectTransform from = this.layout.transform(
            this.previousBase, link.column(), link.row(), link.layer(), this.heights
        );
        EffectTransform to = this.layout.transform(
            this.currentBase, link.column(), link.row(), link.layer(), this.heights
        );
        if (this.previousBaseGameTime == this.currentBaseGameTime) {
            return new VisualEffectInstance(member.payload(), to, to, this.currentBaseGameTime,
                this.currentBaseGameTime);
        }
        return new VisualEffectInstance(member.payload(), from, to, this.previousBaseGameTime,
            this.currentBaseGameTime);
    }

    /**
     * Visits cells whose segment could intersect {@code query} at any point between the previous and current base.
     * The range is deliberately conservative: it includes both endpoint layouts and a movement/rotation margin.
     */
    void forEachCellIn(AABB query, Consumer<SegmentMember> consumer) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(consumer, "consumer");
        if (!Double.isFinite(this.maxSpatialRadius) || query.isInfinite()) {
            this.forEachCell(consumer);
            return;
        }

        double baseMovement = Math.sqrt(
            square(this.currentBase.position().x() - this.previousBase.position().x())
                + square(this.currentBase.position().y() - this.previousBase.position().y())
                + square(this.currentBase.position().z() - this.previousBase.position().z())
        );
        double yawDelta = shortestAngle(this.currentBase.yaw() - this.previousBase.yaw());
        double rotationMovement = 2.0 * this.halfDiagonal
            * Math.abs(Math.sin(Math.toRadians(yawDelta) / 2.0));
        AABB expanded = query.inflate(
            (baseMovement + rotationMovement) / 2.0 + this.maxSpatialRadius + this.heightSpread
        );

        double verticalMin;
        double verticalMax;
        if (this.heights.isEmpty()) {
            double baseMin = Math.min(this.previousBase.position().y(), this.currentBase.position().y());
            double baseMax = Math.max(this.previousBase.position().y(), this.currentBase.position().y());
            verticalMin = baseMin - this.verticalHalfExtent;
            verticalMax = baseMax + this.verticalHalfExtent;
        } else {
            verticalMin = this.heights.minimumY() - this.verticalHalfExtent;
            verticalMax = this.heights.maximumY() + this.verticalHalfExtent;
        }
        if (expanded.maxY < verticalMin || expanded.minY > verticalMax) {
            return;
        }

        int[] range = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
        this.accumulateRange(expanded, this.previousBase, range);
        this.accumulateRange(expanded, this.currentBase, range);
        int minColumn = Math.max(0, range[0]);
        int maxColumn = Math.min(this.layout.columns() - 1, range[1]);
        int minRow = Math.max(0, range[2]);
        int maxRow = Math.min(this.layout.rows() - 1, range[3]);
        if (minColumn > maxColumn || minRow > maxRow) {
            return;
        }

        for (int layer = 0; layer < this.layout.layers(); layer++) {
            for (int row = minRow; row <= maxRow; row++) {
                for (int column = minColumn; column <= maxColumn; column++) {
                    SegmentMember member = this.cells[this.layout.index(column, row, layer)];
                    if (member != null) {
                        consumer.accept(member);
                    }
                }
            }
        }
    }

    private void accumulateRange(AABB query, EffectTransform base, int[] range) {
        double yaw = Math.toRadians(base.yaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double minLocalX = Double.POSITIVE_INFINITY;
        double maxLocalX = Double.NEGATIVE_INFINITY;
        double minLocalZ = Double.POSITIVE_INFINITY;
        double maxLocalZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < 4; index++) {
            double x = (index & 1) == 0 ? query.minX : query.maxX;
            double z = (index & 2) == 0 ? query.minZ : query.maxZ;
            double dx = x - base.position().x();
            double dz = z - base.position().z();
            double localX = dx * cos + dz * sin;
            double localZ = -dx * sin + dz * cos;
            minLocalX = Math.min(minLocalX, localX);
            maxLocalX = Math.max(maxLocalX, localX);
            minLocalZ = Math.min(minLocalZ, localZ);
            maxLocalZ = Math.max(maxLocalZ, localZ);
        }

        double spacing = this.layout.spacing();
        if (spacing <= 0.0) {
            range[0] = Math.min(range[0], 0);
            range[1] = Math.max(range[1], this.layout.columns() - 1);
            range[2] = Math.min(range[2], 0);
            range[3] = Math.max(range[3], this.layout.rows() - 1);
            return;
        }

        double columnCenter = (this.layout.columns() - 1) / 2.0;
        double rowCenter = (this.layout.rows() - 1) / 2.0;
        int minColumn = (int)Math.ceil(minLocalX / spacing + columnCenter - RANGE_EPSILON);
        int maxColumn = (int)Math.floor(maxLocalX / spacing + columnCenter + RANGE_EPSILON);
        int minRow = (int)Math.ceil(minLocalZ / spacing + rowCenter - RANGE_EPSILON);
        int maxRow = (int)Math.floor(maxLocalZ / spacing + rowCenter + RANGE_EPSILON);
        range[0] = Math.min(range[0], minColumn);
        range[1] = Math.max(range[1], maxColumn);
        range[2] = Math.min(range[2], minRow);
        range[3] = Math.max(range[3], maxRow);
    }

    private void forEachCell(Consumer<SegmentMember> consumer) {
        for (SegmentMember member : this.cells) {
            if (member != null) {
                consumer.accept(member);
            }
        }
    }

    private static double square(double value) {
        return value * value;
    }

    private static float shortestAngle(float difference) {
        float wrapped = difference % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    record SegmentMember(UUID instanceId, StartVisualEffectPayload payload, SegmentLink link, double spatialRadius) {
        SegmentMember {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(link, "link");
            if (Double.isNaN(spatialRadius) || spatialRadius < 0.0) {
                throw new IllegalArgumentException("Invalid segment spatial radius");
            }
        }
    }
}
