package mafisher.quasar.effect.runtime;

import java.util.Objects;
import mafisher.quasar.effect.EffectThreading;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Per-level effect runtime. It owns the migrated service instances and their mutable state; the
 * public static service classes are facades that resolve this runtime from the registry.
 */
@ApiStatus.Internal
public final class LevelEffectRuntime {
    private final ServerLevel level;
    private final EffectStore store;
    private final EffectPacketSink packets;
    private final EffectBudgetSource budgets;
    private final TransformUpdates transforms;
    private final EffectAudioRuntime audio;
    private final VisualEffectRuntime visuals;
    private final SegmentedEffectRuntime segmented;
    private final CinematicEffectRuntime cinematics;

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

    /** Flushes per-level transform batches; domain ticks run through their facades for now. */
    public void tick() {
        this.transforms.flush();
    }

    /** Stops and clears transient runtime state. */
    public void close() {
        this.cinematics.clear();
        this.visuals.clear();
        this.segmented.clear();
        this.audio.clear();
        this.transforms.clear();
    }

    /** Drops transient state without running service shutdown hooks. */
    public void discard() {
        this.cinematics.clear();
        this.visuals.clear();
        this.segmented.clear();
        this.audio.clear();
        this.transforms.clear();
    }
}
