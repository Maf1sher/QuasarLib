package mafisher.quasar.effect.gameplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectScope;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread registry for active gameplay effect sessions. Sessions are transient: they are not
 * saved, and level unload or server shutdown closes them.
 */
public final class GameplayEffectService {
    /** Provisional safety cap; it is not a balance budget and needs measurement before adoption. */
    public static final int MAX_ACTIVE_SESSIONS_PER_LEVEL = 128;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, Map<UUID, ActiveSession>> ACTIVE_SESSIONS = new WeakHashMap<>();

    private GameplayEffectService() {
    }

    /**
     * Starts a session and calls {@link GameplayEffectController#onStart} before registering it.
     * A failed start closes the scope and leaves no active session behind.
     */
    public static GameplayEffectHandle start(
        ServerLevel level,
        GameplayEffectSpec spec,
        GameplayEffectController controller
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(controller, "controller");
        if (level.isClientSide()) {
            throw new IllegalStateException("Gameplay effects are server-authoritative");
        }

        Map<UUID, ActiveSession> sessions = activeSessions(level);
        if (sessions.size() >= MAX_ACTIVE_SESSIONS_PER_LEVEL) {
            LOGGER.warn(
                "Rejected gameplay effect {} in {}: active session cap {} reached",
                spec.typeId(),
                level.dimension().location(),
                MAX_ACTIVE_SESSIONS_PER_LEVEL
            );
            throw new IllegalStateException("Too many active gameplay effects in this level");
        }

        EffectScope scope = new EffectScope(level);
        GameplayEffectContext context = new GameplayEffectContext(level, scope, spec, scope.sessionId());
        try {
            controller.onStart(context);
            controller.onPhaseEnter(context, 0);
        } catch (RuntimeException | Error exception) {
            context.close();
            scope.close();
            if (sessions.isEmpty()) {
                ACTIVE_SESSIONS.remove(level);
            }
            throw exception;
        }

        ActiveSession session = new ActiveSession(spec, controller, scope, context);
        sessions.put(session.id, session);
        if (context.isStopRequested()) {
            finish(level, session, GameplayEffectEndReason.CANCELLED);
        }
        return new GameplayEffectHandle(session.id);
    }

    /** Stops one session; unknown handles are no-ops and repeated stops are safe. */
    public static void stop(ServerLevel level, GameplayEffectHandle handle, GameplayEffectEndReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(reason, "reason");
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        ActiveSession session = sessions == null ? null : sessions.get(handle.sessionId());
        if (session != null) {
            finish(level, session, reason);
        }
    }

    /** Stops every session in one level and returns how many were active. */
    public static int stopAll(ServerLevel level, GameplayEffectEndReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(reason, "reason");
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions == null) {
            return 0;
        }
        int count = sessions.size();
        for (ActiveSession session : new ArrayList<>(sessions.values())) {
            finish(level, session, reason);
        }
        ACTIVE_SESSIONS.remove(level);
        return count;
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        return sessions == null ? 0 : sessions.size();
    }

    /** Advances every active session in one level by one server tick. */
    public static void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions == null || sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
            return;
        }

        for (ActiveSession session : new ArrayList<>(sessions.values())) {
            if (sessions.containsKey(session.id)) {
                tickSession(level, session);
            }
        }
        if (sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
        }
    }

    private static void tickSession(ServerLevel level, ActiveSession session) {
        GameplayEffectContext context = session.context;
        int nextAge = context.ageTicks() + 1;
        if (nextAge >= session.spec.phases().totalDurationTicks()) {
            context.endAt(session.spec.phases().totalDurationTicks());
            finish(level, session, GameplayEffectEndReason.EXPIRED);
            return;
        }
        if (ownerUnavailable(level, session.spec)) {
            finish(level, session, GameplayEffectEndReason.OWNER_UNAVAILABLE);
            return;
        }

        int previousPhase = context.phaseIndex();
        context.beginTick(nextAge);
        try {
            int phaseIndex = context.phaseIndex();
            if (phaseIndex != previousPhase) {
                session.controller.onPhaseEnter(context, phaseIndex);
                if (context.isStopRequested()) {
                    finish(level, session, GameplayEffectEndReason.CANCELLED);
                    return;
                }
            }
            session.controller.onTick(context);
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Gameplay effect {} failed on tick {}",
                session.spec.typeId(),
                nextAge,
                exception
            );
            finish(level, session, GameplayEffectEndReason.FAILED);
            return;
        }

        if (context.isStopRequested()) {
            finish(level, session, GameplayEffectEndReason.CANCELLED);
        }
    }

    private static boolean ownerUnavailable(ServerLevel level, GameplayEffectSpec spec) {
        if (spec.ownerId() == null || spec.ownerPolicy() == OwnerPolicy.CONTINUE) {
            return false;
        }
        Entity owner = level.getEntity(spec.ownerId());
        return owner == null || owner.isRemoved() || !owner.isAlive() || owner.level() != level;
    }

    private static void finish(ServerLevel level, ActiveSession session, GameplayEffectEndReason reason) {
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions == null || sessions.remove(session.id) == null) {
            return;
        }

        try {
            session.controller.onEnd(session.context, reason);
        } catch (RuntimeException exception) {
            LOGGER.error("Gameplay effect {} failed in onEnd", session.spec.typeId(), exception);
        } finally {
            session.context.close();
            session.scope.close();
        }
        if (sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
        }
    }

    private static Map<UUID, ActiveSession> activeSessions(ServerLevel level) {
        return ACTIVE_SESSIONS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    }

    private static final class ActiveSession {
        private final UUID id;
        private final GameplayEffectSpec spec;
        private final GameplayEffectController controller;
        private final EffectScope scope;
        private final GameplayEffectContext context;

        private ActiveSession(
            GameplayEffectSpec spec,
            GameplayEffectController controller,
            EffectScope scope,
            GameplayEffectContext context
        ) {
            this.id = context.sessionId();
            this.spec = spec;
            this.controller = controller;
            this.scope = scope;
            this.context = context;
        }
    }
}
