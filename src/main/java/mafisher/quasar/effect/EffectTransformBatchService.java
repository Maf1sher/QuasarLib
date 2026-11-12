package mafisher.quasar.effect;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Stateless compatibility facade for per-tick transform batching. Pending updates are owned by
 * {@link LevelEffectRuntime}; this class will be removed once call sites use the runtime directly.
 */
@ApiStatus.Internal
public final class EffectTransformBatchService {
    private EffectTransformBatchService() {
    }

    public static void queueVisual(
        ServerLevel level,
        UUID instanceId,
        EffectTransform transform,
        Set<UUID> trackingPlayers
    ) {
        queue(level, instanceId, transform, trackingPlayers, false);
    }

    /** Queues one segmented coordinator update; public only because the scale package is a subpackage. */
    public static void queueSegmented(
        ServerLevel level,
        UUID coordinatorId,
        EffectTransform transform,
        Set<UUID> trackingPlayers
    ) {
        queue(level, coordinatorId, transform, trackingPlayers, true);
    }

    /** Sends pending updates for one level, splitting at the per-payload entry cap. */
    public static void flush(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.transforms().flush();
        }
    }

    /** Drops pending updates without sending them. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        if (runtime != null) {
            runtime.transforms().clear();
        }
    }

    private static void queue(
        ServerLevel level,
        UUID id,
        EffectTransform transform,
        Set<UUID> trackingPlayers,
        boolean segmented
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(trackingPlayers, "trackingPlayers");
        if (trackingPlayers.isEmpty()) {
            return;
        }
        LevelEffectRuntime runtime = EffectRuntimeRegistry.get(level);
        if (segmented) {
            runtime.transforms().queueSegmented(id, transform, trackingPlayers);
        } else {
            runtime.transforms().queueVisual(id, transform, trackingPlayers);
        }
    }
}
