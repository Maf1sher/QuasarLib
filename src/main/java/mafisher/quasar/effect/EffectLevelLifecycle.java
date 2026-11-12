package mafisher.quasar.effect;

import java.util.Objects;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.cinematic.CinematicEffectService;
import mafisher.quasar.effect.gameplay.GameplayEffectEndReason;
import mafisher.quasar.effect.gameplay.GameplayEffectService;
import mafisher.quasar.effect.gameplay.TerrainEditService;
import mafisher.quasar.effect.persistence.BlockRollbackService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import net.minecraft.server.level.ServerLevel;

/**
 * Single owner of per-level effect runtime shutdown. Services still own their domain state, but
 * this class decides when every domain stops and clears on level unload or server shutdown.
 */
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
        } finally {
            LevelStateSlot.clearLevel(level);
        }
    }

    /** Drops stale level state before restore. */
    public static void reset(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelStateSlot.clearLevel(level);
    }
}
