package mafisher.quasar.effect.network;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.QuasarLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cancels the client-side sound schedule for one cinematic session. */
public record StopCinematicAudioPayload(UUID sessionId, ResourceLocation dimensionId) implements CustomPacketPayload {
    public static final Type<StopCinematicAudioPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "stop_cinematic_audio")
    );
    public static final StreamCodec<FriendlyByteBuf, StopCinematicAudioPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeUUID(payload.sessionId());
            buffer.writeResourceLocation(payload.dimensionId());
        },
        buffer -> new StopCinematicAudioPayload(buffer.readUUID(), buffer.readResourceLocation())
    );

    public StopCinematicAudioPayload {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(dimensionId, "dimensionId");
    }

    @Override
    public Type<StopCinematicAudioPayload> type() {
        return TYPE;
    }
}
