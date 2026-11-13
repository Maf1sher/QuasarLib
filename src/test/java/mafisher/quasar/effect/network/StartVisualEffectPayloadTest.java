package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentLink;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StartVisualEffectPayloadTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsAStandaloneEffect() {
        StartVisualEffectPayload payload = standalone();

        StartVisualEffectPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertNull(decoded.segmentLink());
        assertNull(decoded.attachment());
        assertEquals(140L, decoded.serverGameTime());
    }

    @Test
    void roundTripsAnAttachment() {
        EffectAttachment attachment = EffectAttachment.toEntity(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            new EffectPoint(1.0, 2.0, 3.0),
            4.0F,
            5.0F,
            6.0F
        ).withRotationInheritance(EffectAttachment.RotationInheritance.YAW_ONLY)
            .withParentLossPolicy(EffectAttachment.ParentLossPolicy.FREEZE_AT_LAST);
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            EFFECT_ID, UUID.randomUUID(), DIMENSION, 1.0, 64.0, 2.0,
            0.0F, 0.0F, 0.0F, 2.0F, 200, 100L, 140L, 42L, null, attachment
        );

        StartVisualEffectPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertEquals(attachment, decoded.attachment());
    }

    @Test
    void roundTripsASegmentLink() {
        SegmentLink link = new SegmentLink(
            UUID.randomUUID(), 17, new SegmentGridLayout(4, 3, 2, 8.0, 4.0)
        );
        StartVisualEffectPayload payload = withLink(link);

        StartVisualEffectPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertEquals(link, decoded.segmentLink());
        assertEquals(1, decoded.segmentLink().row());
        assertEquals(1, decoded.segmentLink().column());
        assertEquals(1, decoded.segmentLink().layer());
    }

    @Test
    void rejectsScaleAboveTheDocumentedMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new StartVisualEffectPayload(
            EFFECT_ID, UUID.randomUUID(), DIMENSION, 0.0, 64.0, 0.0,
            0.0F, 0.0F, 0.0F, 129.0F, 200, 100L, 42L
        ));
    }

    @Test
    void rejectsAnOversizedIdentifier() {
        ResourceLocation oversized = ResourceLocation.fromNamespaceAndPath(
            "a".repeat(128), "b".repeat(128)
        );

        assertThrows(IllegalArgumentException.class, () -> new StartVisualEffectPayload(
            oversized, UUID.randomUUID(), DIMENSION, 0.0, 64.0, 0.0,
            0.0F, 0.0F, 0.0F, 1.0F, 200, 100L, 42L
        ));
    }

    @Test
    void staysWithinTheDocumentedEncodedByteCapWithWidestIdentifiers() {
        ResourceLocation widest = ResourceLocation.fromNamespaceAndPath(
            "a".repeat(128), "b".repeat(127)
        );
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            widest,
            UUID.randomUUID(),
            widest,
            1.0,
            64.0,
            2.0,
            0.0F,
            0.0F,
            0.0F,
            128.0F,
            200,
            100L,
            140L,
            42L,
            new SegmentLink(UUID.randomUUID(), 127, new SegmentGridLayout(8, 8, 2, 8.0, 4.0)),
            EffectAttachment.toEntity(
                UUID.randomUUID(),
                new EffectPoint(1.0, 2.0, 3.0),
                4.0F,
                5.0F,
                6.0F
            )
        );

        assertTrue(encodedSize(payload) <= EffectPayloadLimits.MAX_VISUAL_START_BYTES,
            "visual start encoded to " + encodedSize(payload) + " bytes");
    }

    @Test
    void aSegmentLinkAddsOnlyAFewBytes() {
        int standaloneSize = encodedSize(standalone());
        int linkedSize = encodedSize(withLink(new SegmentLink(
            UUID.randomUUID(), 0, new SegmentGridLayout(8, 8, 4.0)
        )));

        assertTrue(linkedSize - standaloneSize <= 48,
            "segment link overhead was " + (linkedSize - standaloneSize) + " bytes");
    }

    private static StartVisualEffectPayload standalone() {
        return new StartVisualEffectPayload(EFFECT_ID, UUID.randomUUID(), DIMENSION, 1.0, 64.0, 2.0,
            0.0F, 0.0F, 0.0F, 2.0F, 200, 100L, 140L, 42L, null);
    }

    private static StartVisualEffectPayload withLink(SegmentLink link) {
        return new StartVisualEffectPayload(EFFECT_ID, UUID.randomUUID(), DIMENSION, 1.0, 64.0, 2.0,
            0.0F, 0.0F, 0.0F, 2.0F, 200, 100L, 140L, 42L, link);
    }

    private static StartVisualEffectPayload roundTrip(StartVisualEffectPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            StartVisualEffectPayload.STREAM_CODEC.encode(buffer, payload);
            return StartVisualEffectPayload.STREAM_CODEC.decode(buffer);
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
}
