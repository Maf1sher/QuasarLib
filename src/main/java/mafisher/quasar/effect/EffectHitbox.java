package mafisher.quasar.effect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/** Reusable server-authoritative damage or removal volume for gameplay executions. */
public final class EffectHitbox {
    private EffectDamageShape shape;
    private final float damage;
    private final int rehitDelayTicks;
    private final int maxTargetsPerTick;
    private final EffectHitboxOutcome outcome;
    private final EffectHitCooldowns hitCooldowns = new EffectHitCooldowns();

    public EffectHitbox(EffectDamageShape shape, float damage, int rehitDelayTicks, int maxTargetsPerTick) {
        this(shape, damage, rehitDelayTicks, maxTargetsPerTick, EffectHitboxOutcome.DAMAGE);
    }

    public EffectHitbox(
        EffectDamageShape shape,
        float damage,
        int rehitDelayTicks,
        int maxTargetsPerTick,
        EffectHitboxOutcome outcome
    ) {
        this.shape = Objects.requireNonNull(shape, "shape");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        if (!Float.isFinite(damage) || damage < 0.0F
            || (outcome == EffectHitboxOutcome.DAMAGE && damage <= 0.0F)) {
            throw new IllegalArgumentException("Damage must be finite and positive for damage hitboxes");
        }
        if (rehitDelayTicks < 0 || maxTargetsPerTick < 1) {
            throw new IllegalArgumentException("Invalid hitbox limits");
        }
        this.damage = damage;
        this.rehitDelayTicks = rehitDelayTicks;
        this.maxTargetsPerTick = maxTargetsPerTick;
    }

    /** A hitbox that removes non-player living targets and kills players with void damage. */
    public static EffectHitbox removal(EffectDamageShape shape, int maxTargetsPerTick) {
        return new EffectHitbox(shape, 0.0F, 0, maxTargetsPerTick, EffectHitboxOutcome.REMOVE);
    }

    /** Updates geometry while retaining this effect instance's per-target hit history. */
    public void setShape(EffectDamageShape shape) {
        this.shape = Objects.requireNonNull(shape, "shape");
    }

    /**
     * Applies damage to intersecting targets. A zero re-hit delay means once per target for this hitbox.
     * The caller supplies target policy and damage attribution explicitly.
     */
    public int apply(
        ServerLevel level,
        int effectAgeTicks,
        Entity owner,
        DamageSource damageSource,
        Predicate<LivingEntity> targetPolicy
    ) {
        return this.applyDetailed(
            level, effectAgeTicks, owner, damageSource, targetPolicy, this.maxTargetsPerTick
        ).damaged();
    }

    /**
     * Applies damage with an additional per-call target limit. Candidates are ordered by UUID so the
     * same world state always selects the same targets when a limit is reached.
     */
    public EffectHitResult applyDetailed(
        ServerLevel level,
        int effectAgeTicks,
        Entity owner,
        DamageSource damageSource,
        Predicate<LivingEntity> targetPolicy,
        int maxTargetsThisCall
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(damageSource, "damageSource");
        Objects.requireNonNull(targetPolicy, "targetPolicy");
        if (level.isClientSide()) {
            throw new IllegalStateException("Effect hitboxes are server-authoritative");
        }
        if (effectAgeTicks < 0) {
            throw new IllegalArgumentException("Effect age cannot be negative");
        }
        if (maxTargetsThisCall < 0) {
            throw new IllegalArgumentException("Per-call target limit cannot be negative");
        }

        int targetLimit = Math.min(maxTargetsThisCall, this.maxTargetsPerTick);
        if (targetLimit == 0) {
            return EffectHitResult.NONE;
        }

        int examined = 0;
        int damaged = 0;
        EffectBounds bounds = this.shape.bounds();
        AABB broadPhaseBounds = new AABB(
            bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
        );
        List<LivingEntity> candidates = new ArrayList<>(
            level.getEntitiesOfClass(LivingEntity.class, broadPhaseBounds)
        );
        candidates.sort(Comparator.comparing(LivingEntity::getUUID));
        for (LivingEntity target : candidates) {
            if (target == owner || !target.isAlive() || !this.shape.intersects(toEffectBounds(target.getBoundingBox()))
                || !targetPolicy.test(target)
                || !this.hitCooldowns.canHit(target.getUUID(), effectAgeTicks, this.rehitDelayTicks)) {
                continue;
            }
            examined++;
            if (this.outcome == EffectHitboxOutcome.REMOVE) {
                if (remove(level, target, damageSource)) {
                    this.hitCooldowns.recordHit(target.getUUID(), effectAgeTicks);
                    damaged++;
                }
            } else if (target.hurt(damageSource, this.damage)) {
                this.hitCooldowns.recordHit(target.getUUID(), effectAgeTicks);
                damaged++;
            }
            if (examined >= targetLimit) {
                break;
            }
        }
        return new EffectHitResult(examined, damaged);
    }

    private static boolean remove(ServerLevel level, LivingEntity target, DamageSource damageSource) {
        if (target instanceof ServerPlayer player) {
            player.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            if (!player.isRemoved() && player.isAlive()) {
                player.die(level.damageSources().fellOutOfWorld());
            }
            return player.isRemoved() || !player.isAlive();
        }
        target.discard();
        return target.isRemoved();
    }

    private static EffectBounds toEffectBounds(AABB bounds) {
        return new EffectBounds(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
    }
}
