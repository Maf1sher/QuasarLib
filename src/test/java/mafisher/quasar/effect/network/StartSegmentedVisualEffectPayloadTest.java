package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StartSegmentedVisualEffectPayloadTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsAGridStart() {
        StartSegmentedVisualEffectPayload payload = payload(16, 8);

        StartSegmentedVisualEffectPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertEquals(128, decoded.segmentCount());
        assertEquals(8.0, decoded.layout().spacing());
    }

    @Test
    void oneAggregatedStartIsMuchSmallerThanPerCellStarts() {
        StartSegmentedVisualEffectPayload payload = payload(16, 8);
        StartVisualEffectPayload cell = new StartVisualEffectPayload(
            EFFECT_ID, UUID.randomUUID(), DIMENSION, 0.0, 64.0, 0.0,
            0.0F, 0.0F, 0.0F, 4.0F, 200, 100L, 140L, 42L, null
        );

        assertTrue(encodedSize(payload) < encodedSize(cell) * 4,
            "one grid start should not scale with cell count");
    }

    @Test
    void rejectsInvalidGridParameters() {
        assertThrows(IllegalArgumentException.class, () -> payload(0, 1));
        assertThrows(IllegalArgumentException.class, () -> payload(17, 8));
        assertThrows(IllegalArgumentException.class, () -> payload(2, 2, 0.0));
        assertThrows(IllegalArgumentException.class, () -> payload(2, 2, 8.0, 0.0F));
    }

    private static StartSegmentedVisualEffectPayload payload(int columns, int rows) {
        return payload(columns, rows, columns * rows > 1 ? 8.0 : 0.0, 4.0F);
    }

    private static StartSegmentedVisualEffectPayload payload(int columns, int rows, double spacing) {
        return payload(columns, rows, spacing, 4.0F);
    }

    private static StartSegmentedVisualEffectPayload payload(
        int columns,
        int rows,
        double spacing,
        float segmentScale
    ) {
        return new StartSegmentedVisualEffectPayload(
            EFFECT_ID,
            UUID.randomUUID(),
            DIMENSION,
            1.0,
            64.0,
            -3.0,
            15.0F,
            20.0F,
            25.0F,
            segmentScale,
            columns,
            rows,
            spacing,
            200,
            100L,
            140L,
            42L
        );
    }

    private static StartSegmentedVisualEffectPayload roundTrip(StartSegmentedVisualEffectPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            StartSegmentedVisualEffectPayload.STREAM_CODEC.encode(buffer, payload);
            return StartSegmentedVisualEffectPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static int encodedSize(StartVisualEffectPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            StartVisualEffectPayload.STREAM_CODEC.encode(buffer, payload);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    private static int encodedSize(StartSegmentedVisualEffectPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            StartSegmentedVisualEffectPayload.STREAM_CODEC.encode(buffer, payload);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }
}
