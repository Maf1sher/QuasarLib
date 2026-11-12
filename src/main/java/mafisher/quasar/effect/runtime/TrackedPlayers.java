package mafisher.quasar.effect.runtime;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

/** Delivers one payload to the named players that are still present in the effect's level. */
@ApiStatus.Internal
public final class TrackedPlayers {
    private TrackedPlayers() {
    }

    public static void send(
        ServerLevel level,
        EffectPacketSink packets,
        Collection<UUID> playerIds,
        CustomPacketPayload payload
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(packets, "packets");
        Objects.requireNonNull(playerIds, "playerIds");
        Objects.requireNonNull(payload, "payload");
        if (playerIds.isEmpty() || level.getServer() == null) {
            return;
        }
        for (UUID playerId : playerIds) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && player.level() == level) {
                packets.sendToPlayer(player, payload);
            }
        }
    }
}
