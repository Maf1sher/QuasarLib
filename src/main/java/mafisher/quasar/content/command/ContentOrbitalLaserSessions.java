package mafisher.quasar.content.command;

import java.util.function.BiConsumer;
import mafisher.quasar.api.effect.VisualEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import net.minecraft.server.level.ServerLevel;

/** Tracks orbital-laser sessions and marker previews independently from other content commands. */
final class ContentOrbitalLaserSessions {
    private static final SessionSet<GameplayEffectHandle> SESSIONS = new SessionSet<>();
    private static final SessionSet<VisualEffectHandle> PREVIEWS = new SessionSet<>();

    private ContentOrbitalLaserSessions() {
    }

    static void trackSession(ServerLevel level, GameplayEffectHandle handle) {
        SESSIONS.track(level, handle);
    }

    static void trackPreview(ServerLevel level, VisualEffectHandle handle) {
        PREVIEWS.track(level, handle);
    }

    static int stopSessions(
        ServerLevel level,
        BiConsumer<ServerLevel, GameplayEffectHandle> stopper
    ) {
        return SESSIONS.stop(level, stopper);
    }

    static int stopPreviews(
        ServerLevel level,
        BiConsumer<ServerLevel, VisualEffectHandle> stopper
    ) {
        return PREVIEWS.stop(level, stopper);
    }

    static void clear(ServerLevel level) {
        SESSIONS.clear(level);
        PREVIEWS.clear(level);
    }
}
