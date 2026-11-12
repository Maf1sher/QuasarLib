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
    int columns,
    int rows,
    double spacing,
    int durationTicks,
    long seed,
    EffectAudience audience,
    EffectAudioSpec audio,
    EffectPriority priority,
    int layers,
    double layerSpacing,
    SegmentTerrainConform terrain
) {
    /** Shared per-level ceiling. A coordinator cannot reserve more than this many segments. */
    public static final int MAX_SEGMENTS = SegmentGridLayout.MAX_SEGMENTS;

    public SegmentedVisualEffectSpec {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(terrain, "terrain");
        new SegmentGridLayout(columns, rows, layers, spacing, layerSpacing);
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(segmentScale) || segmentScale <= 0.0F
            || segmentScale > EffectLimits.MAX_SCALE
            || !EffectTimeline.isValidDuration(durationTicks)) {
            throw new IllegalArgumentException("Invalid segmented visual effect parameters");
        }
        audio.requireFitsWithin(durationTicks);
    }

    /** Source-compatible constructor for effects that use the default priority and a flat single-layer grid. */
    public SegmentedVisualEffectSpec(
        ResourceLocation effectId,
        Vec3 center,
        float yaw,
        float pitch,
        float roll,
        float segmentScale,
        int columns,
        int rows,
        double spacing,
        int durationTicks,
        long seed,
        EffectAudience audience,
        EffectAudioSpec audio
    ) {
        this(effectId, center, yaw, pitch, roll, segmentScale, columns, rows, spacing, durationTicks, seed, audience,
            audio, EffectPriority.NORMAL, 1, 0.0, SegmentTerrainConform.NONE);
    }

    /** Source-compatible constructor for effects without audio or an explicit priority. */
    public SegmentedVisualEffectSpec(
        ResourceLocation effectId,
        Vec3 center,
        float yaw,
        float pitch,
        float roll,
        float segmentScale,
        int columns,
        int rows,
        double spacing,
        int durationTicks,
        long seed,
        EffectAudience audience
    ) {
        this(effectId, center, yaw, pitch, roll, segmentScale, columns, rows, spacing, durationTicks, seed, audience,
            EffectAudioSpec.NONE, EffectPriority.NORMAL, 1, 0.0, SegmentTerrainConform.NONE);
    }

    /** Source-compatible constructor with an explicit priority but no vertical layers. */
    public SegmentedVisualEffectSpec(
        ResourceLocation effectId,
        Vec3 center,
        float yaw,
        float pitch,
        float roll,
        float segmentScale,
        int columns,
        int rows,
        double spacing,
        int durationTicks,
        long seed,
        EffectAudience audience,
        EffectAudioSpec audio,
        EffectPriority priority
    ) {
        this(effectId, center, yaw, pitch, roll, segmentScale, columns, rows, spacing, durationTicks, seed, audience,
            audio, priority, 1, 0.0, SegmentTerrainConform.NONE);
    }

    /** Layered grid without terrain alignment. */
    public SegmentedVisualEffectSpec(
        ResourceLocation effectId,
        Vec3 center,
        float yaw,
        float pitch,
        float roll,
        float segmentScale,
        int columns,
        int rows,
        double spacing,
        int durationTicks,
        long seed,
        EffectAudience audience,
        EffectAudioSpec audio,
        EffectPriority priority,
        int layers,
        double layerSpacing
    ) {
        this(effectId, center, yaw, pitch, roll, segmentScale, columns, rows, spacing, durationTicks, seed, audience,
            audio, priority, layers, layerSpacing, SegmentTerrainConform.NONE);
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

    /** Grid geometry shared with the client, so both sides place segments identically. */
    public SegmentGridLayout layout() {
        return new SegmentGridLayout(this.columns, this.rows, this.layers, this.spacing, this.layerSpacing);
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
        return this.columns * this.rows * this.layers;
    }

    private void requireCell(int column, int row, int layer) {
        if (column < 0 || column >= this.columns || row < 0 || row >= this.rows
            || layer < 0 || layer >= this.layers) {
            throw new IndexOutOfBoundsException("Segment cell is outside the configured grid");
        }
    }
}
