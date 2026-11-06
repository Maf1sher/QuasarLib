package mafisher.quasar.effect.gameplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread registry for active gameplay effect sessions. Sessions are resumable through their
 * descriptors; level unload and server shutdown close them without deleting persisted state.
 */
public final class GameplayEffectService {
    /** Provisional safety cap; it is not a balance budget and needs measurement before adoption. */
    public static final int MAX_ACTIVE_SESSIONS_PER_LEVEL = 128;
    /** Provisional per-owner cap; it is a safety ceiling, not a measured balance target. */
    public static final int MAX_ACTIVE_SESSIONS_PER_OWNER = OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER;
    /** Provisional per-owner start-rate cap; it is a safety ceiling, not a measured balance target. */
    public static final int MAX_STARTS_PER_OWNER_PER_TICK = OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK;
    /**
     * Provisional level-wide ceiling for block changes applied per server tick. It keeps many
     * sessions from exhausting the tick; it is a safety cap, not a measured balance budget.
     */
    public static final int MAX_BLOCK_CHANGES_PER_LEVEL_TICK = 512;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, Map<UUID, ActiveSession>> ACTIVE_SESSIONS = new WeakHashMap<>();
    private static final Map<ServerLevel, OwnerAdmission> OWNER_ADMISSIONS = new WeakHashMap<>();

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
        return start(level, spec, controller, EffectPersistenceOptions.DEFAULT);
    }

    /**
     * Starts a session and calls {@link GameplayEffectController#onStart} before registering it.
     * A failed start closes the scope and leaves no active session behind.
     */
    public static GameplayEffectHandle start(
        ServerLevel level,
        GameplayEffectSpec spec,
        GameplayEffectController controller,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(options, "options");
        if (level.isClientSide()) {
            throw new IllegalStateException("Gameplay effects are server-authoritative");
        }
        if (!destructivePolicyAllows(level, spec, options.capabilities())) {
            LOGGER.warn(
                "Rejected destructive gameplay effect {} in {} for owner {}: server policy denied",
                spec.typeId(),
                level.dimension().location(),
                spec.ownerId()
            );
            throw new IllegalStateException("Destructive gameplay effects are disabled by server policy");
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

        OwnerAdmission admission = ownerAdmission(level);
        OwnerAdmission.Result admissionResult = admission.tryStart(spec.ownerId(), level.getGameTime());
        if (admissionResult != OwnerAdmission.Result.ACCEPTED) {
            LOGGER.warn(
                "Rejected gameplay effect {} in {} for owner {}: {}",
                spec.typeId(),
                level.dimension().location(),
                spec.ownerId(),
                admissionResult
            );
            throw new IllegalStateException(admissionResult == OwnerAdmission.Result.OWNER_SESSION_CAP
                ? "Too many active gameplay effects for this owner"
                : "Gameplay effect start rate exceeded for this owner");
        }

        EffectScope scope = null;
        GameplayEffectContext context = null;
        try {
            scope = new EffectScope(level);
            context = new GameplayEffectContext(level, scope, spec, scope.sessionId(), options.capabilities());
            controller.onStart(context);
            controller.onPhaseEnter(context, 0);
        } catch (RuntimeException | Error exception) {
            admission.recordEnd(spec.ownerId());
            if (context != null) {
                context.close();
            }
            if (scope != null) {
                scope.close();
            }
            if (sessions.isEmpty()) {
                ACTIVE_SESSIONS.remove(level);
            }
            throw exception;
        }

        EffectDescriptor descriptor = descriptor(level, spec, context.sessionId(), level.getGameTime(), options);
        ActiveSession session = new ActiveSession(spec, controller, scope, context, descriptor, level.getGameTime());
        sessions.put(session.id, session);
        EffectPersistenceService.recordStart(level, descriptor);
        if (context.isStopRequested()) {
            finish(level, session, GameplayEffectEndReason.CANCELLED, true);
        }
        return new GameplayEffectHandle(session.id);
    }

    private static EffectDescriptor descriptor(
        ServerLevel level,
        GameplayEffectSpec spec,
        UUID sessionId,
        long startGameTime,
        EffectPersistenceOptions options
    ) {
        return new EffectDescriptor(
            sessionId,
            EffectKind.GAMEPLAY,
            spec.typeId(),
            level.dimension().location(),
            new EffectTransform(new EffectPoint(spec.anchor().x, spec.anchor().y, spec.anchor().z), 0.0F, 0.0F, 0.0F),
            1.0F,
            spec.seed(),
            startGameTime,
            spec.phases().totalDurationTicks(),
            EffectAudience.dimensionWide(),
            options.capabilities(),
            options.persistence(),
            null,
            GameplayEffectNbt.saveDescriptorState(spec, options.state())
        );
    }

    /**
     * Restores one gameplay session with its original identity, start time, and consumer state.
     * The controller factory must be registered in {@link GameplayEffectTypeRegistry}.
     */
    public static GameplayEffectHandle restore(
        ServerLevel level,
        GameplayEffectSpec spec,
        UUID sessionId,
        long startGameTime,
        EffectDescriptor descriptor,
        GameplayEffectController controller
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(controller, "controller");
        if (level.isClientSide()) {
            throw new IllegalStateException("Gameplay effects are server-authoritative");
        }

        int age = (int)Math.min(Integer.MAX_VALUE, Math.max(0L, level.getGameTime() - startGameTime));
        if (age >= spec.phases().totalDurationTicks()) {
            EffectPersistenceService.recordStop(level, sessionId);
            return null;
        }
        if (!destructivePolicyAllows(level, spec, descriptor.capabilities())) {
            LOGGER.info(
                "Deferred destructive gameplay effect {} in {}: server policy denied",
                spec.typeId(),
                level.dimension().location()
            );
            return null;
        }

        Map<UUID, ActiveSession> sessions = activeSessions(level);
        if (sessions.size() >= MAX_ACTIVE_SESSIONS_PER_LEVEL) {
            LOGGER.warn("Could not restore gameplay effect {}: active session cap reached", spec.typeId());
            return null;
        }
        OwnerAdmission admission = ownerAdmission(level);
        if (spec.ownerId() != null && admission.activeSessions(spec.ownerId()) >= MAX_ACTIVE_SESSIONS_PER_OWNER) {
            LOGGER.warn("Could not restore gameplay effect {}: owner session cap reached", spec.typeId());
            return null;
        }

        EffectScope scope = new EffectScope(level, sessionId);
        GameplayEffectContext context = new GameplayEffectContext(
            level,
            scope,
            spec,
            sessionId,
            descriptor.capabilities(),
            age,
            GameplayEffectNbt.loadConsumerState(descriptor)
        );
        try {
            controller.onResume(context);
        } catch (RuntimeException | Error exception) {
            context.close();
            scope.close(false);
            throw exception;
        }
        admission.adopt(spec.ownerId());

        ActiveSession session = new ActiveSession(spec, controller, scope, context, descriptor, startGameTime);
        sessions.put(session.id, session);
        if (context.isStopRequested()) {
            finish(level, session, GameplayEffectEndReason.CANCELLED, true);
        }
        return new GameplayEffectHandle(session.id);
    }

    /** Scope of one active session, used to reattach persisted child visuals after restore. */
    public static EffectScope scope(ServerLevel level, GameplayEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        ActiveSession session = sessions == null ? null : sessions.get(handle.sessionId());
        return session == null ? null : session.scope;
    }

    /** Stops one session; unknown handles are no-ops and repeated stops are safe. */
    public static void stop(ServerLevel level, GameplayEffectHandle handle, GameplayEffectEndReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(reason, "reason");
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        ActiveSession session = sessions == null ? null : sessions.get(handle.sessionId());
        if (session != null) {
            finish(level, session, reason, true);
        }
    }

    /** Stops every session in one level and returns how many were active. */
    public static int stopAll(ServerLevel level, GameplayEffectEndReason reason) {
        return stopAll(level, reason, true);
    }

    /**
     * Stops every session in one level. Level unload and shutdown pass {@code false} so the
     * descriptors stay available for restore; an explicit stop passes {@code true}.
     */
    public static int stopAll(ServerLevel level, GameplayEffectEndReason reason, boolean persistRemoval) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(reason, "reason");
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions == null) {
            OWNER_ADMISSIONS.remove(level);
            return 0;
        }
        int count = sessions.size();
        for (ActiveSession session : new ArrayList<>(sessions.values())) {
            finish(level, session, reason, persistRemoval);
        }
        ACTIVE_SESSIONS.remove(level);
        OWNER_ADMISSIONS.remove(level);
        return count;
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        return sessions == null ? 0 : sessions.size();
    }

    /** Advances every active session in one level by one server tick. */
    public static void tick(ServerLevel level) {
        tick(level, MAX_BLOCK_CHANGES_PER_LEVEL_TICK);
    }

    /** Package-private overload that lets tests exercise the level-wide block application budget. */
    static void tick(ServerLevel level, int blockChangeBudget) {
        Objects.requireNonNull(level, "level");
        if (blockChangeBudget < 0) {
            throw new IllegalArgumentException("Block change application budget cannot be negative");
        }
        Map<UUID, ActiveSession> sessions = ACTIVE_SESSIONS.get(level);
        if (sessions == null || sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
            return;
        }

        int remainingBlockChanges = blockChangeBudget;
        for (ActiveSession session : new ArrayList<>(sessions.values())) {
            if (sessions.containsKey(session.id)) {
                remainingBlockChanges = tickSession(level, session, remainingBlockChanges);
            }
        }
        if (sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
        }
    }

    private static int tickSession(ServerLevel level, ActiveSession session, int remainingBlockChanges) {
        GameplayEffectContext context = session.context;
        int nextAge = context.ageTicks() + 1;
        if (nextAge >= session.spec.phases().totalDurationTicks()) {
            context.endAt(session.spec.phases().totalDurationTicks());
            try {
                remainingBlockChanges = applyBlockChanges(session, remainingBlockChanges);
            } catch (RuntimeException exception) {
                LOGGER.error(
                    "Gameplay effect {} failed to apply block changes on expiry",
                    session.spec.typeId(),
                    exception
                );
            }
            finish(level, session, GameplayEffectEndReason.EXPIRED, true);
            return remainingBlockChanges;
        }
        if (ownerUnavailable(level, session.spec)) {
            finish(level, session, GameplayEffectEndReason.OWNER_UNAVAILABLE, true);
            return remainingBlockChanges;
        }

        int previousPhase = context.phaseIndex();
        context.beginTick(nextAge);
        try {
            int phaseIndex = context.phaseIndex();
            if (phaseIndex != previousPhase) {
                session.controller.onPhaseEnter(context, phaseIndex);
                if (context.isStopRequested()) {
                    remainingBlockChanges = applyBlockChanges(session, remainingBlockChanges);
                    finish(level, session, GameplayEffectEndReason.CANCELLED, true);
                    return remainingBlockChanges;
                }
            }
            session.controller.onTick(context);
            remainingBlockChanges = applyBlockChanges(session, remainingBlockChanges);
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Gameplay effect {} failed on tick {}",
                session.spec.typeId(),
                nextAge,
                exception
            );
            finish(level, session, GameplayEffectEndReason.FAILED, true);
            return remainingBlockChanges;
        }

        if (context.consumeStateDirty()) {
            session.descriptor = session.descriptor.withState(
                GameplayEffectNbt.saveDescriptorState(session.spec, context.rawPersistentState())
            );
            EffectPersistenceService.recordUpdate(level, session.descriptor);
        }

        if (context.isStopRequested()) {
            finish(level, session, GameplayEffectEndReason.CANCELLED, true);
        }
        return remainingBlockChanges;
    }

    private static int applyBlockChanges(ActiveSession session, int remainingBlockChanges) {
        int perSession = Math.min(session.spec.budget().maxBlockChanges(), remainingBlockChanges);
        if (perSession <= 0) {
            return remainingBlockChanges;
        }
        return remainingBlockChanges - session.context.applyBlockChanges(perSession);
    }

    private static boolean destructivePolicyAllows(
        ServerLevel level,
        GameplayEffectSpec spec,
        EffectCapabilities capabilities
    ) {
        if (!capabilities.destructive()) {
            return true;
        }
        return DestructiveEffectPolicies.allows(
            new DestructiveEffectPolicyContext(
                level,
                spec.typeId(),
                spec.ownerId(),
                spec.anchor(),
                capabilities
            )
        );
    }

    private static boolean ownerUnavailable(ServerLevel level, GameplayEffectSpec spec) {
        if (spec.ownerId() == null || spec.ownerPolicy() == OwnerPolicy.CONTINUE) {
            return false;
        }
        Entity owner = level.getEntity(spec.ownerId());
        return owner == null || owner.isRemoved() || !owner.isAlive() || owner.level() != level;
    }

    private static void finish(
        ServerLevel level,
        ActiveSession session,
        GameplayEffectEndReason reason,
        boolean persistRemoval
    ) {
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
            session.scope.close(persistRemoval);
            OwnerAdmission admission = OWNER_ADMISSIONS.get(level);
            if (admission != null) {
                admission.recordEnd(session.spec.ownerId());
            }
            if (persistRemoval) {
                EffectPersistenceService.recordStop(level, session.id);
            }
        }
        if (sessions.isEmpty()) {
            ACTIVE_SESSIONS.remove(level);
        }
    }

    private static Map<UUID, ActiveSession> activeSessions(ServerLevel level) {
        return ACTIVE_SESSIONS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    }

    private static OwnerAdmission ownerAdmission(ServerLevel level) {
        return OWNER_ADMISSIONS.computeIfAbsent(level, ignored -> new OwnerAdmission());
    }

    private static final class ActiveSession {
        private final UUID id;
        private final GameplayEffectSpec spec;
        private final GameplayEffectController controller;
        private final EffectScope scope;
        private final GameplayEffectContext context;
        private final long startGameTime;
        private EffectDescriptor descriptor;

        private ActiveSession(
            GameplayEffectSpec spec,
            GameplayEffectController controller,
            EffectScope scope,
            GameplayEffectContext context,
            EffectDescriptor descriptor,
            long startGameTime
        ) {
            this.id = context.sessionId();
            this.spec = spec;
            this.controller = controller;
            this.scope = scope;
            this.context = context;
            this.descriptor = descriptor;
            this.startGameTime = startGameTime;
        }
    }
}
