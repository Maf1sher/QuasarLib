package mafisher.quasar.effect.network;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.QuasarLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Stops one client-side effect audio plan early. */
public record StopEffectAudioPayload(UUID effectId, ResourceLocation dimensionId)
    implements CustomPacketPayload {
    public static final Type<StopEffectAudioPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "stop_effect_audio")
    );
    public static final StreamCodec<FriendlyByteBuf, StopEffectAudioPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeUUID(payload.effectId());
            buffer.writeResourceLocation(payload.dimensionId());
        },
        buffer -> new StopEffectAudioPayload(buffer.readUUID(), buffer.readResourceLocation())
    );

    public StopEffectAudioPayload {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(dimensionId, "dimensionId");
    }

    @Override
    public Type<StopEffectAudioPayload> type() {
        return TYPE;
    }
}
