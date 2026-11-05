package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EffectHitCooldownsTest {
    @Test
    void enforcesOncePerTargetAndTimedRehits() {
        EffectHitCooldowns cooldowns = new EffectHitCooldowns();
        UUID target = UUID.randomUUID();

        assertTrue(cooldowns.canHit(target, 0, 0));
        cooldowns.recordHit(target, 0);
        assertFalse(cooldowns.canHit(target, 100, 0));
        assertFalse(cooldowns.canHit(target, 19, 20));
        assertTrue(cooldowns.canHit(target, 20, 20));
    }

    @Test
    void validatesTickInputs() {
        EffectHitCooldowns cooldowns = new EffectHitCooldowns();
        UUID target = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> cooldowns.canHit(target, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> cooldowns.canHit(target, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> cooldowns.recordHit(target, -1));
    }
}
