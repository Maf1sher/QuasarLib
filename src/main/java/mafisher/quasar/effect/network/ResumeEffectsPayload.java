package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Atomic visual snapshot sent when a player joins or changes dimension. The client replaces its
 * local instances for the dimension, so a reconnect cannot leave stale ghosts behind.
 */
public record ResumeEffectsPayload(
    ResourceLocation dimensionId,
    List<StartVisualEffectPayload> effects
) implements CustomPacketPayload {
    public static final int MAX_EFFECTS = 128;
    public static final Type<ResumeEffectsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "resume_effects")
    );

    public static final StreamCodec<ByteBuf, ResumeEffectsPayload> STREAM_CODEC = StreamCodec.of(
        ResumeEffectsPayload::encode,
        ResumeEffectsPayload::decode
    );

    public ResumeEffectsPayload {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(effects, "effects");
        if (effects.size() > MAX_EFFECTS) {
            throw new IllegalArgumentException("A resume payload supports at most " + MAX_EFFECTS + " effects");
        }
        effects = List.copyOf(effects);
        for (StartVisualEffectPayload effect : effects) {
            Objects.requireNonNull(effect, "effect");
            if (!effect.dimensionId().equals(dimensionId)) {
                throw new IllegalArgumentException("Resume entries must match the payload dimension");
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, ResumeEffectsPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
        buffer.writeInt(payload.effects.size());
        for (StartVisualEffectPayload effect : payload.effects) {
            StartVisualEffectPayload.encode(buffer, effect);
        }
    }

    private static ResumeEffectsPayload decode(ByteBuf buffer) {
        ResourceLocation dimensionId = ResourceLocation.STREAM_CODEC.decode(buffer);
        int size = buffer.readInt();
        if (size < 0 || size > MAX_EFFECTS) {
            throw new IllegalArgumentException("Invalid resume payload size: " + size);
        }
        java.util.ArrayList<StartVisualEffectPayload> effects = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            effects.add(StartVisualEffectPayload.decode(buffer));
        }
        return new ResumeEffectsPayload(dimensionId, effects);
    }
}
