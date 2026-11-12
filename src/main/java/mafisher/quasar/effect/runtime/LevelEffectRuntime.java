package mafisher.quasar.effect.runtime;

import java.util.Objects;
import mafisher.quasar.effect.EffectThreading;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Per-level effect runtime. It owns the service instances and all mutable state for one level;
 * the public static service classes are facades that resolve this runtime from the registry.
 */
@ApiStatus.Internal
public final class LevelEffectRuntime {
    private final ServerLevel level;
    private final EffectStore store;
    private final EffectPacketSink packets;
    private final EffectBudgetSource budgets;
    private final TransformUpdates transforms;

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

    /** Runs the per-level effect ticks. Only transform batching is runtime-owned so far. */
    public void tick() {
        this.transforms.flush();
    }

    /** Stops and clears transient runtime state. */
    public void close() {
        this.transforms.clear();
    }

    /** Drops transient state without running service shutdown hooks. */
    public void discard() {
        this.transforms.clear();
    }
}
