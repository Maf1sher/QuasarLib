package mafisher.quasar.effect.persistence;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Serializable identity and current state of one active effect. A descriptor is persisted with
 * its level and restored after level load or server restart. {@code startGameTime} is absolute,
 * so elapsed time and phase resume without replaying ticks.
 */
public record EffectDescriptor(
    UUID instanceId,
    EffectKind kind,
    ResourceLocation typeId,
    ResourceLocation dimensionId,
    EffectTransform transform,
    float scale,
    long seed,
    long startGameTime,
    int durationTicks,
    EffectAudience audience,
    EffectCapabilities capabilities,
    EffectPersistence persistence,
    UUID ownerSessionId,
    CompoundTag state
) {
    public EffectDescriptor {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(state, "state");
        if (!Float.isFinite(scale) || scale <= 0.0F || scale > 128.0F) {
            throw new IllegalArgumentException("Effect descriptor scale must be between 0 and 128");
        }
        if (durationTicks < 1 || durationTicks > 72_000) {
            throw new IllegalArgumentException("Effect descriptor duration must be between 1 and 72000 ticks");
        }
        state = state.copy();
    }

    @Override
    public CompoundTag state() {
        return this.state.copy();
    }

    public EffectDescriptor withTransform(EffectTransform transform) {
        return new EffectDescriptor(this.instanceId, this.kind, this.typeId, this.dimensionId, transform,
            this.scale, this.seed, this.startGameTime, this.durationTicks, this.audience, this.capabilities,
            this.persistence, this.ownerSessionId, this.state);
    }

    public EffectDescriptor withScale(float scale) {
        return new EffectDescriptor(this.instanceId, this.kind, this.typeId, this.dimensionId, this.transform,
            scale, this.seed, this.startGameTime, this.durationTicks, this.audience, this.capabilities,
            this.persistence, this.ownerSessionId, this.state);
    }

    public EffectDescriptor withOwnerSessionId(UUID ownerSessionId) {
        return new EffectDescriptor(this.instanceId, this.kind, this.typeId, this.dimensionId, this.transform,
            this.scale, this.seed, this.startGameTime, this.durationTicks, this.audience, this.capabilities,
            this.persistence, ownerSessionId, this.state);
    }

    public EffectDescriptor withState(CompoundTag state) {
        return new EffectDescriptor(this.instanceId, this.kind, this.typeId, this.dimensionId, this.transform,
            this.scale, this.seed, this.startGameTime, this.durationTicks, this.audience, this.capabilities,
            this.persistence, this.ownerSessionId, state);
    }

    public boolean isExpired(long gameTime) {
        return gameTime - this.startGameTime >= this.durationTicks;
    }
}
