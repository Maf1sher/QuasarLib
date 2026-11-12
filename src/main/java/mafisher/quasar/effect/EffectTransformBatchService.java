package mafisher.quasar.effect;

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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.ApiStatus;

/**
 * Coalesces transform changes into one packet per player per server tick. Queue entries hold the
 * live tracking set owned by the visual or segmented service, so a stopped effect or a player who
 * left the audience cannot receive a stale update. This is an implementation detail of the effect
 * runtime, not a supported add-on API.
 */
@ApiStatus.Internal
public final class EffectTransformBatchService {
    private static final LevelStateSlot<BatchState> STATE = LevelStateSlot.create(BatchState::new);

    private EffectTransformBatchService() {
    }

    public static void queueVisual(
        ServerLevel level,
        UUID instanceId,
        EffectTransform transform,
        Set<UUID> trackingPlayers
    ) {
        queue(level, instanceId, false, transform, trackingPlayers);
    }

    /** Queues one segmented coordinator update; public only because the scale package is a subpackage. */
    public static void queueSegmented(
        ServerLevel level,
        UUID coordinatorId,
        EffectTransform transform,
        Set<UUID> trackingPlayers
    ) {
        queue(level, coordinatorId, true, transform, trackingPlayers);
    }

    /** Sends pending updates for one level, splitting at the per-payload entry cap. */
    public static void flush(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        BatchState state = STATE.peek(level);
        if (state == null) {
            return;
        }

        Map<UUID, List<EffectTransformBatchPayload.Entry>> byPlayer = new LinkedHashMap<>();
        collect(state.visuals, false, byPlayer);
        collect(state.segmented, true, byPlayer);
        state.clear();
        STATE.remove(level);

        if (byPlayer.isEmpty() || level.getServer() == null) {
            return;
        }
        long gameTime = level.getGameTime();
        ResourceLocation dimension = level.dimension().location();
        for (Map.Entry<UUID, List<EffectTransformBatchPayload.Entry>> entry : byPlayer.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != level) {
                continue;
            }
            List<EffectTransformBatchPayload.Entry> updates = entry.getValue();
            for (int start = 0; start < updates.size(); start += EffectTransformBatchPayload.MAX_UPDATES) {
                int end = Math.min(start + EffectTransformBatchPayload.MAX_UPDATES, updates.size());
                PacketDistributor.sendToPlayer(player, new EffectTransformBatchPayload(
                    dimension,
                    gameTime,
                    List.copyOf(updates.subList(start, end))
                ));
            }
        }
    }

    /** Drops pending updates without sending them; used by lifecycle cleanup and tests. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        STATE.remove(level);
    }

    private static void queue(
        ServerLevel level,
        UUID instanceId,
        boolean segmented,
        EffectTransform transform,
        Set<UUID> trackingPlayers
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(trackingPlayers, "trackingPlayers");
        if (trackingPlayers.isEmpty()) {
            return;
        }
        BatchState state = STATE.get(level);
        Map<UUID, PendingUpdate> updates = segmented ? state.segmented : state.visuals;
        updates.put(instanceId, new PendingUpdate(transform, trackingPlayers));
    }

    private static void collect(
        Map<UUID, PendingUpdate> pending,
        boolean segmented,
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
                segmented,
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

    private static final class BatchState {
        private final Map<UUID, PendingUpdate> visuals = new LinkedHashMap<>();
        private final Map<UUID, PendingUpdate> segmented = new LinkedHashMap<>();

        private void clear() {
            this.visuals.clear();
            this.segmented.clear();
        }
    }
}
