package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import mafisher.quasar.api.client.ClientEffectImpulses;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ContentBlackHoleImpulsesTest {
    private static final float IGNITION = ContentBlackHoleCinematics.IGNITION;
    private static final float SHOCKWAVE = ContentBlackHoleCinematics.IGNITION + 0.010F;
    private static final float COLLAPSE = ContentBlackHoleCinematics.DESTABILISE + 0.010F;
    private static final float IMPLOSION = ContentBlackHoleCinematics.IMPLOSION;

    @BeforeEach
    @AfterEach
    void clearGlobalState() {
        ContentBlackHoleImpulses.clear();
        ClientEffectImpulses.clear();
    }

    @Test
    void firesEveryMilestoneOnceInOrder() {
        UUID instanceId = UUID.randomUUID();

        tickAt(instanceId, IGNITION - 0.001F);
        assertEquals(0, ClientEffectImpulses.activeShakeCount());

        tickAt(instanceId, IGNITION + 0.001F);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());
        tickAt(instanceId, SHOCKWAVE + 0.001F);
        assertEquals(2, ClientEffectImpulses.activeShakeCount());
        tickAt(instanceId, COLLAPSE + 0.001F);
        assertEquals(3, ClientEffectImpulses.activeShakeCount());
        tickAt(instanceId, IMPLOSION + 0.001F);
        assertEquals(4, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void doesNotRepeatAMilestoneAtTheSameProgress() {
        UUID instanceId = UUID.randomUUID();
        tickAt(instanceId, 0.0F);
        tickAt(instanceId, IMPLOSION + 0.001F);
        assertEquals(4, ClientEffectImpulses.activeShakeCount());

        for (int index = 0; index < 5; index++) {
            tickAt(instanceId, IMPLOSION + 0.001F);
        }

        assertEquals(4, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void rewindReArmsEveryMilestone() {
        UUID instanceId = UUID.randomUUID();
        tickAt(instanceId, 0.0F);
        tickAt(instanceId, IMPLOSION + 0.001F);
        assertEquals(4, ClientEffectImpulses.activeShakeCount());

        ClientEffectImpulses.clear();
        tickAt(instanceId, 0.0F);
        tickAt(instanceId, IGNITION + 0.001F);

        assertEquals(1, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void tracksInstancesIndependently() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        tickAt(first, 0.0F);
        tickAt(second, 0.0F);
        tickAt(first, IGNITION + 0.001F);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());

        tickAt(second, IGNITION + 0.001F);
        assertEquals(2, ClientEffectImpulses.activeShakeCount());

        tickAt(first, SHOCKWAVE + 0.001F);
        assertEquals(3, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void evictsTheOldestTrackerAtCapacity() {
        UUID oldest = UUID.randomUUID();
        tickAt(oldest, 0.0F);
        for (int index = 0; index < ContentBlackHoleImpulses.MAX_TRACKED; index++) {
            tickAt(UUID.randomUUID(), 0.0F);
        }

        tickAt(oldest, COLLAPSE + 0.001F);

        assertEquals(0, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void clearRemovesEveryTracker() {
        UUID instanceId = UUID.randomUUID();
        tickAt(instanceId, 0.0F);
        tickAt(instanceId, IMPLOSION + 0.001F);
        assertEquals(4, ClientEffectImpulses.activeShakeCount());

        ClientEffectImpulses.clear();
        ContentBlackHoleImpulses.clear();
        tickAt(instanceId, 0.0F);
        tickAt(instanceId, IMPLOSION + 0.001F);

        assertEquals(4, ClientEffectImpulses.activeShakeCount());
    }

    private static void tickAt(UUID instanceId, float progress) {
        ContentBlackHoleImpulses.tick(TestVisualEffects.context(
            ContentEffectIds.BLACK_HOLE,
            instanceId,
            Vec3.ZERO,
            1.0F,
            1200,
            progress
        ));
    }
}
