package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.scale.SegmentHeightProfile;
import mafisher.quasar.api.scale.StartSegmentedVisualEffectPayload;
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
    void roundTripsLayersAndTerrainHeights() {
        SegmentHeightProfile heights = SegmentHeightProfile.of(2, 2, new double[] {64.0, 66.0, 68.0, 70.0});
        StartSegmentedVisualEffectPayload payload = new StartSegmentedVisualEffectPayload(
            EFFECT_ID, UUID.randomUUID(), DIMENSION, 1.0, 64.0, -3.0,
            15.0F, 20.0F, 25.0F, 4.0F, 2, 2, 8.0, 200, 100L, 140L, 42L,
            3, 5.0, heights
        );

        StartSegmentedVisualEffectPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertEquals(12, decoded.segmentCount());
        assertEquals(3, decoded.layers());
        assertEquals(5.0, decoded.layerSpacing());
        assertEquals(70.0, decoded.heights().y(1, 1));
    }

    @Test
    void staysWithinTheDocumentedEncodedByteCapWithWidestIdentifiers() {
        ResourceLocation widest = ResourceLocation.fromNamespaceAndPath(
            "a".repeat(128), "b".repeat(127)
        );
        double[] values = new double[128];
        for (int index = 0; index < values.length; index++) {
            values[index] = 64.0 + index;
        }
        StartSegmentedVisualEffectPayload payload = new StartSegmentedVisualEffectPayload(
            widest, UUID.randomUUID(), widest, 1.0, 64.0, -3.0,
            15.0F, 20.0F, 25.0F, 128.0F, 16, 8, 8.0, 200, 100L, 140L, 42L,
            1, 0.0, SegmentHeightProfile.of(16, 8, values)
        );

        assertTrue(encodedSize(payload) <= EffectPayloadLimits.MAX_SEGMENTED_START_BYTES,
            "segmented start encoded to " + encodedSize(payload) + " bytes");
    }

    @Test
    void rejectsMismatchedHeightProfiles() {
        SegmentHeightProfile heights = SegmentHeightProfile.of(2, 2, new double[] {64.0, 66.0, 68.0, 70.0});

        assertThrows(IllegalArgumentException.class, () -> new StartSegmentedVisualEffectPayload(
            EFFECT_ID, UUID.randomUUID(), DIMENSION, 1.0, 64.0, -3.0,
            15.0F, 20.0F, 25.0F, 4.0F, 2, 1, 8.0, 200, 100L, 140L, 42L,
            1, 0.0, heights
        ));
    }

    @Test
    void flatStartsCarryNoHeightData() {
        StartSegmentedVisualEffectPayload payload = payload(2, 2);

        StartSegmentedVisualEffectPayload decoded = roundTrip(payload);

        assertTrue(decoded.heights().isEmpty());
        assertEquals(4, decoded.segmentCount());
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
