package mafisher.quasar.effect;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Single owner of per-level effect runtime shutdown. The runtime owns every domain; this class
 * decides when it stops on level unload or server shutdown.
 */
@ApiStatus.Internal
public final class EffectLevelLifecycle {
    private EffectLevelLifecycle() {
    }

    /** Stops and clears one level; {@link EffectRemovalPolicy#KEEP_FOR_RESTORE} preserves descriptors. */
    public static void clear(
        ServerLevel level,
        EffectRemovalPolicy policy,
        GameplayEffectEndReason reason
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(reason, "reason");
        try {
            LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
            if (runtime != null) {
                runtime.shutdown(policy, reason);
            }
        } finally {
            EffectRuntimeRegistry.close(level);
        }
    }

    /** Drops stale level state before restore. */
    public static void reset(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectRuntimeRegistry.reset(level);
    }
}
