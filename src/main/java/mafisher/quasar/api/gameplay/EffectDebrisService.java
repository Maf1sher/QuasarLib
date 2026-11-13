package mafisher.quasar.api.gameplay;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.debris.EffectDebrisSpec;
import mafisher.quasar.effect.network.EffectDebrisPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

/** Replicates one client-only debris burst to the players that can see it. */
final class EffectDebrisService {
    private EffectDebrisService() {
    }

    static int send(ServerLevel level, EffectDebrisSpec spec, int pieces, long seed) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        if (pieces <= 0) {
            return 0;
        }
        int blockStateId = Block.getId(spec.state());
        if (blockStateId <= 0) {
            return 0;
        }
        EffectDebrisPayload payload = new EffectDebrisPayload(
            level.dimension().location(),
            spec.origin(),
            blockStateId,
            Math.min(pieces, EffectDebrisSpec.MAX_PIECES),
            spec.direction(),
            (float)spec.speed(),
            (float)spec.spread(),
            (float)spec.gravity(),
            spec.lifetimeTicks(),
            seed,
            level.getGameTime(),
            spec.attractor(),
            spec.spawn(),
            spec.scale()
        );
        if (spec.audience() instanceof EffectAudience.Nearby nearby) {
            PacketDistributor.sendToPlayersNear(
                level,
                null,
                spec.origin().x,
                spec.origin().y,
                spec.origin().z,
                nearby.radius(),
                payload
            );
        } else {
            PacketDistributor.sendToPlayersInDimension(level, payload);
        }
        return payload.pieces();
    }
}
