package mafisher.quasar.effect.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerAdmissionTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void acceptsFirstStart() {
        OwnerAdmission admission = new OwnerAdmission(
            OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER,
            OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK
        );

        assertEquals(OwnerAdmission.Result.ACCEPTED, admission.tryStart(OWNER, 0L));
        assertEquals(1, admission.activeSessions(OWNER));
    }

    @Test
    void rejectsSecondStartInTheSameTick() {
        OwnerAdmission admission = new OwnerAdmission(
            OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER,
            OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK
        );
        admission.tryStart(OWNER, 42L);

        assertEquals(OwnerAdmission.Result.OWNER_START_RATE, admission.tryStart(OWNER, 42L));
        assertEquals(1, admission.activeSessions(OWNER));
    }

    @Test
    void acceptsAnotherStartOnTheNextTick() {
        OwnerAdmission admission = new OwnerAdmission(
            OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER,
            OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK
        );
        admission.tryStart(OWNER, 42L);

        assertEquals(OwnerAdmission.Result.ACCEPTED, admission.tryStart(OWNER, 43L));
        assertEquals(2, admission.activeSessions(OWNER));
    }

    @Test
    void rejectsStartsAtTheOwnerSessionCap() {
        OwnerAdmission admission = new OwnerAdmission(
            OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER,
            OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK
        );
        for (int tick = 0; tick < OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER; tick++) {
            assertEquals(OwnerAdmission.Result.ACCEPTED, admission.tryStart(OWNER, tick));
        }

        assertEquals(
            OwnerAdmission.Result.OWNER_SESSION_CAP,
            admission.tryStart(OWNER, OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER)
        );
        assertEquals(OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER, admission.activeSessions(OWNER));
    }

    @Test
    void endingASessionFreesOneSlot() {
        OwnerAdmission admission = new OwnerAdmission(
            OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER,
            OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK
        );
        for (int tick = 0; tick < OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER; tick++) {
            admission.tryStart(OWNER, tick);
        }

        admission.recordEnd(OWNER);

        assertEquals(OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER - 1, admission.activeSessions(OWNER));
        assertEquals(
            OwnerAdmission.Result.ACCEPTED,
            admission.tryStart(OWNER, OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER)
        );
    }

    @Test
    void ownerlessSessionsAreNotLimitedByOwnerState() {
        OwnerAdmission admission = new OwnerAdmission(
            OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER,
            OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK
        );

        for (int attempt = 0; attempt < 100; attempt++) {
            assertEquals(OwnerAdmission.Result.ACCEPTED, admission.tryStart(null, 1L));
        }
        assertEquals(0, admission.activeSessions(null));
    }

    @Test
    void endingAnUnknownOwnerIsANoOp() {
        OwnerAdmission admission = new OwnerAdmission(
            OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER,
            OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK
        );

        admission.recordEnd(OWNER);

        assertEquals(0, admission.activeSessions(OWNER));
    }
}
