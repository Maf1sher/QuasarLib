package mafisher.quasar.effect.persistence;

import java.util.Objects;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Facade for the per-level restore runtime. Descriptor ordering, quarantine, rollback scheduling,
 * and the recovery report live in {@link LevelEffectRuntime#recovery()}.
 */
@ApiStatus.Internal
public final class EffectRestoreService {
    private EffectRestoreService() {
    }

    public static EffectRecoveryReport restoreLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return EffectRuntimeRegistry.get(level).recovery().restoreLevel();
    }

    public static EffectRecoveryReport report(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(level);
        return runtime == null ? EffectRecoveryReport.EMPTY : runtime.recovery().report();
    }
}
