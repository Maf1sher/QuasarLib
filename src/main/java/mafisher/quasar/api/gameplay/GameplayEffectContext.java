package mafisher.quasar.api.gameplay;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.block.BlockProtectionContext;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.effect.EffectDebrisSpec;
import mafisher.quasar.api.effect.EffectForceField;
import mafisher.quasar.api.effect.EffectForceResult;
import mafisher.quasar.api.effect.EffectHitResult;
import mafisher.quasar.api.effect.EffectHitbox;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.block.BlockProtectionService;
import mafisher.quasar.effect.gameplay.TerrainEditService;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-mostly view of one active gameplay effect session. */
public final class GameplayEffectContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameplayEffectContext.class);
    private final ServerLevel level;
    private final EffectScope scope;
    private final GameplayEffectSpec spec;
    private final UUID sessionId;
    private final EffectCapabilities capabilities;
    private final BlockJournal blockJournal;
    private final EffectTickBudgetState budgetState;
    private final PendingBlockChanges blockChanges = new PendingBlockChanges();
    private final PendingAudioTriggers pendingAudioTriggers = new PendingAudioTriggers();
    private final EffectCloseActions closeActions = new EffectCloseActions();

    private boolean audioStarted;
    private int ageTicks;
    private RandomSource random;
    private boolean stopRequested;
    private boolean open = true;
    private CompoundTag persistentState;
    private boolean stateDirty;
    private EffectWorldQuery world;

    GameplayEffectContext(
        ServerLevel level,
        EffectScope scope,
        GameplayEffectSpec spec,
        UUID sessionId,
        EffectCapabilities capabilities,
        BlockJournal blockJournal
    ) {
        this(level, scope, spec, sessionId, capabilities, blockJournal, 0, new CompoundTag());
    }

    GameplayEffectContext(
        ServerLevel level,
        EffectScope scope,
        GameplayEffectSpec spec,
        UUID sessionId,
        EffectCapabilities capabilities,
        BlockJournal blockJournal,
        int initialAgeTicks,
        CompoundTag initialState
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.blockJournal = Objects.requireNonNull(blockJournal, "blockJournal");
        this.persistentState = Objects.requireNonNull(initialState, "initialState").copy();
        this.budgetState = new EffectTickBudgetState(spec.budget());
        this.beginTick(initialAgeTicks);
    }

    public ServerLevel level() {
        return this.level;
    }

    /** The session scope; the service owns closing it. */
    public EffectScope scope() {
        return this.scope;
    }

    public GameplayEffectSpec spec() {
        return this.spec;
    }

    public UUID sessionId() {
        return this.sessionId;
    }

    /** Capabilities declared for this session; block changes require the destructive capability. */
    public EffectCapabilities capabilities() {
        return this.capabilities;
    }

    public int ageTicks() {
        return this.ageTicks;
    }

    /** Returns the current phase index, or -1 when the plan is finished. */
    public int phaseIndex() {
        return this.spec.phases().phaseIndexAt(this.ageTicks);
    }

    public int phaseAgeTicks() {
        return this.spec.phases().phaseAgeTicks(this.ageTicks);
    }

    /**
     * Budgeted world sampling for this session. Queries never load chunks; see
     * {@link EffectWorldQuery}. The configured per-tick limit is
     * {@link EffectTickBudget#maxWorldSamples()}.
     */
    public EffectWorldQuery world() {
        this.ensureOpen();
        if (this.world == null) {
            this.world = new EffectWorldQuery(this.level, this.budgetState, this::ensureOpen);
        }
        return this.world;
    }

    public float phaseProgress() {
        return this.spec.phases().phaseProgress(this.ageTicks);
    }

    /** Fires one audio trigger declared on the spec at its authored position. */
    public boolean triggerAudio(String triggerId) {
        return this.triggerAudio(triggerId, null);
    }

    /**
     * Fires one audio trigger, optionally at a runtime event position. Calls made during
     * {@code onStart}, {@code onPhaseEnter(0)}, or {@code onResume} are buffered until the
     * session's audio plan starts; later calls send immediately.
     *
     * @return {@code false} when the spec does not declare the trigger or the plan is gone
     */
    public boolean triggerAudio(String triggerId, Vec3 position) {
        this.ensureOpen();
        Objects.requireNonNull(triggerId, "triggerId");
        if (this.spec.audio().triggerOrNull(triggerId) == null) {
            return false;
        }
        if (position != null
            && (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))) {
            throw new IllegalArgumentException("Effect audio trigger position must be finite");
        }
        if (this.audioStarted) {
            return EffectAudioService.trigger(this.level, this.sessionId, triggerId, position);
        }
        this.pendingAudioTriggers.add(triggerId, position);
        return true;
    }

    /** Returns the deterministic source for the current tick; call again after a tick boundary. */
    public RandomSource random() {
        this.ensureOpen();
        return this.random;
    }

    /** Resolves the optional owner entity, or null when absent or unavailable. */
    @Nullable
    public Entity owner() {
        this.ensureOpen();
        return this.spec.ownerId() == null ? null : this.level.getEntity(this.spec.ownerId());
    }

    /** Applies one hitbox within this tick's target and call budgets. */
    public EffectHitResult applyHitbox(
        EffectHitbox hitbox,
        DamageSource damageSource,
        Predicate<LivingEntity> targetPolicy
    ) {
        this.ensureOpen();
        Objects.requireNonNull(hitbox, "hitbox");
        Objects.requireNonNull(damageSource, "damageSource");
        Objects.requireNonNull(targetPolicy, "targetPolicy");
        if (!this.budgetState.canApplyHitbox()) {
            return EffectHitResult.NONE;
        }

        this.budgetState.recordHitboxCall();
        EffectHitResult result = hitbox.applyDetailed(
            this.level,
            this.ageTicks,
            this.owner(),
            damageSource,
            targetPolicy,
            this.budgetState.remainingTargets()
        );
        this.budgetState.recordHitResult(result);
        return result;
    }

    /**
     * Applies one force field to server entities within this tick's force-target budget. Candidates
     * are ordered by distance from the field center and then by UUID, so a cap selects the same
     * targets for the same world state. Force fields never damage targets; they only add velocity.
     * Budget exhaustion is soft: the scan stops and reports {@code truncated}.
     */
    public EffectForceResult applyForceField(
        EffectForceField field,
        Predicate<Entity> targetPolicy
    ) {
        this.ensureOpen();
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(targetPolicy, "targetPolicy");
        return EffectForceApplication.apply(this.level, field, targetPolicy, this.budgetState);
    }

    /**
     * Emits a transient client-only block-debris burst. The server sends one compact payload to
     * players in the audience; clients derive every piece from the effect seed. Overflow is
     * degraded by emitting fewer pieces and returning the accepted count.
     */
    public int spawnDebris(EffectDebrisSpec spec) {
        this.ensureOpen();
        Objects.requireNonNull(spec, "spec");
        int pieces = Math.min(spec.pieces(), this.budgetState.remainingDebrisPieces());
        if (pieces <= 0) {
            return 0;
        }
        int sent = EffectDebrisService.send(this.level, spec, pieces, this.random.nextLong());
        this.budgetState.recordDebrisPieces(sent);
        return sent;
    }

    /** Whether this session and its level may spawn more entities on the current tick. */
    public boolean canSpawnEntity() {
        this.ensureOpen();
        return this.budgetState.canSpawnEntity()
            && EffectBudgetService.entitySpawnBudgetAvailable(this.level);
    }

    /**
     * Spawns an entity through the session scope and consumes one spawn budget slot. Spawn budgets
     * are soft: when either the session budget or the level-wide per-tick budget is exhausted, the
     * spawn is dropped and counted, and this method returns {@code null} instead of throwing.
     */
    @Nullable
    public <T extends Entity> T spawnEntity(T entity) {
        this.ensureOpen();
        Objects.requireNonNull(entity, "entity");
        if (!this.budgetState.canSpawnEntity()) {
            EffectBudgetService.recordDroppedEntitySpawn(this.level);
            return null;
        }
        if (!EffectBudgetService.tryAcquireEntitySpawn(this.level)) {
            return null;
        }
        T spawned = this.scope.spawnEntity(entity);
        this.budgetState.recordEntitySpawn();
        return spawned;
    }

    /**
     * Queues one server-authoritative block change. The engine applies at most
     * {@link EffectTickBudget#maxBlockChanges()} changes per tick; overflow waits for later ticks.
     *
     * @return {@code true} when the change was queued, {@code false} when the block-change
     *     budget is zero or the pending safety cap is full
     * @throws IllegalStateException when the session is closed or the effect did not declare the
     *     destructive capability
     * @throws IllegalArgumentException when the position is outside the level
     */
    public boolean queueBlockChange(BlockPos position, BlockState state) {
        this.ensureOpen();
        return this.queueBlockChange(new BlockChange(position, state));
    }

    /** Queues one block change; see {@link #queueBlockChange(BlockPos, BlockState)}. */
    public boolean queueBlockChange(BlockChange change) {
        return this.queueBlockChange(change, null, BlockJournalMode.PER_BLOCK);
    }

    /**
     * Queues one block change with an optional callback invoked after the engine successfully
     * applies it. The callback receives the old block state and is not called for protection
     * vetoes, unchanged blocks, or failed writes. Callbacks are transient and not persisted.
     */
    public boolean queueBlockChange(BlockChange change, Consumer<EffectBlockSample> applied) {
        return this.queueBlockChange(change, applied, BlockJournalMode.PER_BLOCK);
    }

    /**
     * Queues one block change and chooses how rollback records it. {@link BlockJournalMode#SECTION}
     * snapshots the whole 16x16x16 section once, so scattered destructive edits keep a complete
     * rollback without spending one journal entry per block.
     */
    public boolean queueBlockChange(
        BlockChange change,
        Consumer<EffectBlockSample> applied,
        BlockJournalMode journalMode
    ) {
        this.ensureOpen();
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(journalMode, "journalMode");
        if (!this.capabilities.destructive()) {
            throw new IllegalStateException(
                "Gameplay effect " + this.spec.typeId() + " did not declare the destructive capability"
            );
        }
        if (this.spec.budget().maxBlockChanges() <= 0) {
            return false;
        }
        if (!this.level.isInWorldBounds(change.position())) {
            throw new IllegalArgumentException(
                "Block change position is outside the level: " + change.position()
            );
        }
        if (!this.blockChanges.enqueue(change, applied, journalMode)) {
            return false;
        }
        this.budgetState.recordBlockChangeQueued();
        return true;
    }

    /** Changes waiting for a future tick's application budget. */
    public int pendingBlockChanges() {
        return this.blockChanges.size();
    }

    /** Applies up to {@code maxChanges} queued changes; the service owns the per-tick budget. */
    int applyBlockChanges(int maxChanges) {
        this.ensureOpen();
        PendingBlockChanges.ApplyResult result = this.blockChanges.apply(
            this.level,
            maxChanges,
            this::allowsBlockChange,
            this.blockJournal
        );
        this.budgetState.recordBlockChangesApplied(result.applied());
        this.budgetState.recordBlockChangesVetoed(result.vetoed());
        return result.processed();
    }

    /** Whether the engine can restore all recorded changes for this session. */
    public boolean canRollbackChanges() {
        return this.blockJournal.canRollback();
    }

    /** The engine-managed journal for this session. */
    BlockJournal blockJournal() {
        return this.blockJournal;
    }

    /** Whether any native or compat protection must be consulted for this session. */
    boolean hasBlockProtection() {
        return BlockProtectionService.hasProtections() || CompatRegistry.hasProtectionBridge();
    }

    /** Queries the active protection bridge, including the registered native fallback. */
    boolean allowsBlockChange(BlockChange change, BlockState currentState) {
        return CompatRegistry.protection().allows(new BlockProtectionContext(
            this.level,
            change,
            currentState,
            this.spec.typeId(),
            this.sessionId,
            this.spec.ownerId()
        ));
    }

    /** Requests a clean end after the current callback; the reason is {@link GameplayEffectEndReason#CANCELLED}. */
    public void requestStop() {
        this.stopRequested = true;
    }

    public EffectTickCounters counters() {
        return this.budgetState.counters();
    }

    /** Remaining bulk section edits for this tick before the session budget is exhausted. */
    public int remainingSectionEdits() {
        return this.budgetState.remainingSectionEdits();
    }

    /**
     * Reserves one bulk section edit against both the session budget and the level-wide section
     * budget. Returns {@code false} when either soft budget is exhausted for this tick.
     */
    boolean tryAcquireSectionEdit() {
        this.ensureOpen();
        if (this.budgetState.remainingSectionEdits() <= 0) {
            return false;
        }
        if (!TerrainEditService.tryAcquireSection(this.level)) {
            return false;
        }
        return this.budgetState.tryAcquireSectionEdit();
    }

    /** Records the block changes produced by one bulk section edit. */
    void recordBulkSection(int applied, int vetoed) {
        this.budgetState.recordBlockChangesApplied(applied);
        this.budgetState.recordBlockChangesVetoed(vetoed);
    }

    /** A defensive copy of consumer state carried by the persisted descriptor. */
    public CompoundTag persistentState() {
        this.ensureOpen();
        return this.persistentState.copy();
    }

    /** Replaces consumer state and marks it for persistence at the end of the current tick. */
    public void setPersistentState(CompoundTag state) {
        this.ensureOpen();
        this.persistentState = Objects.requireNonNull(state, "state").copy();
        this.stateDirty = true;
    }

    /** Mutates a copy of consumer state and marks it for persistence at the end of the current tick. */
    public void updatePersistentState(Consumer<CompoundTag> mutator) {
        this.ensureOpen();
        Objects.requireNonNull(mutator, "mutator");
        CompoundTag updated = this.persistentState.copy();
        mutator.accept(updated);
        this.persistentState = updated;
        this.stateDirty = true;
    }

    boolean consumeStateDirty() {
        boolean dirty = this.stateDirty;
        this.stateDirty = false;
        return dirty;
    }

    CompoundTag rawPersistentState() {
        return this.persistentState.copy();
    }

    boolean isStopRequested() {
        return this.stopRequested;
    }

    void markAudioStarted() {
        this.audioStarted = true;
    }

    void flushAudioTriggers() {
        if (this.audioStarted) {
            this.pendingAudioTriggers.flush(this.level, this.sessionId);
        }
    }

    public void beginTick(int ageTicks) {
        if (ageTicks < 0) {
            throw new IllegalArgumentException("Effect age cannot be negative");
        }
        this.ageTicks = ageTicks;
        this.random = RandomSource.create(EffectSeeds.forTick(this.spec.seed(), ageTicks));
        this.budgetState.reset();
    }

    void endAt(int ageTicks) {
        if (ageTicks < 0) {
            throw new IllegalArgumentException("Effect age cannot be negative");
        }
        this.ageTicks = ageTicks;
        this.random = RandomSource.create(EffectSeeds.forTick(this.spec.seed(), ageTicks));
    }

    /** Registers engine-owned cleanup that must run even when a controller callback throws. */
    public void onClose(Runnable action) {
        this.closeActions.add(action);
    }

    public void close() {
        this.open = false;
        this.closeActions.run(this.spec.typeId().toString());
        int discarded = this.blockChanges.size();
        this.blockChanges.clear();
        this.pendingAudioTriggers.clear();
        if (discarded > 0) {
            LOGGER.warn(
                "Discarded {} pending block changes for gameplay effect {}",
                discarded,
                this.spec.typeId()
            );
        }
    }

    private void ensureOpen() {
        if (!this.open) {
            throw new IllegalStateException("The gameplay effect session is already closed");
        }
    }
}
