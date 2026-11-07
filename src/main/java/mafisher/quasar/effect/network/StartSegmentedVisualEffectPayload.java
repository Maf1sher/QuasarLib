package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.scale.SegmentGridLayout;
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
    long seed
) implements CustomPacketPayload {
    public static final Type<StartSegmentedVisualEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "start_segmented_visual_effect")
    );

    public static final StreamCodec<ByteBuf, StartSegmentedVisualEffectPayload> STREAM_CODEC = StreamCodec.of(
        StartSegmentedVisualEffectPayload::encode,
        StartSegmentedVisualEffectPayload::decode
    );

    public StartSegmentedVisualEffectPayload {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(segmentScale) || segmentScale <= 0.0F || segmentScale > 128.0F
            || !Double.isFinite(spacing)
            || durationTicks < 1 || durationTicks > 72_000) {
            throw new IllegalArgumentException("Invalid segmented visual effect payload");
        }
        new SegmentGridLayout(columns, rows, spacing);
    }

    /** Grid geometry shared with the server coordinator. */
    public SegmentGridLayout layout() {
        return new SegmentGridLayout(this.columns, this.rows, this.spacing);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.x, this.y, this.z), this.yaw, this.pitch, this.roll);
    }

    public int segmentCount() {
        return this.columns * this.rows;
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
    }

    static StartSegmentedVisualEffectPayload decode(ByteBuf buffer) {
        return new StartSegmentedVisualEffectPayload(
            ResourceLocation.STREAM_CODEC.decode(buffer),
            UUIDUtil.STREAM_CODEC.decode(buffer),
            ResourceLocation.STREAM_CODEC.decode(buffer),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readUnsignedByte(),
            buffer.readUnsignedByte(),
            buffer.readDouble(),
            buffer.readInt(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong()
        );
    }
}
