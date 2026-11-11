package mafisher.quasar.effect.gameplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.EffectBudgets;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.LevelStateSlot;
import mafisher.quasar.effect.audio.EffectAudioPlan;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.BlockRollbackService;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread registry for active gameplay effect sessions. Sessions are resumable through their
 * descriptors; level unload and server shutdown close them without deleting persisted state.
 */
public final class GameplayEffectService {
    /** Default soft budget; it is a quality threshold, not a hard capability gate. */
    public static final int MAX_ACTIVE_SESSIONS_PER_LEVEL = 128;
    /** Default per-owner soft budget. */
    public static final int MAX_ACTIVE_SESSIONS_PER_OWNER = OwnerAdmission.MAX_ACTIVE_SESSIONS_PER_OWNER;
    /** Default per-owner start rate. Further starts are deferred, never rejected. */
    public static final int MAX_STARTS_PER_OWNER_PER_TICK = OwnerAdmission.MAX_STARTS_PER_OWNER_PER_TICK;
    /**
     * Legacy default for the level-wide block-change cap. The live value comes from
     * {@link EffectBudgets#blockChangesPerLevelTick()} and is configurable.
     */
    public static final int MAX_BLOCK_CHANGES_PER_LEVEL_TICK = 512;
    /**
     * How long a restored session may wait for its owner to become available before the normal
     * {@link OwnerPolicy#STOP_WHEN_UNAVAILABLE} path cancels it. Dedicated servers restore effects
     * before players log in, so an immediate owner check would roll every player-owned session back.
     */
    static final int RESTORED_OWNER_GRACE_TICKS = 6_000;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final LevelStateSlot<GameplayState> STATE = LevelStateSlot.create(GameplayState::new);
    private static Integer sessionBudgetOverride;
    private static Integer perOwnerBudgetOverride;
    private static Integer startRateOverride;

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

        PendingStart pending = PendingStart.fresh(
            UUID.randomUUID(), spec, controller, options, level.getGameTime()
        );
        if (tryStartFresh(level, pending)) {
            return new GameplayEffectHandle(pending.id);
        }
        if (enqueue(level, pending)) {
            EffectBudgetService.recordDeferral(level);
            LOGGER.info(
                "Deferred gameplay effect {} in {} for owner {}: capacity is full",
                spec.typeId(),
                level.dimension().location(),
                spec.ownerId()
            );
        }
        return new GameplayEffectHandle(pending.id);
    }

    /**
     * Attempts an immediate start. Returns {@code false} without callbacks when the level or
     * owner budget is full, so the caller can queue the start instead.
     */
    private static boolean tryStartFresh(ServerLevel level, PendingStart pending) {
        GameplayState state = state(level);
        Map<UUID, ActiveSession> sessions = state.sessions;
        if (sessions.size() >= sessionBudget()) {
            return false;
        }
        OwnerAdmission admission = ownerAdmission(level);
        if (admission.tryStart(pending.spec.ownerId(), level.getGameTime())
            != OwnerAdmission.Result.ACCEPTED) {
            return false;
        }

        long startGameTime = level.getGameTime();
        EffectScope scope = null;
        GameplayEffectContext context = null;
        BlockJournal journal = new BlockJournal();
        try {
            scope = new EffectScope(level, pending.id);
            context = new GameplayEffectContext(
                level, scope, pending.spec, pending.id, pending.options.capabilities(), journal
            );
            pending.controller.onStart(context);
            pending.controller.onPhaseEnter(context, 0);
        } catch (RuntimeException | Error exception) {
            admission.recordEnd(pending.spec.ownerId());
            if (context != null) {
                context.close();
            }
            if (scope != null) {
                scope.close();
            }
            cleanupIfEmpty(level, state);
            throw exception;
        }

        EffectDescriptor descriptor = descriptor(level, pending.spec, pending.id, startGameTime, pending.options);
        ActiveSession session = new ActiveSession(
            pending.spec, pending.controller, scope, context, descriptor, startGameTime, 0L
        );
        sessions.put(session.id, session);
        EffectPersistenceService.recordStart(level, descriptor);
        if (pending.options.capabilities().destructive()) {
            EffectPersistenceService.recordJournal(level, session.id, journal);
        }
        startAudio(level, session);
        if (context.isStopRequested()) {
            finish(level, session, GameplayEffectEndReason.CANCELLED, true);
        }
        return true;
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
            spec.scale(),
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
     * Restoring a session that is already active or pending keeps the existing session.
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
        GameplayEffectHandle handle = new GameplayEffectHandle(sessionId);
        if (isActive(level, handle) || isPending(level, handle)) {
            LOGGER.warn(
                "Skipped duplicate restore of gameplay effect {} in {}: the session is already known",
                spec.typeId(),
                level.dimension().location()
            );
            return handle;
        }

        BlockJournal journal = EffectPersistenceService.journalOrNull(level, sessionId);
        if (journal == null) {
            journal = new BlockJournal();
        }
        if (journal.isRollbackPending()) {
            BlockRollbackService.schedule(level, sessionId, journal);
            return null;
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

        PendingStart pending = PendingStart.restored(
            sessionId, spec, controller, descriptor, journal, startGameTime, level.getGameTime()
        );
        if (!tryRestoreNow(level, pending)) {
            if (enqueue(level, pending)) {
                EffectBudgetService.recordDeferral(level);
                LOGGER.info(
                    "Deferred restore of gameplay effect {} until its level or owner has capacity",
                    spec.typeId()
                );
            }
        }
        return new GameplayEffectHandle(sessionId);
    }

    /** Attempts to resume a persisted session immediately; returns {@code false} when capacity is full. */
    private static boolean tryRestoreNow(ServerLevel level, PendingStart pending) {
        GameplayState state = state(level);
        Map<UUID, ActiveSession> sessions = state.sessions;
        if (skipAlreadyActive(level, pending, sessions)) {
            return true;
        }
        if (sessions.size() >= sessionBudget()) {
            return false;
        }
        OwnerAdmission admission = ownerAdmission(level);
        if (pending.spec.ownerId() != null
            && admission.activeSessions(pending.spec.ownerId()) >= perOwnerBudget()) {
            return false;
        }

        int age = (int)Math.min(
            Integer.MAX_VALUE,
            Math.max(0L, level.getGameTime() - pending.startGameTime)
        );
        if (age >= pending.spec.phases().totalDurationTicks()) {
            EffectPersistenceService.recordStop(level, pending.id);
            return true;
        }
        EffectScope scope = new EffectScope(level, pending.id);
        GameplayEffectContext context = new GameplayEffectContext(
            level,
            scope,
            pending.spec,
            pending.id,
            pending.descriptor.capabilities(),
            pending.journal,
            age,
            GameplayEffectNbt.loadConsumerState(pending.descriptor)
        );
        try {
            pending.controller.onResume(context);
        } catch (RuntimeException | Error exception) {
            context.close();
            scope.close(false);
            throw exception;
        }
        admission.adopt(pending.spec.ownerId());

        ActiveSession session = new ActiveSession(
            pending.spec,
            pending.controller,
            scope,
            context,
            pending.descriptor,
            pending.startGameTime,
            level.getGameTime() + RESTORED_OWNER_GRACE_TICKS
        );
        sessions.put(session.id, session);
        startAudio(level, session);
        VisualEffectService.adoptOwnedBy(level, session.id, scope);
        if (context.isStopRequested()) {
            finish(level, session, GameplayEffectEndReason.CANCELLED, true);
        }
        return true;
    }

    private static boolean skipAlreadyActive(
        ServerLevel level,
        PendingStart pending,
        Map<UUID, ActiveSession> sessions
    ) {
        if (!sessions.containsKey(pending.id)) {
            return false;
        }
        LOGGER.warn(
            "Skipped duplicate restore of gameplay effect {} in {}: session {} is already active",
            pending.spec.typeId(),
            level.dimension().location(),
            pending.id
        );
        return true;
    }

    /** Scope of one active session, used to reattach persisted child visuals after restore. */
    public static EffectScope scope(ServerLevel level, GameplayEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        GameplayState state = STATE.peek(level);
        ActiveSession session = state == null ? null : state.sessions.get(handle.sessionId());
        return session == null ? null : session.scope;
    }

    /**
     * Stops one session or cancels one queued start; unknown handles are no-ops and repeated stops
     * are safe.
     */
    public static void stop(ServerLevel level, GameplayEffectHandle handle, GameplayEffectEndReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(reason, "reason");
        if (removePending(level, handle.sessionId(), true)) {
            return;
        }
        GameplayState state = STATE.peek(level);
        ActiveSession session = state == null ? null : state.sessions.get(handle.sessionId());
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
        GameplayState state = STATE.peek(level);
        if (state == null) {
            return 0;
        }
        clearPending(level, state, persistRemoval);
        int count = state.sessions.size();
        for (ActiveSession session : new ArrayList<>(state.sessions.values())) {
            finish(level, session, reason, persistRemoval);
        }
        STATE.remove(level);
        return count;
    }

    public static int activeCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        GameplayState state = STATE.peek(level);
        return state == null ? 0 : state.sessions.size();
    }

    /** Number of gameplay starts waiting for capacity in one level. */
    public static int pendingCount(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        GameplayState state = STATE.peek(level);
        return state == null ? 0 : state.pendingStarts.size();
    }

    /** Whether one gameplay session is currently active. */
    public static boolean isActive(ServerLevel level, GameplayEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        GameplayState state = STATE.peek(level);
        return state != null && state.sessions.containsKey(handle.sessionId());
    }

    /** Whether one gameplay start is queued and will run when capacity becomes available. */
    public static boolean isPending(ServerLevel level, GameplayEffectHandle handle) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handle, "handle");
        GameplayState state = STATE.peek(level);
        List<PendingStart> pending = state == null ? null : state.pendingStarts;
        if (pending == null) {
            return false;
        }
        for (PendingStart start : pending) {
            if (start.id.equals(handle.sessionId())) {
                return true;
            }
        }
        return false;
    }

    /** Advances every active session in one level by one server tick. */
    public static void tick(ServerLevel level) {
        tick(level, EffectBudgets.blockChangesPerLevelTick());
    }

    /** Package-private overload that lets tests exercise the level-wide block application budget. */
    static void tick(ServerLevel level, int blockChangeBudget) {
        Objects.requireNonNull(level, "level");
        if (blockChangeBudget < 0) {
            throw new IllegalArgumentException("Block change application budget cannot be negative");
        }
        TerrainEditService.beginTick(level);
        GameplayState state = STATE.peek(level);
        Map<UUID, ActiveSession> sessions = state == null ? null : state.sessions;
        int rollbackReserve = rollbackReserve(
            blockChangeBudget, BlockRollbackService.activeCount(level)
        );
        int remainingBlockChanges = blockChangeBudget - rollbackReserve;
        if (sessions != null && !sessions.isEmpty()) {
            for (ActiveSession session : new ArrayList<>(sessions.values())) {
                if (sessions.containsKey(session.id)) {
                    remainingBlockChanges = tickSession(level, session, remainingBlockChanges);
                }
            }
        }
        BlockRollbackService.tick(level, remainingBlockChanges + rollbackReserve);
        TerrainEditService.tick(level);
        drainPending(level);
        cleanupIfEmpty(level, state);
    }

    /**
     * Reserves a small share of the block budget for pending rollbacks so live sessions cannot
     * starve them forever when they consume the whole level tick allowance.
     */
    static int rollbackReserve(int blockChangeBudget, int activeRollbacks) {
        if (blockChangeBudget <= 0 || activeRollbacks <= 0) {
            return 0;
        }
        return Math.max(1, blockChangeBudget / 4);
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
            persistJournal(level, session);
            finish(level, session, GameplayEffectEndReason.EXPIRED, true);
            return remainingBlockChanges;
        }
        if (ownerUnavailable(level, session)) {
            persistJournal(level, session);
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
                    persistJournal(level, session);
                    finish(level, session, GameplayEffectEndReason.CANCELLED, true);
                    return remainingBlockChanges;
                }
            }
            session.controller.onTick(context);
            remainingBlockChanges = applyBlockChanges(session, remainingBlockChanges);
            persistJournal(level, session);
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

    private static void persistJournal(ServerLevel level, ActiveSession session) {
        persistJournal(level, session, false);
    }

    private static void persistJournal(ServerLevel level, ActiveSession session, boolean force) {
        BlockJournal journal = session.context.blockJournal();
        if (!journal.isDirty()) {
            return;
        }
        if (!force && journal.sectionCount() > 0 && level.getGameTime() % 20L != 0L) {
            return;
        }
        journal.consumeDirty();
        EffectPersistenceService.recordJournal(level, session.id, journal);
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

    private static boolean ownerUnavailable(ServerLevel level, ActiveSession session) {
        GameplayEffectSpec spec = session.spec;
        if (spec.ownerId() == null || spec.ownerPolicy() == OwnerPolicy.CONTINUE) {
            return false;
        }
        var online = level.getServer() == null
            ? null
            : level.getServer().getPlayerList().getPlayer(spec.ownerId());
        if (online != null) {
            session.ownerSeen = true;
            return online.level() != level || online.isRemoved() || !online.isAlive();
        }
        Entity owner = level.getEntity(spec.ownerId());
        if (owner != null) {
            session.ownerSeen = true;
            return owner.isRemoved() || !owner.isAlive() || owner.level() != level;
        }
        return session.ownerSeen || level.getGameTime() >= session.ownerGraceUntil;
    }

    private static void startAudio(ServerLevel level, ActiveSession session) {
        if (!session.spec.audio().isEmpty()) {
            EffectAudioPlan plan = EffectAudioPlan.from(
                session.id,
                level.dimension().location(),
                session.spec.anchor(),
                session.startGameTime,
                session.spec.phases().totalDurationTicks(),
                session.spec.audio()
            );
            EffectAudioService.start(level, plan, EffectAudience.dimensionWide());
        }
        session.context.markAudioStarted();
        session.context.flushAudioTriggers();
    }

    private static void finish(
        ServerLevel level,
        ActiveSession session,
        GameplayEffectEndReason reason,
        boolean persistRemoval
    ) {
        GameplayState state = STATE.peek(level);
        Map<UUID, ActiveSession> sessions = state == null ? null : state.sessions;
        if (sessions == null || sessions.remove(session.id) == null) {
            return;
        }

        BlockJournal journal = session.context.blockJournal();
        boolean wantsRollback = persistRemoval && shouldRollback(reason);
        boolean rollback = wantsRollback && journal.canRollback();
        if (wantsRollback && journal.isTruncated()) {
            LOGGER.warn(
                "Block journal for {} was invalid or truncated; leaving its changes permanent",
                session.id
            );
        }
        if (rollback) {
            journal.beginRollback();
            EffectPersistenceService.recordJournal(level, session.id, journal);
        } else if (!persistRemoval) {
            persistJournal(level, session, true);
        }

        try {
            session.controller.onEnd(session.context, reason);
        } catch (RuntimeException exception) {
            LOGGER.error("Gameplay effect {} failed in onEnd", session.spec.typeId(), exception);
        } finally {
            session.context.close();
            session.scope.close(persistRemoval);
            EffectAudioService.stop(level, session.id);
            OwnerAdmission admission = state.ownerAdmission;
            if (admission != null) {
                admission.recordEnd(session.spec.ownerId());
            }
        }

        if (rollback) {
            try {
                BlockRollbackService.schedule(level, session.id, journal);
            } catch (RuntimeException exception) {
                LOGGER.error("Could not schedule block rollback for {}", session.id, exception);
            }
        } else if (persistRemoval) {
            EffectPersistenceService.recordStop(level, session.id);
        }
    }

    private static boolean shouldRollback(GameplayEffectEndReason reason) {
        return reason == GameplayEffectEndReason.CANCELLED
            || reason == GameplayEffectEndReason.FAILED
            || reason == GameplayEffectEndReason.OWNER_UNAVAILABLE;
    }

    static void overrideBudgetForTesting(int sessionBudget, int perOwnerBudget, int startsPerTick) {
        if (sessionBudget < 1 || perOwnerBudget < 1 || startsPerTick < 1) {
            throw new IllegalArgumentException("Gameplay budgets must be positive");
        }
        sessionBudgetOverride = sessionBudget;
        perOwnerBudgetOverride = perOwnerBudget;
        startRateOverride = startsPerTick;
    }

    static void resetBudgetForTesting() {
        sessionBudgetOverride = null;
        perOwnerBudgetOverride = null;
        startRateOverride = null;
    }

    private static int sessionBudget() {
        return sessionBudgetOverride == null ? EffectBudgets.gameplaySessions() : sessionBudgetOverride;
    }

    private static int perOwnerBudget() {
        return perOwnerBudgetOverride == null
            ? EffectBudgets.gameplaySessionsPerOwner()
            : perOwnerBudgetOverride;
    }

    private static int startsPerTick() {
        return startRateOverride == null
            ? EffectBudgets.gameplayStartsPerOwnerPerTick()
            : startRateOverride;
    }

    /** Queues a start that could not run immediately; returns false when the queue dropped it. */
    private static boolean enqueue(ServerLevel level, PendingStart pending) {
        List<PendingStart> queue = state(level).pendingStarts;
        queue.add(pending);
        while (queue.size() > EffectBudgets.gameplayPending()) {
            PendingStart victim = lowestPriority(queue);
            if (victim == null) {
                break;
            }
            queue.remove(victim);
            EffectBudgetService.recordCull(level);
            LOGGER.warn(
                "Dropped queued gameplay start {}: the pending queue budget of {} is full",
                victim.spec.typeId(),
                EffectBudgets.gameplayPending()
            );
            if (victim == pending) {
                return false;
            }
        }
        return queue.contains(pending);
    }

    private static PendingStart lowestPriority(List<PendingStart> queue) {
        PendingStart victim = null;
        int lowest = Integer.MAX_VALUE;
        for (PendingStart pending : queue) {
            int weight = pending.spec.priority().weight();
            if (weight < lowest) {
                lowest = weight;
                victim = pending;
            }
        }
        return victim;
    }

    /** Admits queued starts in priority order once capacity frees up. */
    private static void drainPending(ServerLevel level) {
        GameplayState state = STATE.peek(level);
        if (state == null || state.pendingStarts.isEmpty()) {
            return;
        }
        List<PendingStart> queue = state.pendingStarts;
        List<PendingStart> snapshot = new ArrayList<>(queue);
        snapshot.sort(
            Comparator.comparingInt((PendingStart start) -> start.spec.priority().weight())
                .reversed()
                .thenComparingLong(start -> start.requestedTick)
        );
        for (PendingStart pending : snapshot) {
            if (!queue.contains(pending)) {
                continue;
            }
            if (pending.restored) {
                long age = Math.max(0L, level.getGameTime() - pending.startGameTime);
                if (age >= pending.spec.phases().totalDurationTicks()) {
                    queue.remove(pending);
                    EffectPersistenceService.recordStop(level, pending.id);
                    continue;
                }
            }
            try {
                boolean admitted = pending.restored
                    ? tryRestoreNow(level, pending)
                    : tryStartFresh(level, pending);
                if (admitted) {
                    queue.remove(pending);
                }
            } catch (RuntimeException exception) {
                queue.remove(pending);
                LOGGER.error("Queued gameplay effect {} failed to start", pending.spec.typeId(), exception);
            }
        }
    }

    private static boolean removePending(ServerLevel level, UUID id, boolean persistRemoval) {
        GameplayState state = STATE.peek(level);
        if (state == null) {
            return false;
        }
        List<PendingStart> queue = state.pendingStarts;
        for (int index = 0; index < queue.size(); index++) {
            PendingStart pending = queue.get(index);
            if (!pending.id.equals(id)) {
                continue;
            }
            queue.remove(index);
            if (persistRemoval && pending.restored) {
                EffectPersistenceService.recordStop(level, pending.id);
            }
            return true;
        }
        return false;
    }

    private static void clearPending(ServerLevel level, GameplayState state, boolean persistRemoval) {
        List<PendingStart> queue = state.pendingStarts;
        if (queue.isEmpty()) {
            return;
        }
        List<PendingStart> cleared = new ArrayList<>(queue);
        queue.clear();
        if (!persistRemoval) {
            return;
        }
        for (PendingStart pending : cleared) {
            if (pending.restored) {
                EffectPersistenceService.recordStop(level, pending.id);
            }
        }
    }

    private static GameplayState state(ServerLevel level) {
        return STATE.get(level);
    }

    private static void cleanupIfEmpty(ServerLevel level, GameplayState state) {
        if (state != null && state.sessions.isEmpty() && state.pendingStarts.isEmpty()) {
            STATE.remove(level);
        }
    }

    private static OwnerAdmission ownerAdmission(ServerLevel level) {
        GameplayState state = state(level);
        if (state.ownerAdmission == null) {
            state.ownerAdmission = new OwnerAdmission(perOwnerBudget(), startsPerTick());
        }
        return state.ownerAdmission;
    }

    private static final class GameplayState {
        private final Map<UUID, ActiveSession> sessions = new LinkedHashMap<>();
        private final List<PendingStart> pendingStarts = new ArrayList<>();
        private OwnerAdmission ownerAdmission;
    }

    private static final class PendingStart {
        private final UUID id;
        private final GameplayEffectSpec spec;
        private final GameplayEffectController controller;
        private final EffectPersistenceOptions options;
        private final boolean restored;
        private final EffectDescriptor descriptor;
        private final BlockJournal journal;
        private final long startGameTime;
        private final long requestedTick;

        private PendingStart(
            UUID id,
            GameplayEffectSpec spec,
            GameplayEffectController controller,
            EffectPersistenceOptions options,
            boolean restored,
            EffectDescriptor descriptor,
            BlockJournal journal,
            long startGameTime,
            long requestedTick
        ) {
            this.id = id;
            this.spec = spec;
            this.controller = controller;
            this.options = options;
            this.restored = restored;
            this.descriptor = descriptor;
            this.journal = journal;
            this.startGameTime = startGameTime;
            this.requestedTick = requestedTick;
        }

        private static PendingStart fresh(
            UUID id,
            GameplayEffectSpec spec,
            GameplayEffectController controller,
            EffectPersistenceOptions options,
            long requestedTick
        ) {
            return new PendingStart(
                id, spec, controller, options, false, null, null, 0L, requestedTick
            );
        }

        private static PendingStart restored(
            UUID id,
            GameplayEffectSpec spec,
            GameplayEffectController controller,
            EffectDescriptor descriptor,
            BlockJournal journal,
            long startGameTime,
            long requestedTick
        ) {
            return new PendingStart(
                id, spec, controller, EffectPersistenceOptions.DEFAULT, true, descriptor, journal,
                startGameTime, requestedTick
            );
        }
    }

    private static final class ActiveSession {
        private final UUID id;
        private final GameplayEffectSpec spec;
        private final GameplayEffectController controller;
        private final EffectScope scope;
        private final GameplayEffectContext context;
        private final long startGameTime;
        private final long ownerGraceUntil;
        private boolean ownerSeen;
        private EffectDescriptor descriptor;

        private ActiveSession(
            GameplayEffectSpec spec,
            GameplayEffectController controller,
            EffectScope scope,
            GameplayEffectContext context,
            EffectDescriptor descriptor,
            long startGameTime,
            long ownerGraceUntil
        ) {
            this.id = context.sessionId();
            this.spec = spec;
            this.controller = controller;
            this.scope = scope;
            this.context = context;
            this.descriptor = descriptor;
            this.startGameTime = startGameTime;
            this.ownerGraceUntil = ownerGraceUntil;
        }
    }
}
