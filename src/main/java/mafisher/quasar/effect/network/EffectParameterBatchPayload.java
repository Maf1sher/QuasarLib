package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.api.effect.EffectParameterState;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Coalesced server-to-client parameter update for one player.
 *
 * <p>The state in each entry is the full authoritative override set, not a copy of every sampled
 * curve value. Curves are reconstructed from progress on the client; this payload only carries
 * server-authored changes and is sent at most once per player per server tick.</p>
 */
@ApiStatus.Internal
public record EffectParameterBatchPayload(
    ResourceLocation dimensionId,
    long updateGameTime,
    List<Entry> updates
) implements CustomPacketPayload {
    public static final int MAX_UPDATES = EffectPayloadLimits.MAX_PARAMETER_UPDATES;
    public static final int MAX_ENCODED_BYTES = EffectPayloadLimits.MAX_PARAMETER_BATCH_BYTES;
    private static final int MAX_NAME_BYTES = 256;
    public static final Type<EffectParameterBatchPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "effect_parameter_batch")
    );

    public static final StreamCodec<ByteBuf, EffectParameterBatchPayload> STREAM_CODEC = StreamCodec.of(
        EffectParameterBatchPayload::encode,
        EffectParameterBatchPayload::decode
    );

    public EffectParameterBatchPayload {
        EffectPayloadLimits.requireIdentifier(dimensionId, "Payload dimension");
        Objects.requireNonNull(updates, "updates");
        if (updates.isEmpty() || updates.size() > MAX_UPDATES) {
            throw new IllegalArgumentException(
                "A parameter batch must contain between 1 and " + MAX_UPDATES + " updates"
            );
        }
        for (Entry update : updates) {
            Objects.requireNonNull(update, "update");
        }
        updates = List.copyOf(updates);
        ByteBuf buffer = Unpooled.buffer();
        try {
            encodeFields(buffer, dimensionId, updateGameTime, updates);
            if (buffer.readableBytes() > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                    "A parameter batch must encode to at most " + MAX_ENCODED_BYTES + " bytes"
                );
            }
        } finally {
            buffer.release();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, EffectParameterBatchPayload payload) {
        encodeFields(buffer, payload.dimensionId, payload.updateGameTime, payload.updates);
    }

    private static void encodeFields(
        ByteBuf buffer,
        ResourceLocation dimensionId,
        long updateGameTime,
        List<Entry> updates
    ) {
        ResourceLocation.STREAM_CODEC.encode(buffer, dimensionId);
        buffer.writeLong(updateGameTime);
        buffer.writeInt(updates.size());
        for (Entry update : updates) {
            UUIDUtil.STREAM_CODEC.encode(buffer, update.instanceId());
            encodeState(buffer, update.state());
        }
    }

    private static EffectParameterBatchPayload decode(ByteBuf buffer) {
        ResourceLocation dimensionId = ResourceLocation.STREAM_CODEC.decode(buffer);
        long updateGameTime = buffer.readLong();
        int size = buffer.readInt();
        if (size < 1 || size > MAX_UPDATES) {
            throw new IllegalArgumentException("Invalid parameter batch size: " + size);
        }
        ArrayList<Entry> updates = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            updates.add(new Entry(UUIDUtil.STREAM_CODEC.decode(buffer), decodeState(buffer)));
        }
        return new EffectParameterBatchPayload(dimensionId, updateGameTime, updates);
    }

    private static void encodeState(ByteBuf buffer, EffectParameterState state) {
        buffer.writeInt(state.size());
        for (var entry : state.values().entrySet()) {
            EffectCurve.Value value = entry.getValue();
            byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
            if (name.length > MAX_NAME_BYTES) {
                throw new IllegalArgumentException("Effect parameter name is too long");
            }
            buffer.writeInt(name.length);
            buffer.writeBytes(name);
            buffer.writeByte(value.type().ordinal());
            buffer.writeFloat(value.x());
            buffer.writeFloat(value.y());
            buffer.writeFloat(value.z());
            buffer.writeFloat(value.w());
        }
    }

    private static EffectParameterState decodeState(ByteBuf buffer) {
        int size = buffer.readInt();
        if (size < 0 || size > EffectParameterState.MAX_VALUES) {
            throw new IllegalArgumentException("Invalid parameter state size: " + size);
        }
        EffectParameterState.Builder state = EffectParameterState.builder();
        EffectCurve.ValueType[] types = EffectCurve.ValueType.values();
        for (int index = 0; index < size; index++) {
            int nameLength = buffer.readInt();
            if (nameLength < 0 || nameLength > MAX_NAME_BYTES) {
                throw new IllegalArgumentException("Invalid effect parameter name length: " + nameLength);
            }
            String name = buffer.toString(buffer.readerIndex(), nameLength, StandardCharsets.UTF_8);
            buffer.skipBytes(nameLength);
            int typeIndex = buffer.readUnsignedByte();
            if (typeIndex >= types.length) {
                throw new IllegalArgumentException("Invalid effect parameter value type: " + typeIndex);
            }
            state.put(name, new EffectCurve.Value(
                types[typeIndex],
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
            ));
        }
        return state.build();
    }

    /** One instance and its full authoritative override state. */
    public record Entry(UUID instanceId, EffectParameterState state) {
        public Entry {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(state, "state");
        }
    }
}
