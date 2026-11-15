package mafisher.quasar.content.command;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerLevel;

/** One command-owned handle set per level; weak level keys release unloaded levels. */
final class SessionSet<H> {
    private final Map<ServerLevel, Set<H>> sessions = new WeakHashMap<>();

    void track(ServerLevel level, H handle) {
        this.sessions.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(handle);
    }

    int stop(ServerLevel level, BiConsumer<ServerLevel, H> stopper) {
        Set<H> handles = this.sessions.remove(level);
        if (handles == null) {
            return 0;
        }
        handles.forEach(handle -> stopper.accept(level, handle));
        return handles.size();
    }

    void clear(ServerLevel level) {
        this.sessions.remove(level);
    }
}
