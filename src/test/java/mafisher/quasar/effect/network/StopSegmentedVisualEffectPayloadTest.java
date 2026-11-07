package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StopSegmentedVisualEffectPayloadTest {
    @Test
    void roundTripsACoordinatorStop() {
        StopSegmentedVisualEffectPayload payload = new StopSegmentedVisualEffectPayload(
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
        );
        ByteBuf buffer = Unpooled.buffer();
        try {
            StopSegmentedVisualEffectPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, StopSegmentedVisualEffectPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
