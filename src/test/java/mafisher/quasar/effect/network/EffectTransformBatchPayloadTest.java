package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectTransformBatchPayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsVisualAndSegmentedUpdates() {
        EffectTransformBatchPayload.Entry visual = entry(false, 1.0, 64.0, -3.0);
        EffectTransformBatchPayload.Entry segmented = entry(true, 2.0, 65.0, -4.0);
        EffectTransformBatchPayload payload = new EffectTransformBatchPayload(
            DIMENSION, 120L, List.of(visual, segmented)
        );

        EffectTransformBatchPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertFalse(decoded.updates().getFirst().segmented());
        assertTrue(decoded.updates().getLast().segmented());
        assertEquals(120L, decoded.updateGameTime());
    }

    @Test
    void rejectsAnEmptyOrOversizedBatch() {
        EffectTransformBatchPayload.Entry entry = entry(false, 0.0, 0.0, 0.0);

        assertThrows(IllegalArgumentException.class, () -> new EffectTransformBatchPayload(
            DIMENSION, 0L, List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EffectTransformBatchPayload(
            DIMENSION,
            0L,
            Collections.nCopies(EffectTransformBatchPayload.MAX_UPDATES + 1, entry)
        ));
    }

    @Test
    void staysWithinTheDocumentedEncodedByteCapWithMaximumIdentifiers() {
        EffectTransformBatchPayload payload = new EffectTransformBatchPayload(
            widestIdentifier(),
            123L,
            updates(EffectTransformBatchPayload.MAX_UPDATES)
        );

        int encodedBytes = EffectPayloadLimits.encodedSize(
            EffectTransformBatchPayload.STREAM_CODEC, payload
        );

        assertTrue(encodedBytes <= EffectPayloadLimits.MAX_BATCH_BYTES,
            "transform batch encoded to " + encodedBytes + " bytes");
    }

    @Test
    void rejectsNonFiniteTransforms() {
        assertThrows(IllegalArgumentException.class, () -> new EffectTransformBatchPayload.Entry(
            UUID.randomUUID(), false, Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 0.0F
        ));
    }

    private static List<EffectTransformBatchPayload.Entry> updates(int count) {
        List<EffectTransformBatchPayload.Entry> updates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            updates.add(entry(index % 2 == 0, index, 64.0 + index, -index));
        }
        return updates;
    }

    private static EffectTransformBatchPayload.Entry entry(boolean segmented, double x, double y, double z) {
        return new EffectTransformBatchPayload.Entry(
            UUID.randomUUID(), segmented, x, y, z, 15.0F, 20.0F, 25.0F
        );
    }

    private static ResourceLocation widestIdentifier() {
        return ResourceLocation.fromNamespaceAndPath("a".repeat(128), "b".repeat(127));
    }

    private static EffectTransformBatchPayload roundTrip(EffectTransformBatchPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            EffectTransformBatchPayload.STREAM_CODEC.encode(buffer, payload);
            return EffectTransformBatchPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
