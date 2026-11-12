package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import mafisher.quasar.api.effect.EffectDebrisSpawn;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectDebrisPayloadTest {
    @Test
    void roundTripsOneBurst() {        EffectDebrisPayload payload = new EffectDebrisPayload(
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
    void roundTripsSurfaceSpawn() {
        EffectDebrisPayload payload = new EffectDebrisPayload(
            ResourceLocation.withDefaultNamespace("overworld"),
            new Vec3(0.0, 64.0, 0.0),
            42,
            24,
            Vec3.ZERO,
            0.35F,
            0.65F,
            0.0F,
            64,
            77L,
            900L,
            null,
            new EffectDebrisSpawn.Surface(new Vec3(0.0, 64.0, 0.0), 12.0, 24.0, 0.45),
            8.0F
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
