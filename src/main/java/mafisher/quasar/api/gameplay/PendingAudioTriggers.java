package mafisher.quasar.api.gameplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Bounded buffer for audio triggers fired before the session audio plan starts. */
final class PendingAudioTriggers {
    private static final int MAX_TRIGGERS = 16;

    private final List<PendingAudioTrigger> triggers = new ArrayList<>();

    void add(String triggerId, Vec3 position) {
        if (this.triggers.size() >= MAX_TRIGGERS) {
            this.triggers.remove(0);
        }
        this.triggers.add(new PendingAudioTrigger(triggerId, position));
    }

    void flush(ServerLevel level, UUID sessionId) {
        for (PendingAudioTrigger trigger : this.triggers) {
            EffectAudioService.trigger(level, sessionId, trigger.triggerId(), trigger.position());
        }
        this.triggers.clear();
    }

    void clear() {
        this.triggers.clear();
    }

    private record PendingAudioTrigger(String triggerId, Vec3 position) {
        private PendingAudioTrigger {
            Objects.requireNonNull(triggerId, "triggerId");
        }
    }
}
