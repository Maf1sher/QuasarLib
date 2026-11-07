package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client stop event for every cell of one segmented coordinator. */
public record StopSegmentedVisualEffectPayload(
    UUID coordinatorId,
    ResourceLocation dimensionId
) implements CustomPacketPayload {
    public static final Type<StopSegmentedVisualEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "stop_segmented_visual_effect")
    );

    public static final StreamCodec<ByteBuf, StopSegmentedVisualEffectPayload> STREAM_CODEC = StreamCodec.of(
        StopSegmentedVisualEffectPayload::encode,
        StopSegmentedVisualEffectPayload::decode
    );

    public StopSegmentedVisualEffectPayload {
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        Objects.requireNonNull(dimensionId, "dimensionId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, StopSegmentedVisualEffectPayload payload) {
        UUIDUtil.STREAM_CODEC.encode(buffer, payload.coordinatorId);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
    }

    private static StopSegmentedVisualEffectPayload decode(ByteBuf buffer) {
        return new StopSegmentedVisualEffectPayload(
            UUIDUtil.STREAM_CODEC.decode(buffer),
            ResourceLocation.STREAM_CODEC.decode(buffer)
        );
    }
}
