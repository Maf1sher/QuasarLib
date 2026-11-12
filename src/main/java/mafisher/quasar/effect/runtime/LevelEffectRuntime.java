package mafisher.quasar.effect.runtime;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.effect.EffectThreading;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Per-level effect runtime. It owns the migrated service instances and their mutable state,
 * including budget counters and the entity-spawn budget; the public static service classes are
 * facades that resolve this runtime from the registry.
 */
@ApiStatus.Internal
public final class LevelEffectRuntime {
    private final ServerLevel level;
    private final EffectStore store;
    private final EffectPacketSink packets;
    private final EffectBudgetSource budgets;
    private final EffectBudgetRuntime budget;
    private final TransformUpdates transforms;
    private final EffectAudioRuntime audio;
    private final VisualEffectRuntime visuals;
    private final SegmentedEffectRuntime segmented;
    private final CinematicEffectRuntime cinematics;
    private final TerrainEditRuntime terrain;
    private final BlockRollbackRuntime rollbacks;
    private final GameplayEffectRuntime gameplay;
    private final EffectRestoreRuntime recovery;

    /** Creates a runtime with the production persistence, networking, and budget seams. */
    public LevelEffectRuntime(ServerLevel level) {
        this(
            level,
            new SavedDataEffectStore(level),
            new NeoForgeEffectPacketSink(),
            new ConfigEffectBudgetSource()
        );
    }

    public LevelEffectRuntime(
        ServerLevel level,
        EffectStore store,
        EffectPacketSink packets,
        EffectBudgetSource budgets
    ) {
        this.level = Objects.requireNonNull(level, "level");
        EffectThreading.requireServerThread(this.level);
        this.store = Objects.requireNonNull(store, "store");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
        this.budget = new EffectBudgetRuntime(this.level, this.budgets);
        this.transforms = new TransformUpdates(this.level, this.packets);
        this.audio = new EffectAudioRuntime(this.level, this.packets);
        this.visuals = new VisualEffectRuntime(
            this.level, this.store, this.packets, this.budgets, this.transforms, this.audio
        );
        this.segmented = new SegmentedEffectRuntime(
            this.level, this.store, this.packets, this.budgets, this.transforms, this.audio
        );
        this.cinematics = new CinematicEffectRuntime(
            this.level, this.store, this.budgets, this.visuals, this.segmented, this.audio
        );
        this.terrain = new TerrainEditRuntime(this.level, this.budgets);
        this.rollbacks = new BlockRollbackRuntime(this.level, this.store);
        this.gameplay = new GameplayEffectRuntime(
            this.level,
            this.store,
            this.budgets,
            this.terrain,
            this.rollbacks,
            this.visuals,
            this.audio
        );
        this.recovery = new EffectRestoreRuntime(
            this.level,
            this.store,
            this.gameplay,
            this.visuals,
            this.segmented,
            this.cinematics,
            this.rollbacks
        );
    }

    public ServerLevel level() {
        return this.level;
    }

    public EffectStore store() {
        return this.store;
    }

    public EffectPacketSink packets() {
        return this.packets;
    }

    public EffectBudgetSource budgets() {
        return this.budgets;
    }

    public EffectBudgetRuntime budget() {
        return this.budget;
    }

    public TransformUpdates transforms() {
        return this.transforms;
    }

    public EffectAudioRuntime audio() {
        return this.audio;
    }

    public VisualEffectRuntime visuals() {
        return this.visuals;
    }

    public SegmentedEffectRuntime segmented() {
        return this.segmented;
    }

    public CinematicEffectRuntime cinematics() {
        return this.cinematics;
    }

    public TerrainEditRuntime terrain() {
        return this.terrain;
    }

    public BlockRollbackRuntime rollbacks() {
        return this.rollbacks;
    }

    public GameplayEffectRuntime gameplay() {
        return this.gameplay;
    }

    public EffectRestoreRuntime recovery() {
        return this.recovery;
    }

    /** Flushes per-level transform batches; domain ticks run through their facades for now. */
    public void tick() {
        this.transforms.flush();
    }

    /**
     * Stops every domain with the caller's lifecycle policy. Level unload and shutdown pass
     * {@link EffectRemovalPolicy#KEEP_FOR_RESTORE}; explicit clears pass
     * {@link EffectRemovalPolicy#DELETE}.
     */
    public void shutdown(EffectRemovalPolicy policy, GameplayEffectEndReason reason) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(reason, "reason");
        this.cinematics.stopAll(policy);
        this.gameplay.stopAll(reason, policy);
        this.segmented.stopAll(policy);
        this.visuals.clear();
        this.audio.clear();
        this.rollbacks.clear();
        this.terrain.clear();
        this.budget.clear();
    }

    /** Stops and clears transient runtime state. */
    public void close() {
        clearDomains();
    }

    /** Drops transient state without running service shutdown hooks. */
    public void discard() {
        clearDomains();
    }

    private void clearDomains() {
        this.gameplay.clear();
        this.cinematics.clear();
        this.visuals.clear();
        this.segmented.clear();
        this.audio.clear();
        this.terrain.clear();
        this.rollbacks.clear();
        this.budget.clear();
        this.transforms.clear();
    }
}
