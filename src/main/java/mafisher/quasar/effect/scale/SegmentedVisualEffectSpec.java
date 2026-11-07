package mafisher.quasar.effect.scale;

import java.util.Objects;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authored parameters for a segmented, giant-scale world effect. The spec is only a layout: cell placement,
 * identity, and seeds are derived from it, and the coordinator delivers the whole grid in one aggregated lifecycle.
 * Segmented cells use the dedicated segment budget instead of standalone visual slots.
 *
 * <p>Segments are placed on a horizontal {@code columns x rows} grid centered on {@code center}. The grid is rotated
 * by {@code yaw}; pitch and roll are inherited by every segment but do not tilt the grid.
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
    EffectPriority priority
) {
    /** Shared per-level ceiling. A coordinator cannot reserve more than this many segments. */
    public static final int MAX_SEGMENTS = SegmentGridLayout.MAX_SEGMENTS;

    public SegmentedVisualEffectSpec {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(priority, "priority");
        long segmentCount = (long)columns * rows;
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(segmentScale) || segmentScale <= 0.0F || segmentScale > 128.0F
            || columns < 1 || rows < 1 || segmentCount > MAX_SEGMENTS
            || !Double.isFinite(spacing) || spacing < 0.0
            || (segmentCount > 1 && spacing <= 0.0)
            || durationTicks < 1 || durationTicks > 72_000) {
            throw new IllegalArgumentException("Invalid segmented visual effect parameters");
        }
        audio.requireFitsWithin(durationTicks);
    }

    /** Source-compatible constructor for effects that use the default priority. */
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
            audio, EffectPriority.NORMAL);
    }

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
            EffectAudioSpec.NONE, EffectPriority.NORMAL);
    }

    public VisualEffectSpec segmentSpec(int column, int row) {
        requireCell(column, row);
        Vec3 position = this.center.add(segmentOffset(column, row, this.yaw));
        return new VisualEffectSpec(
            this.effectId,
            position,
            this.yaw,
            this.pitch,
            this.roll,
            this.segmentScale,
            this.durationTicks,
            this.segmentSeed(column, row),
            this.audience,
            EffectAudioSpec.NONE,
            this.priority
        );
    }

    /** Grid geometry shared with the client, so both sides place segments identically. */
    public SegmentGridLayout layout() {
        return new SegmentGridLayout(this.columns, this.rows, this.spacing);
    }

    /** Horizontal offset from the root center to one segment, rotated by {@code yaw}. */
    public Vec3 segmentOffset(int column, int row, float yaw) {
        requireCell(column, row);
        return this.layout().offset(column, row, yaw);
    }

    /** Deterministic per-segment seed derived from the root seed and the segment cell. */
    public long segmentSeed(int column, int row) {
        requireCell(column, row);
        return SegmentIds.segmentSeed(this.seed, column, row);
    }

    public int segmentCount() {
        return this.columns * this.rows;
    }

    private void requireCell(int column, int row) {
        if (column < 0 || column >= this.columns || row < 0 || row >= this.rows) {
            throw new IndexOutOfBoundsException("Segment cell is outside the configured grid");
        }
    }
}
