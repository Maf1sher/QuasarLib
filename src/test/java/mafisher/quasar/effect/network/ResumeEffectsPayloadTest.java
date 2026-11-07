package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ResumeEffectsPayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath("quasar_content", "rune");

    @Test
    void roundTripsVisualSnapshots() {
        StartVisualEffectPayload first = start(0L, 200);
        StartVisualEffectPayload second = start(40L, 80);
        ResumeEffectsPayload payload = new ResumeEffectsPayload(DIMENSION, List.of(first, second));

        ResumeEffectsPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertEquals(2, decoded.effects().size());
        assertEquals(40L, decoded.effects().get(1).startGameTime());
    }

    @Test
    void roundTripsSegmentedSnapshots() {
        StartSegmentedVisualEffectPayload segmented = new StartSegmentedVisualEffectPayload(
            EFFECT,
            UUID.randomUUID(),
            DIMENSION,
            1.0,
            64.0,
            -3.0,
            15.0F,
            20.0F,
            25.0F,
            4.0F,
            4,
            4,
            8.0,
            200,
            40L,
            55L,
            99L
        );
        ResumeEffectsPayload payload = new ResumeEffectsPayload(
            DIMENSION, List.of(start(0L, 200)), List.of(segmented)
        );

        ResumeEffectsPayload decoded = roundTrip(payload);

        assertEquals(payload, decoded);
        assertEquals(1, decoded.segmentedEffects().size());
        assertEquals(4, decoded.segmentedEffects().getFirst().columns());
        assertEquals(55L, decoded.segmentedEffects().getFirst().serverGameTime());
    }

    @Test
    void rejectsSegmentedEntriesFromAnotherDimension() {
        StartSegmentedVisualEffectPayload entry = new StartSegmentedVisualEffectPayload(
            EFFECT,
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"),
            0.0, 64.0, 0.0, 0.0F, 0.0F, 0.0F, 1.0F,
            1, 1, 0.0, 100, 0L, 0L, 1L
        );

        assertThrows(IllegalArgumentException.class,
            () -> new ResumeEffectsPayload(DIMENSION, List.of(), List.of(entry)));
    }

    @Test
    void rejectsEntriesFromAnotherDimension() {
        StartVisualEffectPayload entry = new StartVisualEffectPayload(
            EFFECT,
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"),
            0.0, 64.0, 0.0, 0.0F, 0.0F, 0.0F, 1.0F, 100, 0L, 1L
        );

        assertThrows(IllegalArgumentException.class,
            () -> new ResumeEffectsPayload(DIMENSION, List.of(entry)));
    }

    private static StartVisualEffectPayload start(long startGameTime, int durationTicks) {
        return new StartVisualEffectPayload(
            EFFECT,
            UUID.randomUUID(),
            DIMENSION,
            1.0,
            64.0,
            -3.0,
            15.0F,
            20.0F,
            25.0F,
            2.0F,
            durationTicks,
            startGameTime,
            99L
        );
    }

    private static ResumeEffectsPayload roundTrip(ResumeEffectsPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            ResumeEffectsPayload.STREAM_CODEC.encode(buffer, payload);
            return ResumeEffectsPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
