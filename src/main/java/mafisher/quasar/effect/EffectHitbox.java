package mafisher.quasar.effect;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/** Reusable server-authoritative damage volume for effect entities and gameplay executions. */
public final class EffectHitbox {
    private EffectDamageShape shape;
    private final float damage;
    private final int rehitDelayTicks;
    private final int maxTargetsPerTick;
    private final EffectHitCooldowns hitCooldowns = new EffectHitCooldowns();

    public EffectHitbox(EffectDamageShape shape, float damage, int rehitDelayTicks, int maxTargetsPerTick) {
        this.shape = Objects.requireNonNull(shape, "shape");
        if (!Float.isFinite(damage) || damage <= 0.0F) {
            throw new IllegalArgumentException("Damage must be finite and positive");
        }
        if (rehitDelayTicks < 0 || maxTargetsPerTick < 1) {
            throw new IllegalArgumentException("Invalid hitbox limits");
        }
        this.damage = damage;
        this.rehitDelayTicks = rehitDelayTicks;
        this.maxTargetsPerTick = maxTargetsPerTick;
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
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(damageSource, "damageSource");
        Objects.requireNonNull(targetPolicy, "targetPolicy");
        if (level.isClientSide()) {
            throw new IllegalStateException("Effect hitboxes are server-authoritative");
        }
        if (effectAgeTicks < 0) {
            throw new IllegalArgumentException("Effect age cannot be negative");
        }

        int examined = 0;
        int damaged = 0;
        EffectBounds bounds = this.shape.bounds();
        AABB broadPhaseBounds = new AABB(
            bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
        );
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, broadPhaseBounds)) {
            if (target == owner || !target.isAlive() || !this.shape.intersects(toEffectBounds(target.getBoundingBox()))
                || !targetPolicy.test(target)
                || !this.hitCooldowns.canHit(target.getUUID(), effectAgeTicks, this.rehitDelayTicks)) {
                continue;
            }
            this.hitCooldowns.recordHit(target.getUUID(), effectAgeTicks);
            examined++;
            if (target.hurt(damageSource, this.damage)) {
                damaged++;
            }
            if (examined >= this.maxTargetsPerTick) {
                break;
            }
        }
        return damaged;
    }

    private static EffectBounds toEffectBounds(AABB bounds) {
        return new EffectBounds(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
    }
}
