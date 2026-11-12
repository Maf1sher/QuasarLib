package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mafisher.quasar.api.effect.EffectDebrisSpawn;
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

    @Test
    void surfaceSpawnUsesTheResolverForPositionAndAppearance() {
        ClientEffectDebris.Appearance appearance = new ClientEffectDebris.Appearance(
            0.1F, 0.2F, 0.3F, 0.4F, 0.9F, 0.8F, 0.7F
        );
        EffectDebrisPayload payload = surfacePayload(5L, 8);

        List<ClientEffectDebris.Piece> pieces = ClientEffectDebris.pieces(
            payload,
            (candidate, lift) -> new ClientEffectDebris.Sample(
                new Vec3(candidate.x, 70.0 + lift, candidate.z), appearance
            )
        );

        assertEquals(8, pieces.size());
        for (ClientEffectDebris.Piece piece : pieces) {
            assertEquals(70.45, piece.position().y, 1.0E-6);
            assertEquals(appearance, piece.appearance());
            assertTrue(piece.scale() >= 0.12F && piece.scale() <= 0.22F);
        }
    }

    @Test
    void unresolvableSurfaceColumnsFallBackToCandidatePositions() {
        EffectDebrisPayload payload = surfacePayload(7L, 8);

        List<ClientEffectDebris.Piece> pieces = ClientEffectDebris.pieces(
            payload, (candidate, lift) -> null
        );

        assertEquals(8, pieces.size());
        for (ClientEffectDebris.Piece piece : pieces) {
            assertTrue(piece.appearance() == null);
            assertTrue(Math.abs(piece.position().x) <= 12.0);
            assertTrue(Math.abs(piece.position().z) <= 12.0);
        }
    }

    private static EffectDebrisPayload surfacePayload(long seed, int pieces) {
        return new EffectDebrisPayload(
            DIMENSION,
            Vec3.ZERO,
            1,
            pieces,
            Vec3.ZERO,
            0.35F,
            0.65F,
            0.0F,
            40,
            seed,
            0L,
            null,
            new EffectDebrisSpawn.Surface(Vec3.ZERO, 4.0, 12.0, 0.45),
            8.0F
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
