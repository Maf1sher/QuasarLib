package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client request to inject one screen-compositor failure for the receiving player. */
public record ArmScreenCompositorFailurePayload() implements CustomPacketPayload {
    public static final Type<ArmScreenCompositorFailurePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "arm_screen_compositor_failure")
    );

    public static final StreamCodec<ByteBuf, ArmScreenCompositorFailurePayload> STREAM_CODEC =
        StreamCodec.unit(new ArmScreenCompositorFailurePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
