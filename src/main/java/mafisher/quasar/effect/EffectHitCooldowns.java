package mafisher.quasar.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-effect hit history; a zero delay means each target can be hit only once. */
public final class EffectHitCooldowns {
    private final Map<UUID, Integer> lastHitTicks = new HashMap<>();

    public boolean canHit(UUID targetId, int effectAgeTicks, int rehitDelayTicks) {
        Objects.requireNonNull(targetId, "targetId");
        if (effectAgeTicks < 0 || rehitDelayTicks < 0) {
            throw new IllegalArgumentException("Hit ages and delays cannot be negative");
        }
        Integer lastHit = this.lastHitTicks.get(targetId);
        return lastHit == null || rehitDelayTicks > 0 && effectAgeTicks - lastHit >= rehitDelayTicks;
    }

    public void recordHit(UUID targetId, int effectAgeTicks) {
        Objects.requireNonNull(targetId, "targetId");
        if (effectAgeTicks < 0) {
            throw new IllegalArgumentException("Effect age cannot be negative");
        }
        this.lastHitTicks.put(targetId, effectAgeTicks);
    }
}
