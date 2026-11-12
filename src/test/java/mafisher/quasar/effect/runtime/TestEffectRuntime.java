package mafisher.quasar.effect.runtime;

import net.minecraft.server.level.ServerLevel;

/** Installs a runtime with in-memory persistence and a recording packet sink. */
public final class TestEffectRuntime {
    private TestEffectRuntime() {
    }

    public static LevelEffectRuntime install(ServerLevel level) {
        return install(level, new RecordingPacketSink());
    }

    public static LevelEffectRuntime install(ServerLevel level, RecordingPacketSink packets) {
        return install(level, new RecordingEffectStore(), packets, new TestBudgetSource());
    }

    public static LevelEffectRuntime install(
        ServerLevel level,
        RecordingPacketSink packets,
        EffectBudgetSource budgets
    ) {
        return install(level, new RecordingEffectStore(), packets, budgets);
    }

    public static LevelEffectRuntime install(
        ServerLevel level,
        EffectStore store,
        EffectPacketSink packets,
        EffectBudgetSource budgets
    ) {
        LevelEffectRuntime runtime = new LevelEffectRuntime(level, store, packets, budgets);
        EffectRuntimeRegistry.install(level, runtime);
        return runtime;
    }

    public static void uninstall(ServerLevel level) {
        EffectRuntimeRegistry.close(level);
    }
}
