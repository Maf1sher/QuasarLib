package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Coalesced server-to-client transform update for one player. One payload carries every visual and
 * segmented coordinator that moved during the server tick, so update traffic no longer scales with
 * the number of tracked effects. Batches above {@link #MAX_UPDATES} are split by the sender.
 */
@ApiStatus.Internal
public record EffectTransformBatchPayload(
    ResourceLocation dimensionId,
    long updateGameTime,
    List<Entry> updates
) implements CustomPacketPayload {
    public static final int MAX_UPDATES = EffectPayloadLimits.MAX_BATCH_UPDATES;
    public static final Type<EffectTransformBatchPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "effect_transform_batch")
    );

    public static final StreamCodec<ByteBuf, EffectTransformBatchPayload> STREAM_CODEC = StreamCodec.of(
        EffectTransformBatchPayload::encode,
        EffectTransformBatchPayload::decode
    );

    public EffectTransformBatchPayload {
        EffectPayloadLimits.requireIdentifier(dimensionId, "Payload dimension");
        Objects.requireNonNull(updates, "updates");
        if (updates.isEmpty() || updates.size() > MAX_UPDATES) {
            throw new IllegalArgumentException(
                "A transform batch must contain between 1 and " + MAX_UPDATES + " updates"
            );
        }
        for (Entry update : updates) {
            Objects.requireNonNull(update, "update");
        }
        updates = List.copyOf(updates);
    }

    /** One transformed effect instance; {@code segmented} selects the coordinator update path. */
    public record Entry(
        UUID instanceId,
        boolean segmented,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll
    ) {
        public Entry {
            Objects.requireNonNull(instanceId, "instanceId");
            new EffectTransform(new EffectPoint(x, y, z), yaw, pitch, roll);
        }

        public EffectTransform transform() {
            return new EffectTransform(new EffectPoint(this.x, this.y, this.z), this.yaw, this.pitch, this.roll);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, EffectTransformBatchPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
        buffer.writeLong(payload.updateGameTime);
        buffer.writeInt(payload.updates.size());
        for (Entry update : payload.updates) {
            UUIDUtil.STREAM_CODEC.encode(buffer, update.instanceId);
            buffer.writeBoolean(update.segmented);
            buffer.writeDouble(update.x);
            buffer.writeDouble(update.y);
            buffer.writeDouble(update.z);
            buffer.writeFloat(update.yaw);
            buffer.writeFloat(update.pitch);
            buffer.writeFloat(update.roll);
        }
    }

    private static EffectTransformBatchPayload decode(ByteBuf buffer) {
        ResourceLocation dimensionId = ResourceLocation.STREAM_CODEC.decode(buffer);
        long updateGameTime = buffer.readLong();
        int size = buffer.readInt();
        if (size < 1 || size > MAX_UPDATES) {
            throw new IllegalArgumentException("Invalid transform batch size: " + size);
        }
        ArrayList<Entry> updates = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            updates.add(new Entry(
                UUIDUtil.STREAM_CODEC.decode(buffer),
                buffer.readBoolean(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
            ));
        }
        return new EffectTransformBatchPayload(dimensionId, updateGameTime, updates);
    }
}
