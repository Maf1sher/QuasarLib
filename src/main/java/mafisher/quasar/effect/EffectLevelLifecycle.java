package mafisher.quasar.effect;

import java.util.Objects;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.gameplay.TerrainEditService;
import mafisher.quasar.effect.persistence.BlockRollbackService;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Single owner of per-level effect runtime shutdown. Services still own their domain state, but
 * this class decides when every domain stops and clears on level unload or server shutdown.
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
            CinematicEffectService.stopAll(level, policy);
            GameplayEffectService.stopAll(level, reason, policy);
            SegmentedVisualEffectService.stopAll(level, policy);
            VisualEffectService.clear(level);
            EffectAudioService.clear(level);
            EffectBudgetService.clear(level);
            BlockRollbackService.clear(level);
            TerrainEditService.clear(level);
            EffectRuntimeRegistry.close(level);
        } finally {
            LevelStateSlot.clearLevel(level);
        }
    }

    /** Drops stale level state before restore. */
    public static void reset(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        try {
            EffectRuntimeRegistry.reset(level);
        } finally {
            LevelStateSlot.clearLevel(level);
        }
    }
}
