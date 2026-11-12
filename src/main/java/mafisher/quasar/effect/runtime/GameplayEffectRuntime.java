package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.gameplay.GameplayEffectAccess;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level gameplay session registry. Sessions are resumable through their descriptors; level
 * unload and server shutdown close them without deleting persisted state.
 */
@ApiStatus.Internal
public final class GameplayEffectRuntime {
    /**
     * How long a restored session may wait for its owner to become available before the normal
     * {@link OwnerPolicy#STOP_WHEN_UNAVAILABLE} path cancels it. Dedicated servers restore effects
     * before players log in, so an immediate owner check would roll every player-owned session back.
     */
    public static final int RESTORED_OWNER_GRACE_TICKS = 6_000;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final ServerLevel level;
    private final EffectStore store;
    private final EffectBudgetSource budgets;
    private final TerrainEditRuntime terrain;
    private final BlockRollbackRuntime rollbacks;
    private final VisualEffectRuntime visuals;
    private final EffectAudioRuntime audio;
    private final Map<UUID, ActiveSession> sessions = new LinkedHashMap<>();
    private final List<PendingStart> pendingStarts = new ArrayList<>();
    private OwnerAdmission ownerAdmission;

    public GameplayEffectRuntime(
        ServerLevel level,
        EffectStore store,
        EffectBudgetSource budgets,
        TerrainEditRuntime terrain,
        BlockRollbackRuntime rollbacks,
        VisualEffectRuntime visuals,
        EffectAudioRuntime audio
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.store = Objects.requireNonNull(store, "store");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.rollbacks = Objects.requireNonNull(rollbacks, "rollbacks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.audio = Objects.requireNonNull(audio, "audio");
    }

    /**
     * Starts a session and calls {@link GameplayEffectController#onStart} before registering it.
     * A failed start closes the scope and leaves no active session behind.
     */
    public GameplayEffectHandle start(
        GameplayEffectSpec spec,
        GameplayEffectController controller
    ) {
        return start(spec, controller, EffectPersistenceOptions.DEFAULT);
    }

    /**
     * Starts a session and calls {@link GameplayEffectController#onStart} before registering it.
     * A failed start closes the scope and leaves no active session behind.
     */
    public GameplayEffectHandle start(
        GameplayEffectSpec spec,
        GameplayEffectController controller,
        EffectPersistenceOptions options
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(options, "options");
        if (this.level.isClientSide()) {
            throw new IllegalStateException("Gameplay effects are server-authoritative");
        }
        if (!GameplayEffectAccess.destructivePolicyAllows(this.level, spec, options.capabilities())) {
            LOGGER.warn(
                "Rejected destructive gameplay effect {} in {} for owner {}: server policy denied",
                spec.typeId(),
                this.level.dimension().location(),
                spec.ownerId()
            );
            throw new IllegalStateException("Destructive gameplay effects are disabled by server policy");
        }

        PendingStart pending = PendingStart.fresh(
            UUID.randomUUID(), spec, controller, options, this.level.getGameTime()
        );
        if (tryStartFresh(pending)) {
            return new GameplayEffectHandle(pending.id);
        }
        if (enqueue(pending)) {
            EffectBudgetService.recordDeferral(this.level);
            LOGGER.info(
                "Deferred gameplay effect {} in {} for owner {}: capacity is full",
                spec.typeId(),
                this.level.dimension().location(),
                spec.ownerId()
            );
        }
        return new GameplayEffectHandle(pending.id);
    }

    /**
     * Attempts an immediate start. Returns {@code false} without callbacks when the level or
     * owner budget is full, so the caller can queue the start instead.
     */
    private boolean tryStartFresh(PendingStart pending) {
        if (this.sessions.size() >= sessionBudget()) {
            return false;
        }
        OwnerAdmission admission = ownerAdmission();
        if (admission.tryStart(pending.spec.ownerId(), this.level.getGameTime())
            != OwnerAdmission.Result.ACCEPTED) {
            return false;
        }

        long startGameTime = this.level.getGameTime();
        EffectScope scope = null;
        GameplayEffectContext context = null;
        BlockJournal journal = new BlockJournal();
        try {
            scope = new EffectScope(this.level, pending.id);
            context = GameplayEffectAccess.create(
                this.level, scope, pending.spec, pending.id, pending.options.capabilities(), journal
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
            throw exception;
        }

        EffectDescriptor descriptor = descriptor(pending.spec, pending.id, startGameTime, pending.options);
        ActiveSession session = new ActiveSession(
            pending.spec, pending.controller, scope, context, descriptor, startGameTime, 0L
        );
        this.sessions.put(session.id, session);
        this.store.recordStart(descriptor);
        if (pending.options.capabilities().destructive()) {
            this.store.recordJournal(session.id, journal);
        }
        startAudio(session);
        if (GameplayEffectAccess.isStopRequested(context)) {
            finish(session, GameplayEffectEndReason.CANCELLED, EffectRemovalPolicy.DELETE);
        }
        return true;
    }

    private EffectDescriptor descriptor(
        GameplayEffectSpec spec,
        UUID sessionId,
        long startGameTime,
        EffectPersistenceOptions options
    ) {
        return EffectDescriptor.builder(
                sessionId, EffectKind.GAMEPLAY, spec.typeId(), this.level.dimension().location()
            )
            .placement(
                new EffectTransform(
                    new EffectPoint(spec.anchor().x, spec.anchor().y, spec.anchor().z),
                    0.0F,
                    0.0F,
                    0.0F
                ),
                spec.scale()
            )
            .timing(spec.seed(), startGameTime, spec.phases().totalDurationTicks())
            .audience(EffectAudience.dimensionWide())
            .capabilities(options.capabilities())
            .persistence(options.persistence())
            .state(GameplayEffectNbt.saveDescriptorState(spec, options.state()))
            .build();
    }

    /**
     * Restores one gameplay session with its original identity, start time, and consumer state.
     * The controller factory must be registered in {@code GameplayEffectTypeRegistry}.
     * Restoring a session that is already active or pending keeps the existing session.
     */
    public Optional<GameplayEffectHandle> restore(
        GameplayEffectSpec spec,
        UUID sessionId,
        long startGameTime,
        EffectDescriptor descriptor,
        GameplayEffectController controller
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(controller, "controller");
        if (this.level.isClientSide()) {
            throw new IllegalStateException("Gameplay effects are server-authoritative");
        }
        GameplayEffectHandle handle = new GameplayEffectHandle(sessionId);
        if (isActive(handle) || isPending(handle)) {
            LOGGER.warn(
                "Skipped duplicate restore of gameplay effect {} in {}: the session is already known",
                spec.typeId(),
                this.level.dimension().location()
            );
            return Optional.of(handle);
        }

        BlockJournal journal = this.store.journalOrNull(sessionId);
        if (journal == null) {
            journal = new BlockJournal();
        }
        if (journal.isRollbackPending()) {
            this.rollbacks.schedule(sessionId, journal);
            return Optional.empty();
        }

        int age = EffectTimeline.ageTicks(this.level.getGameTime(), startGameTime);
        if (age >= spec.phases().totalDurationTicks()) {
            this.store.recordStop(sessionId);
            return Optional.empty();
        }
        if (!GameplayEffectAccess.destructivePolicyAllows(this.level, spec, descriptor.capabilities())) {
            LOGGER.info(
                "Deferred destructive gameplay effect {} in {}: server policy denied",
                spec.typeId(),
                this.level.dimension().location()
            );
            return Optional.empty();
        }

        PendingStart pending = PendingStart.restored(
            sessionId, spec, controller, descriptor, journal, startGameTime, this.level.getGameTime()
        );
        if (!tryRestoreNow(pending)) {
            if (enqueue(pending)) {
                EffectBudgetService.recordDeferral(this.level);
                LOGGER.info(
                    "Deferred restore of gameplay effect {} until its level or owner has capacity",
                    spec.typeId()
                );
            }
        }
        return Optional.of(new GameplayEffectHandle(sessionId));
    }

    /** Attempts to resume a persisted session immediately; returns {@code false} when capacity is full. */
    private boolean tryRestoreNow(PendingStart pending) {
        if (skipAlreadyActive(pending)) {
            return true;
        }
        if (this.sessions.size() >= sessionBudget()) {
            return false;
        }
        OwnerAdmission admission = ownerAdmission();
        if (pending.spec.ownerId() != null
            && admission.activeSessions(pending.spec.ownerId()) >= perOwnerBudget()) {
            return false;
        }

        int age = EffectTimeline.ageTicks(this.level.getGameTime(), pending.startGameTime);
        if (age >= pending.spec.phases().totalDurationTicks()) {
            this.store.recordStop(pending.id);
            return true;
        }
        EffectScope scope = new EffectScope(this.level, pending.id);
        GameplayEffectContext context = GameplayEffectAccess.restore(
            this.level,
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
            scope.close(EffectRemovalPolicy.KEEP_FOR_RESTORE);
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
            this.level.getGameTime() + RESTORED_OWNER_GRACE_TICKS
        );
        this.sessions.put(session.id, session);
        startAudio(session);
        this.visuals.adoptOwnedBy(session.id, scope);
        if (GameplayEffectAccess.isStopRequested(context)) {
            finish(session, GameplayEffectEndReason.CANCELLED, EffectRemovalPolicy.DELETE);
        }
        return true;
    }

    private boolean skipAlreadyActive(PendingStart pending) {
        if (!this.sessions.containsKey(pending.id)) {
            return false;
        }
        LOGGER.warn(
            "Skipped duplicate restore of gameplay effect {} in {}: session {} is already active",
            pending.spec.typeId(),
            this.level.dimension().location(),
            pending.id
        );
        return true;
    }

    /** Scope of one active session, used to reattach persisted child visuals after restore. */
    public EffectScope scope(GameplayEffectHandle handle) {
        Objects.requireNonNull(handle, "handle");
        ActiveSession session = this.sessions.get(handle.sessionId());
        return session == null ? null : session.scope;
    }

    /**
     * Stops one session or cancels one queued start; unknown handles are no-ops and repeated stops
     * are safe.
     */
    public void stop(GameplayEffectHandle handle, GameplayEffectEndReason reason) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(reason, "reason");
        if (removePending(handle.sessionId(), EffectRemovalPolicy.DELETE)) {
            return;
        }
        ActiveSession session = this.sessions.get(handle.sessionId());
        if (session != null) {
            finish(session, reason, EffectRemovalPolicy.DELETE);
        }
    }

    /** Stops every session in one level and returns how many were active. */
    public int stopAll(GameplayEffectEndReason reason) {
        return stopAll(reason, EffectRemovalPolicy.DELETE);
    }

    /**
     * Stops every session in one level. Level unload and shutdown pass
     * {@link EffectRemovalPolicy#KEEP_FOR_RESTORE} so descriptors stay available for restore; an
     * explicit stop passes {@link EffectRemovalPolicy#DELETE}.
     */
    public int stopAll(GameplayEffectEndReason reason, EffectRemovalPolicy policy) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(policy, "policy");
        clearPending(policy);
        int count = this.sessions.size();
        for (ActiveSession session : new ArrayList<>(this.sessions.values())) {
            finish(session, reason, policy);
        }
        this.sessions.clear();
        this.pendingStarts.clear();
        this.ownerAdmission = null;
        return count;
    }

    public int activeCount() {
        return this.sessions.size();
    }

    /** Number of gameplay starts waiting for capacity in one level. */
    public int pendingCount() {
        return this.pendingStarts.size();
    }

    /** Whether one gameplay session is currently active. */
    public boolean isActive(GameplayEffectHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return this.sessions.containsKey(handle.sessionId());
    }

    /** Whether one gameplay start is queued and will run when capacity becomes available. */
    public boolean isPending(GameplayEffectHandle handle) {
        Objects.requireNonNull(handle, "handle");
        for (PendingStart start : this.pendingStarts) {
            if (start.id.equals(handle.sessionId())) {
                return true;
            }
        }
        return false;
    }

    /** Advances every active session in one level by one server tick. */
    public void tick() {
        tick(this.budgets.blockChangesPerLevelTick());
    }

    /** Advances every active session with an explicit level-wide block application budget. */
    public void tick(int blockChangeBudget) {
        if (blockChangeBudget < 0) {
            throw new IllegalArgumentException("Block change application budget cannot be negative");
        }
        this.terrain.beginTick();
        int rollbackReserve = rollbackReserve(blockChangeBudget, this.rollbacks.activeCount());
        int remainingBlockChanges = blockChangeBudget - rollbackReserve;
        if (!this.sessions.isEmpty()) {
            for (ActiveSession session : new ArrayList<>(this.sessions.values())) {
                if (this.sessions.containsKey(session.id)) {
                    remainingBlockChanges = tickSession(session, remainingBlockChanges);
                }
            }
        }
        this.rollbacks.tick(remainingBlockChanges + rollbackReserve);
        this.terrain.tick();
        drainPending();
    }

    /**
     * Reserves a small share of the block budget for pending rollbacks so live sessions cannot
     * starve them forever when they consume the whole level tick allowance.
     */
    public static int rollbackReserve(int blockChangeBudget, int activeRollbacks) {
        if (blockChangeBudget <= 0 || activeRollbacks <= 0) {
            return 0;
        }
        return Math.max(1, blockChangeBudget / 4);
    }

    private int tickSession(ActiveSession session, int remainingBlockChanges) {
        GameplayEffectContext context = session.context;
        int nextAge = context.ageTicks() + 1;
        if (nextAge >= session.spec.phases().totalDurationTicks()) {
            GameplayEffectAccess.endAt(context, session.spec.phases().totalDurationTicks());
            try {
                remainingBlockChanges = applyBlockChanges(session, remainingBlockChanges);
            } catch (RuntimeException exception) {
                LOGGER.error(
                    "Gameplay effect {} failed to apply block changes on expiry",
                    session.spec.typeId(),
                    exception
                );
            }
            persistJournal(session);
            finish(session, GameplayEffectEndReason.EXPIRED, EffectRemovalPolicy.DELETE);
            return remainingBlockChanges;
        }
        if (ownerUnavailable(session)) {
            persistJournal(session);
            finish(session, GameplayEffectEndReason.OWNER_UNAVAILABLE, EffectRemovalPolicy.DELETE);
            return remainingBlockChanges;
        }

        int previousPhase = context.phaseIndex();
        context.beginTick(nextAge);
        try {
            int phaseIndex = context.phaseIndex();
            if (phaseIndex != previousPhase) {
                session.controller.onPhaseEnter(context, phaseIndex);
                if (GameplayEffectAccess.isStopRequested(context)) {
                    remainingBlockChanges = applyBlockChanges(session, remainingBlockChanges);
                    persistJournal(session);
                    finish(session, GameplayEffectEndReason.CANCELLED, EffectRemovalPolicy.DELETE);
                    return remainingBlockChanges;
                }
            }
            session.controller.onTick(context);
            remainingBlockChanges = applyBlockChanges(session, remainingBlockChanges);
            persistJournal(session);
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Gameplay effect {} failed on tick {}",
                session.spec.typeId(),
                nextAge,
                exception
            );
            finish(session, GameplayEffectEndReason.FAILED, EffectRemovalPolicy.DELETE);
            return remainingBlockChanges;
        }

        if (GameplayEffectAccess.consumeStateDirty(context)) {
            session.descriptor = session.descriptor.withState(
                GameplayEffectNbt.saveDescriptorState(
                    session.spec, GameplayEffectAccess.rawPersistentState(context)
                )
            );
            this.store.recordUpdate(session.descriptor);
        }

        if (GameplayEffectAccess.isStopRequested(context)) {
            finish(session, GameplayEffectEndReason.CANCELLED, EffectRemovalPolicy.DELETE);
        }
        return remainingBlockChanges;
    }

    private int applyBlockChanges(ActiveSession session, int remainingBlockChanges) {
        int perSession = Math.min(session.spec.budget().maxBlockChanges(), remainingBlockChanges);
        if (perSession <= 0) {
            return remainingBlockChanges;
        }
        return remainingBlockChanges - GameplayEffectAccess.applyBlockChanges(session.context, perSession);
    }

    private void persistJournal(ActiveSession session) {
        persistJournal(session, false);
    }

    private void persistJournal(ActiveSession session, boolean force) {
        BlockJournal journal = GameplayEffectAccess.blockJournal(session.context);
        if (!journal.isDirty()) {
            return;
        }
        if (!force && journal.sectionCount() > 0 && this.level.getGameTime() % 20L != 0L) {
            return;
        }
        if (session.descriptor.persistence() != EffectPersistence.RESUMABLE) {
            journal.consumeDirty();
            return;
        }
        if (this.store.recordJournal(session.id, journal)) {
            journal.consumeDirty();
            session.journalPersistenceFailed = false;
        } else if (!session.journalPersistenceFailed) {
            session.journalPersistenceFailed = true;
            LOGGER.warn(
                "Block journal for {} was not persisted; it stays dirty and will retry",
                session.id
            );
        }
    }

    private boolean ownerUnavailable(ActiveSession session) {
        GameplayEffectSpec spec = session.spec;
        if (spec.ownerId() == null || spec.ownerPolicy() == OwnerPolicy.CONTINUE) {
            return false;
        }
        var online = this.level.getServer() == null
            ? null
            : this.level.getServer().getPlayerList().getPlayer(spec.ownerId());
        if (online != null) {
            session.ownerSeen = true;
            return online.level() != this.level || online.isRemoved() || !online.isAlive();
        }
        Entity owner = this.level.getEntity(spec.ownerId());
        if (owner != null) {
            session.ownerSeen = true;
            return owner.isRemoved() || !owner.isAlive() || owner.level() != this.level;
        }
        return session.ownerSeen || this.level.getGameTime() >= session.ownerGraceUntil;
    }

    private void startAudio(ActiveSession session) {
        if (!session.spec.audio().isEmpty()) {
            EffectAudioPlan plan = EffectAudioPlan.from(
                session.id,
                this.level.dimension().location(),
                session.spec.anchor(),
                session.startGameTime,
                session.spec.phases().totalDurationTicks(),
                session.spec.audio()
            );
            this.audio.start(plan, EffectAudience.dimensionWide(), session.spec.priority());
        }
        GameplayEffectAccess.markAudioStarted(session.context);
        GameplayEffectAccess.flushAudioTriggers(session.context);
    }

    private void finish(
        ActiveSession session,
        GameplayEffectEndReason reason,
        EffectRemovalPolicy policy
    ) {
        if (this.sessions.remove(session.id) == null) {
            return;
        }

        BlockJournal journal = GameplayEffectAccess.blockJournal(session.context);
        boolean wantsRollback = policy.deletesDescriptor() && shouldRollback(reason);
        boolean rollback = wantsRollback && journal.canRollback();
        if (wantsRollback && journal.isTruncated()) {
            LOGGER.warn(
                "Block journal for {} was invalid or truncated; leaving its changes permanent",
                session.id
            );
        }
        if (rollback) {
            journal.beginRollback();
            this.store.recordJournal(session.id, journal);
        } else if (!policy.deletesDescriptor()) {
            persistJournal(session, true);
        }

        try {
            session.controller.onEnd(session.context, reason);
        } catch (RuntimeException exception) {
            LOGGER.error("Gameplay effect {} failed in onEnd", session.spec.typeId(), exception);
        } finally {
            session.context.close();
            session.scope.close(policy);
            this.audio.stop(session.id);
            if (this.ownerAdmission != null) {
                this.ownerAdmission.recordEnd(session.spec.ownerId());
            }
        }

        if (rollback) {
            try {
                this.rollbacks.schedule(session.id, journal);
            } catch (RuntimeException exception) {
                LOGGER.error("Could not schedule block rollback for {}", session.id, exception);
            }
        } else if (policy.deletesDescriptor()) {
            this.store.recordStop(session.id);
        }
    }

    private static boolean shouldRollback(GameplayEffectEndReason reason) {
        return reason == GameplayEffectEndReason.CANCELLED
            || reason == GameplayEffectEndReason.FAILED
            || reason == GameplayEffectEndReason.OWNER_UNAVAILABLE;
    }

    private int sessionBudget() {
        return this.budgets.gameplaySessions();
    }

    private int perOwnerBudget() {
        return this.budgets.gameplaySessionsPerOwner();
    }

    private int startsPerTick() {
        return this.budgets.gameplayStartsPerOwnerPerTick();
    }

    /** Queues a start that could not run immediately; returns false when the queue dropped it. */
    private boolean enqueue(PendingStart pending) {
        this.pendingStarts.add(pending);
        while (this.pendingStarts.size() > this.budgets.gameplayPending()) {
            PendingStart victim = EffectBudgetService.lowestPriority(
                this.pendingStarts, queued -> queued.spec.priority().weight()
            );
            if (victim == null) {
                break;
            }
            this.pendingStarts.remove(victim);
            EffectBudgetService.recordCull(this.level);
            LOGGER.warn(
                "Dropped queued gameplay start {}: the pending queue budget of {} is full",
                victim.spec.typeId(),
                this.budgets.gameplayPending()
            );
            if (victim == pending) {
                return false;
            }
        }
        return this.pendingStarts.contains(pending);
    }

    /** Admits queued starts in priority order once capacity frees up. */
    private void drainPending() {
        if (this.pendingStarts.isEmpty()) {
            return;
        }
        List<PendingStart> snapshot = new ArrayList<>(this.pendingStarts);
        snapshot.sort(
            Comparator.comparingInt((PendingStart start) -> start.spec.priority().weight())
                .reversed()
                .thenComparingLong(start -> start.requestedTick)
        );
        for (PendingStart pending : snapshot) {
            if (!this.pendingStarts.contains(pending)) {
                continue;
            }
            if (pending.restored) {
                int age = EffectTimeline.ageTicks(this.level.getGameTime(), pending.startGameTime);
                if (age >= pending.spec.phases().totalDurationTicks()) {
                    this.pendingStarts.remove(pending);
                    this.store.recordStop(pending.id);
                    continue;
                }
            }
            try {
                boolean admitted = pending.restored ? tryRestoreNow(pending) : tryStartFresh(pending);
                if (admitted) {
                    this.pendingStarts.remove(pending);
                }
            } catch (RuntimeException exception) {
                this.pendingStarts.remove(pending);
                LOGGER.error("Queued gameplay effect {} failed to start", pending.spec.typeId(), exception);
            }
        }
    }

    private boolean removePending(UUID id, EffectRemovalPolicy policy) {
        for (int index = 0; index < this.pendingStarts.size(); index++) {
            PendingStart pending = this.pendingStarts.get(index);
            if (!pending.id.equals(id)) {
                continue;
            }
            this.pendingStarts.remove(index);
            if (policy.deletesDescriptor() && pending.restored) {
                this.store.recordStop(pending.id);
            }
            return true;
        }
        return false;
    }

    private void clearPending(EffectRemovalPolicy policy) {
        if (this.pendingStarts.isEmpty()) {
            return;
        }
        List<PendingStart> cleared = new ArrayList<>(this.pendingStarts);
        this.pendingStarts.clear();
        if (!policy.deletesDescriptor()) {
            return;
        }
        for (PendingStart pending : cleared) {
            if (pending.restored) {
                this.store.recordStop(pending.id);
            }
        }
    }

    private OwnerAdmission ownerAdmission() {
        if (this.ownerAdmission == null) {
            this.ownerAdmission = new OwnerAdmission(perOwnerBudget(), startsPerTick());
        }
        return this.ownerAdmission;
    }

    /** Drops transient session state without running controller callbacks or deleting descriptors. */
    public void clear() {
        for (ActiveSession session : this.sessions.values()) {
            session.context.close();
            session.scope.close(EffectRemovalPolicy.KEEP_FOR_RESTORE);
        }
        this.sessions.clear();
        this.pendingStarts.clear();
        this.ownerAdmission = null;
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
        private boolean journalPersistenceFailed;
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
