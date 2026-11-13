package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectParameterState;
import mafisher.quasar.effect.network.EffectParameterBatchPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coalesces parameter changes into one packet per player per server tick. Queue entries hold the
 * live tracking set owned by the visual service, so a stopped effect or a player who left the
 * audience cannot receive a stale update.
 */
@ApiStatus.Internal
public final class ParameterUpdates {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterUpdates.class);

    private final ServerLevel level;
    private final EffectPacketSink packets;
    private final Map<UUID, PendingUpdate> pending = new LinkedHashMap<>();

    public ParameterUpdates(ServerLevel level, EffectPacketSink packets) {
        this.level = Objects.requireNonNull(level, "level");
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    public void queueVisual(
        UUID instanceId,
        EffectParameterState state,
        Set<UUID> trackingPlayers
    ) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(trackingPlayers, "trackingPlayers");
        if (trackingPlayers.isEmpty()) {
            return;
        }
        this.pending.put(instanceId, new PendingUpdate(state, trackingPlayers));
    }

    /** Sends pending updates for this level, splitting at the per-payload entry and byte caps. */
    public void flush() {
        if (this.pending.isEmpty()) {
            return;
        }
        Map<UUID, List<EffectParameterBatchPayload.Entry>> byPlayer = new LinkedHashMap<>();
        for (Map.Entry<UUID, PendingUpdate> entry : this.pending.entrySet()) {
            PendingUpdate update = entry.getValue();
            if (update.trackingPlayers().isEmpty()) {
                continue;
            }
            EffectParameterBatchPayload.Entry payloadEntry =
                new EffectParameterBatchPayload.Entry(entry.getKey(), update.state());
            for (UUID playerId : update.trackingPlayers()) {
                byPlayer.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(payloadEntry);
            }
        }
        this.pending.clear();

        if (byPlayer.isEmpty() || this.level.getServer() == null) {
            return;
        }
        long gameTime = this.level.getGameTime();
        ResourceLocation dimension = this.level.dimension().location();
        for (Map.Entry<UUID, List<EffectParameterBatchPayload.Entry>> entry : byPlayer.entrySet()) {
            ServerPlayer player = this.level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != this.level) {
                continue;
            }
            sendChunks(player, dimension, gameTime, entry.getValue());
        }
    }

    /** Drops pending updates without sending them. */
    public void clear() {
        this.pending.clear();
    }

    private void sendChunks(
        ServerPlayer player,
        ResourceLocation dimension,
        long gameTime,
        List<EffectParameterBatchPayload.Entry> entries
    ) {
        if (entries.isEmpty()) {
            return;
        }
        int chunkSize = Math.min(EffectParameterBatchPayload.MAX_UPDATES, entries.size());
        List<EffectParameterBatchPayload.Entry> chunk = entries.subList(0, chunkSize);
        try {
            this.packets.sendToPlayer(player, new EffectParameterBatchPayload(
                dimension, gameTime, List.copyOf(chunk)
            ));
        } catch (IllegalArgumentException tooLarge) {
            if (chunk.size() == 1) {
                LOGGER.warn(
                    "Dropped an oversized parameter update for {} in {}",
                    dimension, this.level.dimension().location(), tooLarge
                );
                return;
            }
            sendChunks(player, dimension, gameTime, entries.subList(0, chunk.size() / 2));
            sendChunks(player, dimension, gameTime, entries.subList(chunk.size() / 2, entries.size()));
            return;
        }
        if (entries.size() > chunkSize) {
            sendChunks(player, dimension, gameTime, entries.subList(chunkSize, entries.size()));
        }
    }

    private record PendingUpdate(EffectParameterState state, Set<UUID> trackingPlayers) {
    }
}
