package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class UpdateSegmentedVisualEffectPayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsOneAggregatedUpdate() {
        UpdateSegmentedVisualEffectPayload payload = new UpdateSegmentedVisualEffectPayload(
            UUID.randomUUID(), DIMENSION, 10.0, 70.0, -5.0, 45.0F, 10.0F, 5.0F, 1234L
        );

        UpdateSegmentedVisualEffectPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertEquals(payload.transform(), decoded.transform());
    }

    @Test
    void buildsFromATransform() {
        EffectTransform transform = new EffectTransform(new EffectPoint(1.0, 2.0, 3.0), 15.0F, 0.0F, 0.0F);
        UUID coordinatorId = UUID.randomUUID();

        UpdateSegmentedVisualEffectPayload payload =
            UpdateSegmentedVisualEffectPayload.of(coordinatorId, DIMENSION, transform, 99L);

        assertEquals(coordinatorId, payload.coordinatorId());
        assertEquals(transform, payload.transform());
        assertEquals(99L, payload.updateGameTime());
    }

    @Test
    void rejectsNonFiniteTransforms() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateSegmentedVisualEffectPayload(
            UUID.randomUUID(), DIMENSION, Double.NaN, 64.0, 0.0, 0.0F, 0.0F, 0.0F, 0L
        ));
        assertThrows(IllegalArgumentException.class, () -> new UpdateSegmentedVisualEffectPayload(
            UUID.randomUUID(), DIMENSION, 0.0, 64.0, 0.0, Float.POSITIVE_INFINITY, 0.0F, 0.0F, 0L
        ));
    }

    @Test
    void oneUpdateStaysFarBelowTheOldPerSegmentCost() {
        UpdateSegmentedVisualEffectPayload payload = new UpdateSegmentedVisualEffectPayload(
            UUID.randomUUID(), DIMENSION, 10.0, 70.0, -5.0, 45.0F, 10.0F, 5.0F, 1234L
        );
        ByteBuf buffer = Unpooled.buffer();
        try {
            UpdateSegmentedVisualEffectPayload.STREAM_CODEC.encode(buffer, payload);
            assertTrue(buffer.readableBytes() <= 80,
                "aggregated update was " + buffer.readableBytes() + " bytes");
        } finally {
            buffer.release();
        }
    }

    private static UpdateSegmentedVisualEffectPayload roundTrip(UpdateSegmentedVisualEffectPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            UpdateSegmentedVisualEffectPayload.STREAM_CODEC.encode(buffer, payload);
            return UpdateSegmentedVisualEffectPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
