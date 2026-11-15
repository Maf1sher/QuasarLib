package mafisher.quasar.content.command;

import java.util.function.BiConsumer;
import mafisher.quasar.api.cinematic.CinematicEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.scale.SegmentedVisualEffectHandle;
import net.minecraft.server.level.ServerLevel;

/** Tracks effects started by content commands so stop commands never touch another mod's sessions. */
public final class ContentCommandSessions {
    private static final SessionSet<GameplayEffectHandle> GAMEPLAY = new SessionSet<>();
    private static final SessionSet<SegmentedVisualEffectHandle> SEGMENTED = new SessionSet<>();
    private static final SessionSet<CinematicEffectHandle> CINEMATIC = new SessionSet<>();

    private ContentCommandSessions() {
    }

    static void track(ServerLevel level, GameplayEffectHandle handle) {
        GAMEPLAY.track(level, handle);
    }

    static void track(ServerLevel level, SegmentedVisualEffectHandle handle) {
        SEGMENTED.track(level, handle);
    }

    static void track(ServerLevel level, CinematicEffectHandle handle) {
        CINEMATIC.track(level, handle);
    }

    static int stopGameplay(ServerLevel level, BiConsumer<ServerLevel, GameplayEffectHandle> stopper) {
        return GAMEPLAY.stop(level, stopper);
    }

    static int stopSegmented(
        ServerLevel level,
        BiConsumer<ServerLevel, SegmentedVisualEffectHandle> stopper
    ) {
        return SEGMENTED.stop(level, stopper);
    }

    static int stopCinematic(ServerLevel level, BiConsumer<ServerLevel, CinematicEffectHandle> stopper) {
        return CINEMATIC.stop(level, stopper);
    }

    public static void clear(ServerLevel level) {
        GAMEPLAY.clear(level);
        SEGMENTED.clear(level);
        CINEMATIC.clear(level);
        ContentOrbitalLaserSessions.clear(level);
    }
}
