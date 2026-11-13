package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectParameterState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectParameterBatchPayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsEveryValueType() {
        EffectParameterState state = EffectParameterState.builder()
            .putFloat("fade", 0.25F)
            .putVec2("uv", 0.5F, 0.75F)
            .putVec3("axis", 1.0F, 2.0F, 3.0F)
            .putColor("tint", 0.1F, 0.2F, 0.3F, 0.4F)
            .build();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        EffectParameterBatchPayload payload = new EffectParameterBatchPayload(
            DIMENSION, 123L, List.of(
                new EffectParameterBatchPayload.Entry(first, state),
                new EffectParameterBatchPayload.Entry(second, EffectParameterState.EMPTY)
            )
        );

        EffectParameterBatchPayload decoded = roundTrip(payload);

        assertEquals(123L, decoded.updateGameTime());
        assertEquals(DIMENSION, decoded.dimensionId());
        assertEquals(2, decoded.updates().size());
        assertEquals(first, decoded.updates().getFirst().instanceId());
        assertEquals(state.values(), decoded.updates().getFirst().state().values());
        assertTrue(decoded.updates().get(1).state().isEmpty());
    }

    @Test
    void rejectsEmptyAndOversizedBatches() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParameterBatchPayload(DIMENSION, 0L, List.of()));

        List<EffectParameterBatchPayload.Entry> updates = new ArrayList<>();
        for (int index = 0; index <= EffectParameterBatchPayload.MAX_UPDATES; index++) {
            updates.add(new EffectParameterBatchPayload.Entry(
                UUID.randomUUID(), EffectParameterState.EMPTY
            ));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParameterBatchPayload(DIMENSION, 0L, updates));
    }

    private static EffectParameterBatchPayload roundTrip(EffectParameterBatchPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            EffectParameterBatchPayload.STREAM_CODEC.encode(buffer, payload);
            return EffectParameterBatchPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
