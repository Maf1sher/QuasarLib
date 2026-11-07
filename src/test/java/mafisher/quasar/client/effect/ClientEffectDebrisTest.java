package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mafisher.quasar.effect.network.EffectDebrisPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ClientEffectDebrisTest {
    private static final ResourceLocation DIMENSION =
        ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void derivesTheSamePiecesFromTheSameSeed() {
        EffectDebrisPayload payload = payload(99L);

        List<ClientEffectDebris.Piece> first = ClientEffectDebris.pieces(payload);
        List<ClientEffectDebris.Piece> second = ClientEffectDebris.pieces(payload);

        assertEquals(first, second);
        assertEquals(8, first.size());
        for (ClientEffectDebris.Piece piece : first) {
            assertTrue(piece.lifetimeTicks() >= 28 && piece.lifetimeTicks() <= 40);
            assertTrue(Math.abs(piece.position().x) <= 0.35);
            assertTrue(Math.abs(piece.position().y) <= 0.35);
            assertTrue(Math.abs(piece.position().z) <= 0.35);
            assertTrue(piece.velocity().length() <= 1.0);
        }
    }

    @Test
    void differentSeedsProduceDifferentPieces() {
        assertNotEquals(
            ClientEffectDebris.pieces(payload(1L)),
            ClientEffectDebris.pieces(payload(2L))
        );
    }

    private static EffectDebrisPayload payload(long seed) {
        return new EffectDebrisPayload(
            DIMENSION,
            Vec3.ZERO,
            1,
            8,
            Vec3.ZERO,
            1.0F,
            1.0F,
            0.08F,
            40,
            seed,
            0L
        );
    }
}
