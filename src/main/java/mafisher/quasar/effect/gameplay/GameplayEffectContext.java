package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mafisher.quasar.effect.EffectHitResult;
import mafisher.quasar.effect.EffectHitbox;
import mafisher.quasar.effect.EffectScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Read-mostly view of one active gameplay effect session. */
public final class GameplayEffectContext {
    private final ServerLevel level;
    private final EffectScope scope;
    private final GameplayEffectSpec spec;
    private final UUID sessionId;
    private final EffectTickBudgetState budgetState;

    private int ageTicks;
    private RandomSource random;
    private boolean stopRequested;
    private boolean open = true;
    private CompoundTag persistentState;
    private boolean stateDirty;

    GameplayEffectContext(ServerLevel level, EffectScope scope, GameplayEffectSpec spec, UUID sessionId) {
        this(level, scope, spec, sessionId, 0, new CompoundTag());
    }

    GameplayEffectContext(
        ServerLevel level,
        EffectScope scope,
        GameplayEffectSpec spec,
        UUID sessionId,
        int initialAgeTicks,
        CompoundTag initialState
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
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
    }

    private void ensureOpen() {
        if (!this.open) {
            throw new IllegalStateException("The gameplay effect session is already closed");
        }
    }
}
