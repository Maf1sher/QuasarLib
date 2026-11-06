package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mafisher.quasar.effect.EffectHitResult;
import mafisher.quasar.effect.EffectHitbox;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.block.BlockProtectionContext;
import mafisher.quasar.effect.block.BlockProtectionService;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Read-mostly view of one active gameplay effect session. */
public final class GameplayEffectContext {
    private final ServerLevel level;
    private final EffectScope scope;
    private final GameplayEffectSpec spec;
    private final UUID sessionId;
    private final EffectCapabilities capabilities;
    private final BlockJournal blockJournal;
    private final EffectTickBudgetState budgetState;
    private final PendingBlockChanges blockChanges = new PendingBlockChanges();

    private int ageTicks;
    private RandomSource random;
    private boolean stopRequested;
    private boolean open = true;
    private CompoundTag persistentState;
    private boolean stateDirty;

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

    public float phaseProgress() {
        return this.spec.phases().phaseProgress(this.ageTicks);
    }

    /** Returns the deterministic source for the current tick; call again after a tick boundary. */
    public RandomSource random() {
        this.ensureOpen();
        return this.random;
    }

    /** Resolves the optional owner entity, or null when absent or unavailable. */
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

    /** Spawns an entity through the session scope and consumes one spawn budget slot. */
    public <T extends Entity> T spawnEntity(T entity) {
        this.ensureOpen();
        Objects.requireNonNull(entity, "entity");
        if (!this.budgetState.canSpawnEntity()) {
            throw new IllegalStateException("Gameplay effect entity spawn budget is exhausted");
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
        this.ensureOpen();
        Objects.requireNonNull(change, "change");
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
        if (!this.blockChanges.enqueue(change)) {
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

    /** The engine-managed journal for this session. */
    BlockJournal blockJournal() {
        return this.blockJournal;
    }

    boolean consumeJournalDirty() {
        return this.blockJournal.consumeDirty();
    }

    private boolean allowsBlockChange(BlockChange change, BlockState currentState) {
        return BlockProtectionService.allows(new BlockProtectionContext(
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

    void beginTick(int ageTicks) {
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

    void close() {
        this.open = false;
        this.blockChanges.clear();
    }

    private void ensureOpen() {
        if (!this.open) {
            throw new IllegalStateException("The gameplay effect session is already closed");
        }
    }
}
