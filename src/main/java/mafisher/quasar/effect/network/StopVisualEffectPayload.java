package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Server-to-client cancellation event for a visual effect that has not naturally expired. */
@ApiStatus.Internal
public record StopVisualEffectPayload(UUID instanceId, ResourceLocation dimensionId)
    implements CustomPacketPayload {
    public static final Type<StopVisualEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "stop_visual_effect")
    );

    public static final StreamCodec<ByteBuf, StopVisualEffectPayload> STREAM_CODEC = StreamCodec.of(
        StopVisualEffectPayload::encode,
        StopVisualEffectPayload::decode
    );

    public StopVisualEffectPayload {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(dimensionId, "dimensionId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, StopVisualEffectPayload payload) {
        UUIDUtil.STREAM_CODEC.encode(buffer, payload.instanceId);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
    }

    private static StopVisualEffectPayload decode(ByteBuf buffer) {
        return new StopVisualEffectPayload(
            UUIDUtil.STREAM_CODEC.decode(buffer),
            ResourceLocation.STREAM_CODEC.decode(buffer)
        );
    }
}
