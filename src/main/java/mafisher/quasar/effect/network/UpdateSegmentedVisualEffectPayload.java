package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Server-to-client aggregated transform update for every segment of one coordinator. The client rebuilds each segment
 * transform from this base transform and the layout it received at start, so one packet covers the whole grid.
 */
@ApiStatus.Internal
public record UpdateSegmentedVisualEffectPayload(
    UUID coordinatorId,
    ResourceLocation dimensionId,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    float roll,
    long updateGameTime
) implements CustomPacketPayload {
    public static final Type<UpdateSegmentedVisualEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "update_segmented_visual_effect")
    );

    public static final StreamCodec<ByteBuf, UpdateSegmentedVisualEffectPayload> STREAM_CODEC = StreamCodec.of(
        UpdateSegmentedVisualEffectPayload::encode,
        UpdateSegmentedVisualEffectPayload::decode
    );

    public UpdateSegmentedVisualEffectPayload {
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("Invalid segmented visual effect update");
        }
    }

    public static UpdateSegmentedVisualEffectPayload of(UUID coordinatorId, ResourceLocation dimensionId,
        EffectTransform transform, long updateGameTime) {
        Objects.requireNonNull(transform, "transform");
        EffectPoint position = transform.position();
        return new UpdateSegmentedVisualEffectPayload(coordinatorId, dimensionId, position.x(), position.y(),
            position.z(), transform.yaw(), transform.pitch(), transform.roll(), updateGameTime);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.x, this.y, this.z), this.yaw, this.pitch, this.roll);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, UpdateSegmentedVisualEffectPayload payload) {
        UUIDUtil.STREAM_CODEC.encode(buffer, payload.coordinatorId);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeFloat(payload.yaw);
        buffer.writeFloat(payload.pitch);
        buffer.writeFloat(payload.roll);
        buffer.writeLong(payload.updateGameTime);
    }

    private static UpdateSegmentedVisualEffectPayload decode(ByteBuf buffer) {
        return new UpdateSegmentedVisualEffectPayload(
            UUIDUtil.STREAM_CODEC.decode(buffer),
            ResourceLocation.STREAM_CODEC.decode(buffer),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readLong()
        );
    }
}
