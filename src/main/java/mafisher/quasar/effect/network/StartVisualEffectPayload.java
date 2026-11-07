package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.scale.SegmentGridLayout;
import mafisher.quasar.effect.scale.SegmentLink;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client start event for one short-lived, client-rendered effect.
 *
 * @param startGameTime absolute server game time at which the effect started
 * @param serverGameTime server game time when this payload was created; clients use it to
 *     calibrate their own monotonic server timeline
 */
public record StartVisualEffectPayload(
    ResourceLocation effectId,
    UUID instanceId,
    ResourceLocation dimensionId,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    float roll,
    float scale,
    int durationTicks,
    long startGameTime,
    long serverGameTime,
    long seed,
    SegmentLink segmentLink
) implements CustomPacketPayload {
    public static final Type<StartVisualEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "start_visual_effect")
    );

    public static final StreamCodec<ByteBuf, StartVisualEffectPayload> STREAM_CODEC = StreamCodec.of(
        StartVisualEffectPayload::encode,
        StartVisualEffectPayload::decode
    );

    public StartVisualEffectPayload {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(scale) || scale <= 0.0F
            || durationTicks < 1 || durationTicks > 72_000) {
            throw new IllegalArgumentException("Invalid visual effect payload");
        }
    }

    public StartVisualEffectPayload(
        ResourceLocation effectId,
        UUID instanceId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float scale,
        int durationTicks,
        long startGameTime,
        long seed,
        SegmentLink segmentLink
    ) {
        this(effectId, instanceId, dimensionId, x, y, z, yaw, pitch, roll, scale, durationTicks, startGameTime,
            startGameTime, seed, segmentLink);
    }

    public StartVisualEffectPayload(
        ResourceLocation effectId,
        UUID instanceId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float scale,
        int durationTicks,
        long startGameTime,
        long seed
    ) {
        this(effectId, instanceId, dimensionId, x, y, z, yaw, pitch, roll, scale, durationTicks, startGameTime, seed,
            null);
    }

    public StartVisualEffectPayload(
        ResourceLocation effectId,
        UUID instanceId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float scale,
        int durationTicks,
        long startGameTime,
        long seed
    ) {
        this(effectId, instanceId, dimensionId, x, y, z, yaw, pitch, 0.0F, scale, durationTicks, startGameTime, seed,
            null);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.x, this.y, this.z),
            this.yaw, this.pitch, this.roll);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void encode(ByteBuf buffer, StartVisualEffectPayload payload) {        ResourceLocation.STREAM_CODEC.encode(buffer, payload.effectId);
        UUIDUtil.STREAM_CODEC.encode(buffer, payload.instanceId);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeFloat(payload.yaw);
        buffer.writeFloat(payload.pitch);
        buffer.writeFloat(payload.roll);
        buffer.writeFloat(payload.scale);
        buffer.writeInt(payload.durationTicks);
        buffer.writeLong(payload.startGameTime);
        buffer.writeLong(payload.serverGameTime);
        buffer.writeLong(payload.seed);
        SegmentLink link = payload.segmentLink;
        buffer.writeBoolean(link != null);
        if (link != null) {
            UUIDUtil.STREAM_CODEC.encode(buffer, link.coordinatorId());
            buffer.writeByte(link.segmentIndex());
            buffer.writeByte(link.layout().columns());
            buffer.writeByte(link.layout().rows());
            buffer.writeByte(link.layout().layers());
            buffer.writeDouble(link.layout().spacing());
            buffer.writeDouble(link.layout().layerSpacing());
        }
    }

    static StartVisualEffectPayload decode(ByteBuf buffer) {
        return new StartVisualEffectPayload(
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
            buffer.readInt(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong(),
            decodeSegmentLink(buffer)
        );
    }

    private static SegmentLink decodeSegmentLink(ByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        UUID coordinatorId = UUIDUtil.STREAM_CODEC.decode(buffer);
        int segmentIndex = buffer.readUnsignedByte();
        int columns = buffer.readUnsignedByte();
        int rows = buffer.readUnsignedByte();
        int layers = buffer.readUnsignedByte();
        double spacing = buffer.readDouble();
        double layerSpacing = buffer.readDouble();
        return new SegmentLink(coordinatorId, segmentIndex,
            new SegmentGridLayout(columns, rows, layers, spacing, layerSpacing));
    }
}
