package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.effect.scale.SegmentedEffectLimits;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Atomic visual snapshot sent when a player joins or changes dimension. The client replaces its
 * local instances for the dimension, so a reconnect cannot leave stale ghosts behind.
 */
public record ResumeEffectsPayload(
    ResourceLocation dimensionId,
    List<StartVisualEffectPayload> effects,
    List<StartSegmentedVisualEffectPayload> segmentedEffects
) implements CustomPacketPayload {
    public static final int MAX_EFFECTS = 128;
    public static final int MAX_SEGMENTED_EFFECTS = SegmentedEffectLimits.MAX_SEGMENTS_PER_LEVEL;
    public static final int MAX_ENCODED_BYTES = EffectPayloadLimits.MAX_RESUME_BYTES;
    public static final Type<ResumeEffectsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "resume_effects")
    );

    public static final StreamCodec<ByteBuf, ResumeEffectsPayload> STREAM_CODEC = StreamCodec.of(
        ResumeEffectsPayload::encode,
        ResumeEffectsPayload::decode
    );

    public ResumeEffectsPayload {
        EffectPayloadLimits.requireIdentifier(dimensionId, "Payload dimension");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(segmentedEffects, "segmentedEffects");
        if (effects.size() > MAX_EFFECTS) {
            throw new IllegalArgumentException("A resume payload supports at most " + MAX_EFFECTS + " effects");
        }
        if (segmentedEffects.size() > MAX_SEGMENTED_EFFECTS) {
            throw new IllegalArgumentException(
                "A resume payload supports at most " + MAX_SEGMENTED_EFFECTS + " segmented effects"
            );
        }
        effects = List.copyOf(effects);
        segmentedEffects = List.copyOf(segmentedEffects);
        for (StartVisualEffectPayload effect : effects) {
            Objects.requireNonNull(effect, "effect");
            if (!effect.dimensionId().equals(dimensionId)) {
                throw new IllegalArgumentException("Resume entries must match the payload dimension");
            }
        }
        for (StartSegmentedVisualEffectPayload effect : segmentedEffects) {
            Objects.requireNonNull(effect, "segmented effect");
            if (!effect.dimensionId().equals(dimensionId)) {
                throw new IllegalArgumentException("Segmented resume entries must match the payload dimension");
            }
        }

        int encodedBytes = EffectPayloadLimits.encodedSize(
            ResourceLocation.STREAM_CODEC, dimensionId
        ) + Integer.BYTES * 2;
        for (StartVisualEffectPayload effect : effects) {
            encodedBytes += EffectPayloadLimits.encodedSize(StartVisualEffectPayload.STREAM_CODEC, effect);
        }
        for (StartSegmentedVisualEffectPayload effect : segmentedEffects) {
            encodedBytes += EffectPayloadLimits.encodedSize(StartSegmentedVisualEffectPayload.STREAM_CODEC, effect);
        }
        if (encodedBytes > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                "A resume payload must encode to at most " + MAX_ENCODED_BYTES + " bytes"
            );
        }
    }

    public ResumeEffectsPayload(ResourceLocation dimensionId, List<StartVisualEffectPayload> effects) {
        this(dimensionId, effects, List.of());
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
        buffer.writeInt(payload.segmentedEffects.size());
        for (StartSegmentedVisualEffectPayload effect : payload.segmentedEffects) {
            StartSegmentedVisualEffectPayload.encode(buffer, effect);
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
        int segmentedSize = buffer.readInt();
        if (segmentedSize < 0 || segmentedSize > MAX_SEGMENTED_EFFECTS) {
            throw new IllegalArgumentException("Invalid segmented resume payload size: " + segmentedSize);
        }
        java.util.ArrayList<StartSegmentedVisualEffectPayload> segmentedEffects =
            new java.util.ArrayList<>(segmentedSize);
        for (int index = 0; index < segmentedSize; index++) {
            segmentedEffects.add(StartSegmentedVisualEffectPayload.decode(buffer));
        }
        return new ResumeEffectsPayload(dimensionId, effects, segmentedEffects);
    }
}
