package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.api.client.impulse.ClientEffectImpulses;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ContentOrbitalLaserImpulsesTest {
    private static final int IMPACT = ContentOrbitalLaserSettings.phasePlan()
        .phaseStartTick(ContentOrbitalLaserSettings.PHASE_IMPACT);
    private static final int SUSTAIN = ContentOrbitalLaserSettings.phasePlan()
        .phaseStartTick(ContentOrbitalLaserSettings.PHASE_SUSTAIN);
    private static final int COLLAPSE = ContentOrbitalLaserSettings.phasePlan()
        .phaseStartTick(ContentOrbitalLaserSettings.PHASE_COLLAPSE);

    @BeforeEach
    @AfterEach
    void clearGlobalState() {
        ContentOrbitalLaserImpulses.clear();
        ClientEffectImpulses.clear();
    }

    @Test
    void impactFiresOnceAtTheImpactPhase() {
        UUID instanceId = UUID.randomUUID();

        tickAt(instanceId, IMPACT - 1);
        assertEquals(0, ClientEffectImpulses.activeShakeCount());

        tickAt(instanceId, IMPACT);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());
        for (int age = IMPACT + 1; age < SUSTAIN; age++) {
            tickAt(instanceId, age);
        }
        assertEquals(1, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void sustainAddsTheBedAndPeriodicPulses() {
        UUID instanceId = UUID.randomUUID();

        tickAt(instanceId, 0);
        tickAt(instanceId, IMPACT);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());

        tickAt(instanceId, SUSTAIN);
        assertEquals(3, ClientEffectImpulses.activeShakeCount());

        tickAt(instanceId, SUSTAIN + 15);
        assertEquals(3, ClientEffectImpulses.activeShakeCount());
        tickAt(instanceId, SUSTAIN + 16);
        assertEquals(4, ClientEffectImpulses.activeShakeCount());
        tickAt(instanceId, SUSTAIN + 32);
        assertEquals(5, ClientEffectImpulses.activeShakeCount());
        tickAt(instanceId, SUSTAIN + 96);
        assertEquals(6, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void collapseAndAftermathFireAfterSustain() {
        UUID instanceId = UUID.randomUUID();

        tickAt(instanceId, 0);
        for (int age = IMPACT; age < COLLAPSE; age++) {
            tickAt(instanceId, age);
        }
        int beforeCollapse = ClientEffectImpulses.activeShakeCount();
        for (int age = COLLAPSE; age < ContentOrbitalLaserSettings.TOTAL_TICKS; age++) {
            tickAt(instanceId, age);
        }
        assertTrue(ClientEffectImpulses.activeShakeCount() > beforeCollapse + 1);
    }

    @Test
    void lateJoinSkipsPastBeatsAndResumesTheNextPulse() {
        UUID instanceId = UUID.randomUUID();

        tickAt(instanceId, SUSTAIN + 36);
        assertEquals(0, ClientEffectImpulses.activeShakeCount());

        tickAt(instanceId, SUSTAIN + 48);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void rewindReArmsTheImpulseSchedule() {
        UUID instanceId = UUID.randomUUID();

        tickAt(instanceId, 0);
        tickAt(instanceId, IMPACT);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());

        tickAt(instanceId, 0);
        tickAt(instanceId, IMPACT);
        assertEquals(2, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void tracksInstancesIndependently() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        tickAt(first, 0);
        tickAt(second, 0);
        tickAt(first, IMPACT);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());

        tickAt(second, IMPACT);
        assertEquals(2, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void evictsTheOldestTrackerAtCapacity() {
        UUID oldest = UUID.randomUUID();
        tickAt(oldest, 0);
        for (int index = 0; index < ContentOrbitalLaserImpulses.MAX_TRACKED; index++) {
            tickAt(UUID.randomUUID(), 0);
        }

        tickAt(oldest, SUSTAIN + 36);
        assertEquals(0, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void clearDropsEveryTracker() {
        UUID instanceId = UUID.randomUUID();
        tickAt(instanceId, 0);
        tickAt(instanceId, IMPACT);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());

        ContentOrbitalLaserImpulses.clear();
        ClientEffectImpulses.clear();
        tickAt(instanceId, 0);
        tickAt(instanceId, IMPACT);
        assertEquals(1, ClientEffectImpulses.activeShakeCount());
    }

    @Test
    void sustainCompositionKeepsAnImpulseActiveAndBounded() {
        UUID instanceId = UUID.randomUUID();
        Vec3 listener = new Vec3(10.0, 64.0, -5.0);

        for (int age = 0; age < ContentOrbitalLaserSettings.TOTAL_TICKS; age++) {
            tickAt(instanceId, age);
            if (age >= SUSTAIN && age < COLLAPSE) {
                assertTrue(ClientEffectImpulses.activeShakeCount() > 0,
                    "no active impulse at " + age);
                assertTrue(ClientEffectImpulses.activeShakeCount() <= 4,
                    "too many impulses at " + age);
            }
            ClientEffectImpulses.tick();
            var offsets = ClientEffectImpulses.shakeOffsets(listener, 0.0F);
            assertTrue(Math.abs(offsets.x) <= ClientEffectImpulses.MAX_TOTAL_ANGLE_DEGREES);
            assertTrue(Math.abs(offsets.y) <= ClientEffectImpulses.MAX_TOTAL_ANGLE_DEGREES);
            assertTrue(Math.abs(offsets.z) <= ClientEffectImpulses.MAX_TOTAL_ANGLE_DEGREES);
        }
    }

    private static void tickAt(UUID instanceId, int ageTicks) {
        StartVisualEffectPayload payload = TestVisualEffects.payload(
            ContentEffectIds.ORBITAL_LASER,
            instanceId,
            new Vec3(10.0, 64.0, -5.0),
            6.0F,
            ContentOrbitalLaserSettings.TOTAL_TICKS,
            1234L
        );
        EffectRenderContext context = TestVisualEffects.context(
            payload,
            ageTicks,
            ageTicks / (float)ContentOrbitalLaserSettings.TOTAL_TICKS
        );
        ContentOrbitalLaserImpulses.tick(context);
    }
}
