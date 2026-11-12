package mafisher.quasar.effect;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.effect.EffectBudgetReport;
import mafisher.quasar.api.effect.EffectBudgets;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.runtime.EffectBudgetRuntime;
import mafisher.quasar.effect.runtime.EffectBudgetSource;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Stateless facade for the per-level soft capacity accounting owned by
 * {@link EffectBudgetRuntime}. Every service reports culls, deferrals, dropped spawns, and audio
 * evictions here so operators can inspect degradation from one place.
 */
@SuppressWarnings("PMD.TooManyMethods")
@ApiStatus.Internal
public final class EffectBudgetService {
    private EffectBudgetService() {
    }

    /** Current capacity state and cumulative degradation counters for one level. */
    public static EffectBudgetReport report(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        EffectBudgetSource budgets = runtime == null ? null : runtime.budgets();
        EffectBudgetRuntime.BudgetCounters counters =
            runtime == null ? null : runtime.budget().counters();
        return new EffectBudgetReport(
            VisualEffectService.activeCount(level),
            budgets == null ? EffectBudgets.standaloneVisuals() : budgets.standaloneVisuals(),
            SegmentedVisualEffectService.activeSegmentCount(level),
            budgets == null ? EffectBudgets.segments() : budgets.segments(),
            GameplayEffectService.activeCount(level),
            budgets == null ? EffectBudgets.gameplaySessions() : budgets.gameplaySessions(),
            GameplayEffectService.pendingCount(level),
            budgets == null ? EffectBudgets.gameplayPending() : budgets.gameplayPending(),
            CinematicEffectService.activeCount(level),
            budgets == null ? EffectBudgets.cinematicSessions() : budgets.cinematicSessions(),
            EffectAudioService.activeCount(level),
            EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL,
            counters == null ? 0L : counters.culledEffects(),
            counters == null ? 0L : counters.deferredStarts(),
            counters == null ? 0L : counters.droppedEntitySpawns(),
            counters == null ? 0L : counters.audioEvictions(),
            counters == null ? 0L : counters.audioDrops()
        );
    }

    private static EffectBudgetRuntime budget(ServerLevel level) {
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? null : runtime.budget();
    }

    /** Records that one active effect was culled because its level was over budget. */
    public static void recordCull(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectBudgetRuntime budget = budget(level);
        if (budget != null) {
            budget.recordCull();
        }
    }

    /** Records that a gameplay start was queued until capacity becomes available. */
    public static void recordDeferral(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectBudgetRuntime budget = budget(level);
        if (budget != null) {
            budget.recordDeferral();
        }
    }

    /** Records that one entity spawn was dropped by a soft spawn budget. */
    public static void recordDroppedEntitySpawn(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectBudgetRuntime budget = budget(level);
        if (budget != null) {
            budget.recordDroppedEntitySpawn();
        }
    }

    /** Records that one active audio plan was evicted by the priority-aware plan cap. */
    public static void recordAudioEviction(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectBudgetRuntime budget = budget(level);
        if (budget != null) {
            budget.recordAudioEviction();
        }
    }

    /** Records that a new audio plan was dropped because its priority was below every active plan. */
    public static void recordAudioDrop(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectBudgetRuntime budget = budget(level);
        if (budget != null) {
            budget.recordAudioDrop();
        }
    }

    /**
     * Returns the lowest-priority key in insertion order, breaking ties by the first entry (oldest).
     * {@code excludedId} lets a segmented newcomer stay out of its own cull scan.
     */
    public static <T> UUID lowestPriority(
        Map<UUID, T> candidates,
        ToIntFunction<? super T> weight,
        UUID excludedId
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(weight, "weight");
        UUID victim = null;
        int lowest = Integer.MAX_VALUE;
        for (Map.Entry<UUID, T> entry : candidates.entrySet()) {
            if (entry.getKey().equals(excludedId)) {
                continue;
            }
            int candidate = weight.applyAsInt(entry.getValue());
            if (candidate < lowest) {
                lowest = candidate;
                victim = entry.getKey();
            }
        }
        return victim;
    }

    /** Returns the lowest-priority element of a list, keeping the first element on ties. */
    public static <T> T lowestPriority(List<T> candidates, ToIntFunction<? super T> weight) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(weight, "weight");
        T victim = null;
        int lowest = Integer.MAX_VALUE;
        for (T candidate : candidates) {
            int candidateWeight = weight.applyAsInt(candidate);
            if (candidateWeight < lowest) {
                lowest = candidateWeight;
                victim = candidate;
            }
        }
        return victim;
    }

    /** Whether one more entity spawn fits in the level's current tick without consuming a slot. */
    public static boolean entitySpawnBudgetAvailable(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectBudgetRuntime budget = budget(level);
        return budget == null || budget.entitySpawnBudgetAvailable();
    }

    /**
     * Reserves one level-wide entity spawn slot for the current game tick.
     *
     * @return {@code false} when the level tick budget is exhausted; the caller must drop the
     *     spawn without throwing. Without an installed runtime no level budget exists.
     */
    public static boolean tryAcquireEntitySpawn(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectBudgetRuntime budget = budget(level);
        return budget == null || budget.tryAcquireEntitySpawn();
    }

    /** Drops transient counters for a level; active services are unaffected. */
    public static void clear(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EffectBudgetRuntime budget = budget(level);
        if (budget != null) {
            budget.clear();
        }
    }
}
