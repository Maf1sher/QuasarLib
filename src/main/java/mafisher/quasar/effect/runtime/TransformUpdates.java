package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.effect.network.EffectTransformBatchPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Coalesces transform changes into one packet per player per server tick. Queue entries hold the
 * live tracking set owned by the visual or segmented service, so a stopped effect or a player who
 * left the audience cannot receive a stale update.
 */
@ApiStatus.Internal
public final class TransformUpdates {
    private final ServerLevel level;
    private final EffectPacketSink packets;
    private final Map<UUID, PendingUpdate> visuals = new LinkedHashMap<>();
    private final Map<UUID, PendingUpdate> segmented = new LinkedHashMap<>();

    public TransformUpdates(ServerLevel level, EffectPacketSink packets) {
        this.level = Objects.requireNonNull(level, "level");
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    public void queueVisual(
        UUID instanceId,
        EffectTransform transform,
        Set<UUID> trackingPlayers
    ) {
        queue(instanceId, false, transform, trackingPlayers);
    }

    public void queueSegmented(
        UUID coordinatorId,
        EffectTransform transform,
        Set<UUID> trackingPlayers
    ) {
        queue(coordinatorId, true, transform, trackingPlayers);
    }

    /** Sends pending updates for this level, splitting at the per-payload entry cap. */
    public void flush() {
        Map<UUID, List<EffectTransformBatchPayload.Entry>> byPlayer = new LinkedHashMap<>();
        collect(this.visuals, false, byPlayer);
        collect(this.segmented, true, byPlayer);
        clear();

        if (byPlayer.isEmpty() || this.level.getServer() == null) {
            return;
        }
        long gameTime = this.level.getGameTime();
        ResourceLocation dimension = this.level.dimension().location();
        for (Map.Entry<UUID, List<EffectTransformBatchPayload.Entry>> entry : byPlayer.entrySet()) {
            ServerPlayer player = this.level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != this.level) {
                continue;
            }
            List<EffectTransformBatchPayload.Entry> updates = entry.getValue();
            for (int start = 0; start < updates.size(); start += EffectTransformBatchPayload.MAX_UPDATES) {
                int end = Math.min(start + EffectTransformBatchPayload.MAX_UPDATES, updates.size());
                this.packets.sendToPlayer(player, new EffectTransformBatchPayload(
                    dimension,
                    gameTime,
                    List.copyOf(updates.subList(start, end))
                ));
            }
        }
    }

    /** Drops pending updates without sending them. */
    public void clear() {
        this.visuals.clear();
        this.segmented.clear();
    }

    private void queue(
        UUID instanceId,
        boolean isSegmented,
        EffectTransform transform,
        Set<UUID> trackingPlayers
    ) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(trackingPlayers, "trackingPlayers");
        if (trackingPlayers.isEmpty()) {
            return;
        }
        Map<UUID, PendingUpdate> updates = isSegmented ? this.segmented : this.visuals;
        updates.put(instanceId, new PendingUpdate(transform, trackingPlayers));
    }

    private static void collect(
        Map<UUID, PendingUpdate> pending,
        boolean isSegmented,
        Map<UUID, List<EffectTransformBatchPayload.Entry>> byPlayer
    ) {
        for (Map.Entry<UUID, PendingUpdate> entry : pending.entrySet()) {
            PendingUpdate update = entry.getValue();
            if (update.trackingPlayers().isEmpty()) {
                continue;
            }
            EffectTransform source = update.transform();
            EffectPoint position = source.position();
            EffectTransformBatchPayload.Entry transform = new EffectTransformBatchPayload.Entry(
                entry.getKey(),
                isSegmented,
                position.x(),
                position.y(),
                position.z(),
                source.yaw(),
                source.pitch(),
                source.roll()
            );
            for (UUID playerId : update.trackingPlayers()) {
                byPlayer.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(transform);
            }
        }
    }

    private record PendingUpdate(EffectTransform transform, Set<UUID> trackingPlayers) {
    }
}
