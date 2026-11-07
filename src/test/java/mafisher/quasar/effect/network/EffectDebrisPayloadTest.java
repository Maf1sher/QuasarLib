package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectDebrisPayloadTest {
    @Test
    void roundTripsOneBurst() {
        EffectDebrisPayload payload = new EffectDebrisPayload(
            ResourceLocation.withDefaultNamespace("overworld"),
            new Vec3(1.0, 64.0, -2.0),
            42,
            16,
            new Vec3(0.0, 1.0, 0.0),
            0.5F,
            0.75F,
            0.08F,
            40,
            1234L,
            900L
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        EffectDebrisPayload.STREAM_CODEC.encode(buffer, payload);
        EffectDebrisPayload decoded = EffectDebrisPayload.STREAM_CODEC.decode(buffer);

        assertEquals(payload, decoded);
    }

    @Test
    void rejectsInvalidBursts() {
        assertThrows(IllegalArgumentException.class, () -> payload(0, 1, 1.0F, 1.0F, 0.0F, 1));
        assertThrows(IllegalArgumentException.class, () -> payload(1, 0, 1.0F, 1.0F, 0.0F, 1));
        assertThrows(IllegalArgumentException.class, () -> payload(1, 1, -1.0F, 1.0F, 0.0F, 1));
        assertThrows(IllegalArgumentException.class, () -> payload(1, 1, 1.0F, 2.0F, 0.0F, 1));
        assertThrows(IllegalArgumentException.class, () -> payload(1, 1, 1.0F, 1.0F, 2.0F, 1));
        assertThrows(IllegalArgumentException.class, () -> payload(1, 1, 1.0F, 1.0F, 0.0F, 0));
    }

    private static EffectDebrisPayload payload(
        int blockStateId,
        int pieces,
        float speed,
        float spread,
        float gravity,
        int lifetime
    ) {
        return new EffectDebrisPayload(
            ResourceLocation.withDefaultNamespace("overworld"),
            Vec3.ZERO,
            blockStateId,
            pieces,
            Vec3.ZERO,
            speed,
            spread,
            gravity,
            lifetime,
            1L,
            1L
        );
    }
}
