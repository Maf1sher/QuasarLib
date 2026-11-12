package mafisher.quasar.effect.scale;

import java.util.Objects;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectLimits;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authored parameters for a segmented, giant-scale world effect. The spec is only a layout: cell placement,
 * identity, and seeds are derived from it, and the coordinator delivers the whole grid in one aggregated lifecycle.
 * Segmented cells use the dedicated segment budget instead of standalone visual slots.
 *
 * <p>Segments are placed on a {@code columns x rows x layers} grid centered on {@code center}. The grid is rotated by
 * {@code yaw}; pitch and roll are inherited by every segment but do not tilt the grid. When {@code terrain} is not
 * {@link SegmentTerrainConform#NONE}, the server samples one height per column at start and bakes the result into the
 * aggregated start payload.
 */
public record SegmentedVisualEffectSpec(
    ResourceLocation effectId,
    Vec3 center,
    float yaw,
    float pitch,
    float roll,
    float segmentScale,
    SegmentGridLayout grid,
    int durationTicks,
    long seed,
    EffectAudience audience,
    EffectAudioSpec audio,
    EffectPriority priority,
    SegmentTerrainConform terrain
) {
    /** Shared per-level ceiling. A coordinator cannot reserve more than this many segments. */
    public static final int MAX_SEGMENTS = SegmentGridLayout.MAX_SEGMENTS;

    public SegmentedVisualEffectSpec {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(terrain, "terrain");
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(segmentScale) || segmentScale <= 0.0F
            || segmentScale > EffectLimits.MAX_SCALE
            || !EffectTimeline.isValidDuration(durationTicks)) {
            throw new IllegalArgumentException("Invalid segmented visual effect parameters");
        }
        audio.requireFitsWithin(durationTicks);
    }

    /** Starts a builder with the required fields; optional options keep their current defaults. */
    public static Builder builder(
        ResourceLocation effectId,
        Vec3 center,
        int durationTicks,
        long seed,
        EffectAudience audience
    ) {
        return new Builder(effectId, center, durationTicks, seed, audience);
    }

    public VisualEffectSpec segmentSpec(int column, int row) {
        return this.segmentSpec(column, row, 0);
    }

    public VisualEffectSpec segmentSpec(int column, int row, int layer) {
        this.requireCell(column, row, layer);
        Vec3 position = this.center.add(this.segmentOffset(column, row, layer, this.yaw));
        return new VisualEffectSpec(
            this.effectId,
            position,
            this.yaw,
            this.pitch,
            this.roll,
            this.segmentScale,
            this.durationTicks,
            this.segmentSeed(column, row, layer),
            this.audience,
            EffectAudioSpec.NONE,
            this.priority
        );
    }

    /** Returns a copy centered at {@code center}, preserving every other authored option. */
    public SegmentedVisualEffectSpec withCenter(Vec3 center) {
        Objects.requireNonNull(center, "center");
        return new SegmentedVisualEffectSpec(
            this.effectId,
            center,
            this.yaw,
            this.pitch,
            this.roll,
            this.segmentScale,
            this.grid,
            this.durationTicks,
            this.seed,
            this.audience,
            this.audio,
            this.priority,
            this.terrain
        );
    }

    /** Grid geometry shared with the client, so both sides place segments identically. */
    public SegmentGridLayout layout() {
        return this.grid;
    }

    public int columns() {
        return this.grid.columns();
    }

    public int rows() {
        return this.grid.rows();
    }

    public int layers() {
        return this.grid.layers();
    }

    public double spacing() {
        return this.grid.spacing();
    }

    public double layerSpacing() {
        return this.grid.layerSpacing();
    }

    /** Offset from the root center to one segment in the bottom layer, rotated by {@code yaw}. */
    public Vec3 segmentOffset(int column, int row, float yaw) {
        return this.segmentOffset(column, row, 0, yaw);
    }

    /** Offset from the root center to one segment, including its layer height. */
    public Vec3 segmentOffset(int column, int row, int layer, float yaw) {
        this.requireCell(column, row, layer);
        return this.layout().offset(column, row, layer, yaw);
    }

    /** Deterministic per-segment seed derived from the root seed and the cell in the bottom layer. */
    public long segmentSeed(int column, int row) {
        return this.segmentSeed(column, row, 0);
    }

    /** Deterministic per-segment seed derived from the root seed and the 3D segment cell. */
    public long segmentSeed(int column, int row, int layer) {
        this.requireCell(column, row, layer);
        return SegmentIds.segmentSeed(this.seed, column, row, layer);
    }

    public int segmentCount() {
        return this.grid.segmentCount();
    }

    private void requireCell(int column, int row, int layer) {
        if (column < 0 || column >= this.columns() || row < 0 || row >= this.rows()
            || layer < 0 || layer >= this.layers()) {
            throw new IndexOutOfBoundsException("Segment cell is outside the configured grid");
        }
    }

    /** Builder for grid, terrain, audio, priority, and rotation without a long positional list. */
    public static final class Builder {
        private final ResourceLocation effectId;
        private final Vec3 center;
        private final int durationTicks;
        private final long seed;
        private final EffectAudience audience;
        private float yaw;
        private float pitch;
        private float roll;
        private float segmentScale = 1.0F;
        private int columns = 1;
        private int rows = 1;
        private int layers = 1;
        private double spacing;
        private double layerSpacing;
        private SegmentGridLayout explicitGrid;
        private EffectAudioSpec audio = EffectAudioSpec.NONE;
        private EffectPriority priority = EffectPriority.NORMAL;
        private SegmentTerrainConform terrain = SegmentTerrainConform.NONE;

        private Builder(
            ResourceLocation effectId,
            Vec3 center,
            int durationTicks,
            long seed,
            EffectAudience audience
        ) {
            this.effectId = effectId;
            this.center = center;
            this.durationTicks = durationTicks;
            this.seed = seed;
            this.audience = audience;
        }

        public Builder rotation(float yaw, float pitch, float roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            return this;
        }

        public Builder segmentScale(float segmentScale) {
            this.segmentScale = segmentScale;
            return this;
        }

        public Builder grid(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
            return this;
        }

        public Builder grid(SegmentGridLayout grid) {
            this.explicitGrid = Objects.requireNonNull(grid, "grid");
            return this;
        }

        public Builder spacing(double spacing) {
            this.spacing = spacing;
            return this;
        }

        public Builder layers(int layers, double layerSpacing) {
            this.layers = layers;
            this.layerSpacing = layerSpacing;
            return this;
        }

        public Builder audio(EffectAudioSpec audio) {
            this.audio = audio;
            return this;
        }

        public Builder priority(EffectPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder terrain(SegmentTerrainConform terrain) {
            this.terrain = terrain;
            return this;
        }

        public SegmentedVisualEffectSpec build() {
            SegmentGridLayout grid = this.explicitGrid == null
                ? new SegmentGridLayout(this.columns, this.rows, this.layers, this.spacing, this.layerSpacing)
                : this.explicitGrid;
            return new SegmentedVisualEffectSpec(
                this.effectId,
                this.center,
                this.yaw,
                this.pitch,
                this.roll,
                this.segmentScale,
                grid,
                this.durationTicks,
                this.seed,
                this.audience,
                this.audio,
                this.priority,
                this.terrain
            );
        }
    }
}
