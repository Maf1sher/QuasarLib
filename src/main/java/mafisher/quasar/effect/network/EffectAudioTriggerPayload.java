package mafisher.quasar.effect.network;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.audio.EffectAudioTrigger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Fires one named effect audio trigger on clients that already hold the effect's audio plan.
 * The position is resolved on the server so runtime events can play at an impact point.
 */
public record EffectAudioTriggerPayload(
    UUID effectId,
    ResourceLocation dimensionId,
    String triggerId,
    Vec3 position,
    long serverGameTime
) implements CustomPacketPayload {
    public static final Type<EffectAudioTriggerPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "effect_audio_trigger")
    );
    public static final StreamCodec<FriendlyByteBuf, EffectAudioTriggerPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeUUID(payload.effectId());
            buffer.writeResourceLocation(payload.dimensionId());
            buffer.writeUtf(payload.triggerId(), EffectAudioTrigger.MAX_ID_LENGTH);
            buffer.writeDouble(payload.position().x);
            buffer.writeDouble(payload.position().y);
            buffer.writeDouble(payload.position().z);
            buffer.writeLong(payload.serverGameTime());
        },
        buffer -> new EffectAudioTriggerPayload(
            buffer.readUUID(),
            buffer.readResourceLocation(),
            buffer.readUtf(EffectAudioTrigger.MAX_ID_LENGTH),
            new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
            buffer.readLong()
        )
    );

    public EffectAudioTriggerPayload {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(triggerId, "triggerId");
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("Effect audio trigger position must be finite");
        }
    }

    @Override
    public Type<EffectAudioTriggerPayload> type() {
        return TYPE;
    }
}
