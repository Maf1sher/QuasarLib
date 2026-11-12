package mafisher.quasar.api.gameplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provisional per-owner admission limits for gameplay sessions. The values are safety ceilings, not measured balance
 * targets, and are tracked separately from the per-level session cap.
 */
final class OwnerAdmission {
    static final int MAX_ACTIVE_SESSIONS_PER_OWNER = 8;
    static final int MAX_STARTS_PER_OWNER_PER_TICK = 1;

    private final Map<UUID, OwnerState> owners = new HashMap<>();
    private final int maxSessionsPerOwner;
    private final int maxStartsPerTick;

    OwnerAdmission(int maxSessionsPerOwner, int maxStartsPerTick) {
        if (maxSessionsPerOwner < 1 || maxStartsPerTick < 1) {
            throw new IllegalArgumentException("Owner admission budgets must be positive");
        }
        this.maxSessionsPerOwner = maxSessionsPerOwner;
        this.maxStartsPerTick = maxStartsPerTick;
    }

    Result tryStart(UUID ownerId, long gameTime) {
        if (ownerId == null) {
            return Result.ACCEPTED;
        }
        OwnerState state = this.owners.computeIfAbsent(ownerId, ignored -> new OwnerState());
        if (state.activeSessions >= this.maxSessionsPerOwner) {
            return Result.OWNER_SESSION_CAP;
        }
        if (state.startTick != gameTime) {
            state.startTick = gameTime;
            state.startsThisTick = 0;
        }
        if (state.startsThisTick >= this.maxStartsPerTick) {
            return Result.OWNER_START_RATE;
        }
        state.activeSessions++;
        state.startsThisTick++;
        return Result.ACCEPTED;
    }

    void recordEnd(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        OwnerState state = this.owners.get(ownerId);
        if (state != null && state.activeSessions > 0) {
            state.activeSessions--;
        }
    }

    /** Counts a restored session without consuming the per-tick start rate. */
    void adopt(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        OwnerState state = this.owners.computeIfAbsent(ownerId, ignored -> new OwnerState());
        state.activeSessions++;
    }

    int activeSessions(UUID ownerId) {
        if (ownerId == null) {
            return 0;
        }
        OwnerState state = this.owners.get(ownerId);
        return state == null ? 0 : state.activeSessions;
    }

    enum Result {
        ACCEPTED,
        OWNER_SESSION_CAP,
        OWNER_START_RATE
    }

    private static final class OwnerState {
        private int activeSessions;
        private long startTick = Long.MIN_VALUE;
        private int startsThisTick;
    }
}
