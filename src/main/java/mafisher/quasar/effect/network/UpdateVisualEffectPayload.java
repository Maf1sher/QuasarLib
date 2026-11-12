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

/** Server-to-client transform update for an active visual effect. */
@ApiStatus.Internal
public record UpdateVisualEffectPayload(
    UUID instanceId,
    ResourceLocation dimensionId,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    float roll,
    long updateGameTime
) implements CustomPacketPayload {
    public static final Type<UpdateVisualEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "update_visual_effect")
    );

    public static final StreamCodec<ByteBuf, UpdateVisualEffectPayload> STREAM_CODEC = StreamCodec.of(
        UpdateVisualEffectPayload::encode,
        UpdateVisualEffectPayload::decode
    );

    public UpdateVisualEffectPayload {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("Invalid visual effect transform update");
        }
    }

    public static UpdateVisualEffectPayload of(UUID instanceId, ResourceLocation dimensionId,
        EffectTransform transform, long updateGameTime) {
        Objects.requireNonNull(transform, "transform");
        EffectPoint position = transform.position();
        return new UpdateVisualEffectPayload(instanceId, dimensionId, position.x(), position.y(), position.z(),
            transform.yaw(), transform.pitch(), transform.roll(), updateGameTime);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.x, this.y, this.z), this.yaw, this.pitch, this.roll);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, UpdateVisualEffectPayload payload) {
        UUIDUtil.STREAM_CODEC.encode(buffer, payload.instanceId);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeFloat(payload.yaw);
        buffer.writeFloat(payload.pitch);
        buffer.writeFloat(payload.roll);
        buffer.writeLong(payload.updateGameTime);
    }

    private static UpdateVisualEffectPayload decode(ByteBuf buffer) {
        return new UpdateVisualEffectPayload(
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
