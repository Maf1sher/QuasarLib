package mafisher.quasar.effect.entity;

import mafisher.quasar.effect.EffectTimeline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Base for short-lived effect entities with synchronized animation state, roll, and automatic expiry. */
public abstract class TimedEffectEntity extends Entity {
    private static final EntityDataAccessor<Integer> LIFETIME_TICKS = SynchedEntityData.defineId(
        TimedEffectEntity.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Byte> EFFECT_STATE = SynchedEntityData.defineId(
        TimedEffectEntity.class, EntityDataSerializers.BYTE
    );
    private static final EntityDataAccessor<Long> START_GAME_TIME = SynchedEntityData.defineId(
        TimedEffectEntity.class, EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Float> EFFECT_ROLL = SynchedEntityData.defineId(
        TimedEffectEntity.class, EntityDataSerializers.FLOAT
    );

    protected TimedEffectEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LIFETIME_TICKS, 80);
        builder.define(EFFECT_STATE, (byte)0);
        builder.define(START_GAME_TIME, this.level().getGameTime());
        builder.define(EFFECT_ROLL, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && EffectTimeline.isFinished(
            this.level().getGameTime(), this.getEffectStartGameTime(), this.getLifetimeTicks()
        )) {
            this.onEffectExpired();
            this.discard();
        }
    }

    public final void configureLifetime(int lifetimeTicks) {
        if (this.level().isClientSide) {
            throw new IllegalStateException("Effect entity state is server-authoritative");
        }
        if (lifetimeTicks < 1 || lifetimeTicks > 72_000) {
            throw new IllegalArgumentException("Effect entity lifetime must be between 1 and 72000 ticks");
        }
        this.entityData.set(LIFETIME_TICKS, lifetimeTicks);
        this.entityData.set(START_GAME_TIME, this.level().getGameTime());
    }

    public final int getLifetimeTicks() {
        return this.entityData.get(LIFETIME_TICKS);
    }

    public final byte getEffectState() {
        return this.entityData.get(EFFECT_STATE);
    }

    public final void setEffectState(byte state) {
        if (this.level().isClientSide) {
            throw new IllegalStateException("Effect entity state is server-authoritative");
        }
        this.entityData.set(EFFECT_STATE, state);
    }

    public final float getEffectRoll() {
        return this.entityData.get(EFFECT_ROLL);
    }

    public final void setEffectRoll(float roll) {
        if (this.level().isClientSide) {
            throw new IllegalStateException("Effect entity state is server-authoritative");
        }
        if (!Float.isFinite(roll)) {
            throw new IllegalArgumentException("Effect roll must be finite");
        }
        this.entityData.set(EFFECT_ROLL, roll);
    }

    public final long getEffectStartGameTime() {
        return this.entityData.get(START_GAME_TIME);
    }

    public final float getEffectProgress(float partialTick) {
        return EffectTimeline.progress(
            this.level().getGameTime(), partialTick, this.getEffectStartGameTime(), this.getLifetimeTicks()
        );
    }

    protected void onEffectExpired() {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        int lifetime = compound.getInt("EffectLifetimeTicks");
        if (lifetime >= 1 && lifetime <= 72_000) {
            this.entityData.set(LIFETIME_TICKS, lifetime);
        }
        if (compound.contains("EffectState")) {
            this.entityData.set(EFFECT_STATE, compound.getByte("EffectState"));
        }
        if (compound.contains("EffectStartGameTime")) {
            this.entityData.set(START_GAME_TIME, compound.getLong("EffectStartGameTime"));
        }
        if (compound.contains("EffectRoll")) {
            float roll = compound.getFloat("EffectRoll");
            if (Float.isFinite(roll)) {
                this.entityData.set(EFFECT_ROLL, roll);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("EffectLifetimeTicks", this.getLifetimeTicks());
        compound.putByte("EffectState", this.getEffectState());
        compound.putLong("EffectStartGameTime", this.getEffectStartGameTime());
        compound.putFloat("EffectRoll", this.getEffectRoll());
    }
}
