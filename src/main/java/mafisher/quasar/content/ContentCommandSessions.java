package mafisher.quasar.content;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import mafisher.quasar.api.cinematic.CinematicEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.scale.SegmentedVisualEffectHandle;
import net.minecraft.server.level.ServerLevel;

/** Tracks effects started by content commands so stop commands never touch another mod's sessions. */
final class ContentCommandSessions {
    private static final Map<ServerLevel, Set<GameplayEffectHandle>> GAMEPLAY = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<SegmentedVisualEffectHandle>> SEGMENTED =
        new WeakHashMap<>();
    private static final Map<ServerLevel, Set<CinematicEffectHandle>> CINEMATIC = new WeakHashMap<>();

    private ContentCommandSessions() {
    }

    static void track(ServerLevel level, GameplayEffectHandle handle) {
        GAMEPLAY.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(handle);
    }

    static void track(ServerLevel level, SegmentedVisualEffectHandle handle) {
        SEGMENTED.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(handle);
    }

    static void track(ServerLevel level, CinematicEffectHandle handle) {
        CINEMATIC.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(handle);
    }

    static int stopGameplay(ServerLevel level, BiConsumer<ServerLevel, GameplayEffectHandle> stopper) {
        Set<GameplayEffectHandle> handles = GAMEPLAY.remove(level);
        if (handles == null) {
            return 0;
        }
        handles.forEach(handle -> stopper.accept(level, handle));
        return handles.size();
    }

    static int stopSegmented(ServerLevel level, BiConsumer<ServerLevel, SegmentedVisualEffectHandle> stopper) {
        Set<SegmentedVisualEffectHandle> handles = SEGMENTED.remove(level);
        if (handles == null) {
            return 0;
        }
        handles.forEach(handle -> stopper.accept(level, handle));
        return handles.size();
    }

    static int stopCinematic(ServerLevel level, BiConsumer<ServerLevel, CinematicEffectHandle> stopper) {
        Set<CinematicEffectHandle> handles = CINEMATIC.remove(level);
        if (handles == null) {
            return 0;
        }
        handles.forEach(handle -> stopper.accept(level, handle));
        return handles.size();
    }

    static void clear(ServerLevel level) {
        GAMEPLAY.remove(level);
        SEGMENTED.remove(level);
        CINEMATIC.remove(level);
    }
}
