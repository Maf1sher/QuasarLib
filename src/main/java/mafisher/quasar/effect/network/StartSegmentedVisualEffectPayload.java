package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import mafisher.quasar.effect.EffectLimits;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.scale.SegmentGridLayout;
import mafisher.quasar.effect.scale.SegmentHeightProfile;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client start event for one segmented coordinator. The client derives every cell identity, seed, and
 * transform from this payload, so a grid start costs one packet instead of one per segment.
 */
public record StartSegmentedVisualEffectPayload(
    ResourceLocation effectId,
    UUID coordinatorId,
    ResourceLocation dimensionId,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    float roll,
    float segmentScale,
    int columns,
    int rows,
    double spacing,
    int durationTicks,
    long startGameTime,
    long serverGameTime,
    long seed,
    int layers,
    double layerSpacing,
    SegmentHeightProfile heights
) implements CustomPacketPayload {
    public static final Type<StartSegmentedVisualEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "start_segmented_visual_effect")
    );

    public static final StreamCodec<ByteBuf, StartSegmentedVisualEffectPayload> STREAM_CODEC = StreamCodec.of(
        StartSegmentedVisualEffectPayload::encode,
        StartSegmentedVisualEffectPayload::decode
    );

    public StartSegmentedVisualEffectPayload {
        EffectPayloadLimits.requireIdentifier(effectId, "Effect ID");
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        EffectPayloadLimits.requireIdentifier(dimensionId, "Payload dimension");
        Objects.requireNonNull(heights, "heights");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(segmentScale) || segmentScale <= 0.0F
            || segmentScale > EffectLimits.MAX_SCALE
            || !EffectTimeline.isValidDuration(durationTicks)) {
            throw new IllegalArgumentException("Invalid segmented visual effect payload");
        }
        new SegmentGridLayout(columns, rows, layers, spacing, layerSpacing);
        if (!heights.isEmpty() && !heights.matches(columns, rows)) {
            throw new IllegalArgumentException("Height profile does not match the grid columns");
        }
    }

    /** Source-compatible constructor for a flat, single-layer grid without terrain heights. */
    public StartSegmentedVisualEffectPayload(
        ResourceLocation effectId,
        UUID coordinatorId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float segmentScale,
        int columns,
        int rows,
        double spacing,
        int durationTicks,
        long startGameTime,
        long serverGameTime,
        long seed
    ) {
        this(effectId, coordinatorId, dimensionId, x, y, z, yaw, pitch, roll, segmentScale, columns, rows, spacing,
            durationTicks, startGameTime, serverGameTime, seed, 1, 0.0, SegmentHeightProfile.NONE);
    }

    /** Layered grid without terrain heights. */
    public StartSegmentedVisualEffectPayload(
        ResourceLocation effectId,
        UUID coordinatorId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float segmentScale,
        int columns,
        int rows,
        double spacing,
        int durationTicks,
        long startGameTime,
        long serverGameTime,
        long seed,
        int layers,
        double layerSpacing
    ) {
        this(effectId, coordinatorId, dimensionId, x, y, z, yaw, pitch, roll, segmentScale, columns, rows, spacing,
            durationTicks, startGameTime, serverGameTime, seed, layers, layerSpacing, SegmentHeightProfile.NONE);
    }

    /** Grid geometry shared with the server coordinator. */
    public SegmentGridLayout layout() {
        return new SegmentGridLayout(this.columns, this.rows, this.layers, this.spacing, this.layerSpacing);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.x, this.y, this.z), this.yaw, this.pitch, this.roll);
    }

    public int segmentCount() {
        return this.columns * this.rows * this.layers;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void encode(ByteBuf buffer, StartSegmentedVisualEffectPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.effectId);
        UUIDUtil.STREAM_CODEC.encode(buffer, payload.coordinatorId);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeFloat(payload.yaw);
        buffer.writeFloat(payload.pitch);
        buffer.writeFloat(payload.roll);
        buffer.writeFloat(payload.segmentScale);
        buffer.writeByte(payload.columns);
        buffer.writeByte(payload.rows);
        buffer.writeDouble(payload.spacing);
        buffer.writeInt(payload.durationTicks);
        buffer.writeLong(payload.startGameTime);
        buffer.writeLong(payload.serverGameTime);
        buffer.writeLong(payload.seed);
        buffer.writeByte(payload.layers);
        buffer.writeDouble(payload.layerSpacing);
        if (payload.heights.isEmpty()) {
            buffer.writeInt(0);
        } else {
            buffer.writeInt(payload.heights.size());
            for (double value : payload.heights.values()) {
                buffer.writeDouble(value);
            }
        }
    }

    static StartSegmentedVisualEffectPayload decode(ByteBuf buffer) {
        ResourceLocation effectId = ResourceLocation.STREAM_CODEC.decode(buffer);
        UUID coordinatorId = UUIDUtil.STREAM_CODEC.decode(buffer);
        ResourceLocation dimensionId = ResourceLocation.STREAM_CODEC.decode(buffer);
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        float yaw = buffer.readFloat();
        float pitch = buffer.readFloat();
        float roll = buffer.readFloat();
        float segmentScale = buffer.readFloat();
        int columns = buffer.readUnsignedByte();
        int rows = buffer.readUnsignedByte();
        double spacing = buffer.readDouble();
        int durationTicks = buffer.readInt();
        long startGameTime = buffer.readLong();
        long serverGameTime = buffer.readLong();
        long seed = buffer.readLong();
        int layers = buffer.readUnsignedByte();
        double layerSpacing = buffer.readDouble();
        int heightCount = buffer.readInt();
        SegmentHeightProfile heights;
        if (heightCount == 0) {
            heights = SegmentHeightProfile.NONE;
        } else {
            if (heightCount < 0 || heightCount > SegmentGridLayout.MAX_SEGMENTS) {
                throw new IllegalArgumentException("Invalid segment height count");
            }
            double[] values = new double[heightCount];
            for (int index = 0; index < heightCount; index++) {
                values[index] = buffer.readDouble();
            }
            if (heightCount != columns * rows) {
                throw new IllegalArgumentException("Height count does not match the grid columns");
            }
            heights = SegmentHeightProfile.of(columns, rows, values);
        }
        return new StartSegmentedVisualEffectPayload(
            effectId, coordinatorId, dimensionId, x, y, z, yaw, pitch, roll, segmentScale,
            columns, rows, spacing, durationTicks, startGameTime, serverGameTime, seed,
            layers, layerSpacing, heights
        );
    }
}
