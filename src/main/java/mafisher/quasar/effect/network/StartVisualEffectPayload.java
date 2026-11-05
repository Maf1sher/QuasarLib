package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client start event for one short-lived, client-rendered effect. */
public record StartVisualEffectPayload(
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
            || !Float.isFinite(yaw) || !Float.isFinite(pitch)
            || !Float.isFinite(scale) || scale <= 0.0F
            || durationTicks < 1 || durationTicks > 72_000) {
            throw new IllegalArgumentException("Invalid visual effect payload");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, StartVisualEffectPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.effectId);
        UUIDUtil.STREAM_CODEC.encode(buffer, payload.instanceId);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeFloat(payload.yaw);
        buffer.writeFloat(payload.pitch);
        buffer.writeFloat(payload.scale);
        buffer.writeInt(payload.durationTicks);
        buffer.writeLong(payload.startGameTime);
        buffer.writeLong(payload.seed);
    }

    private static StartVisualEffectPayload decode(ByteBuf buffer) {
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
            buffer.readInt(),
            buffer.readLong(),
            buffer.readLong()
        );
    }
}
