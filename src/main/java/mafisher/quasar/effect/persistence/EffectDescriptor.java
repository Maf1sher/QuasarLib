package mafisher.quasar.effect.persistence;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.EffectTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Serializable identity and current state of one active effect. A descriptor is persisted with
 * its level and restored after level load or server restart. {@code startGameTime} is absolute,
 * so elapsed time and phase resume without replaying ticks.
 *
 * <p>State NBT is owned by the descriptor after construction. Update helpers that do not change
 * state reuse the same tag, while {@link #state()} returns a defensive copy for callers.
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class EffectDescriptor {
    private final Fields fields;
    private final CompoundTag state;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public EffectDescriptor(
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
        this(
            new Fields(
                instanceId,
                kind,
                typeId,
                dimensionId,
                transform,
                scale,
                seed,
                startGameTime,
                durationTicks,
                audience,
                capabilities,
                persistence,
                ownerSessionId
            ),
            Objects.requireNonNull(state, "state").copy()
        );
    }

    private EffectDescriptor(Fields fields, CompoundTag state) {
        this.fields = Objects.requireNonNull(fields, "fields");
        this.state = state;
    }

    public UUID instanceId() {
        return this.fields.instanceId();
    }

    public EffectKind kind() {
        return this.fields.kind();
    }

    public ResourceLocation typeId() {
        return this.fields.typeId();
    }

    public ResourceLocation dimensionId() {
        return this.fields.dimensionId();
    }

    public EffectTransform transform() {
        return this.fields.transform();
    }

    public float scale() {
        return this.fields.scale();
    }

    public long seed() {
        return this.fields.seed();
    }

    public long startGameTime() {
        return this.fields.startGameTime();
    }

    public int durationTicks() {
        return this.fields.durationTicks();
    }

    public EffectAudience audience() {
        return this.fields.audience();
    }

    public EffectCapabilities capabilities() {
        return this.fields.capabilities();
    }

    public EffectPersistence persistence() {
        return this.fields.persistence();
    }

    public UUID ownerSessionId() {
        return this.fields.ownerSessionId();
    }

    /** A defensive copy of the consumer state. */
    public CompoundTag state() {
        return this.state.copy();
    }

    /** Internal read-only view used by persistence to avoid copying state on every access. */
    CompoundTag stateView() {
        return this.state;
    }

    public EffectDescriptor withTransform(EffectTransform transform) {
        return new EffectDescriptor(this.fields.withTransform(transform), this.state);
    }

    public EffectDescriptor withScale(float scale) {
        return new EffectDescriptor(this.fields.withScale(scale), this.state);
    }

    public EffectDescriptor withOwnerSessionId(UUID ownerSessionId) {
        return new EffectDescriptor(this.fields.withOwnerSessionId(ownerSessionId), this.state);
    }

    public EffectDescriptor withState(CompoundTag state) {
        return new EffectDescriptor(this.fields, Objects.requireNonNull(state, "state").copy());
    }

    public boolean isExpired(long gameTime) {
        return gameTime - this.startGameTime() >= this.durationTicks();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EffectDescriptor descriptor
            && this.fields.equals(descriptor.fields)
            && this.state.equals(descriptor.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.fields, this.state);
    }

    @Override
    public String toString() {
        return "EffectDescriptor[fields=" + this.fields + ", state=" + this.state + "]";
    }

    private record Fields(
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
        UUID ownerSessionId
    ) {
        private Fields {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(dimensionId, "dimensionId");
            Objects.requireNonNull(transform, "transform");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(capabilities, "capabilities");
            Objects.requireNonNull(persistence, "persistence");
            if (!Float.isFinite(scale) || scale <= 0.0F || scale > 128.0F) {
                throw new IllegalArgumentException("Effect descriptor scale must be between 0 and 128");
            }
            if (!EffectTimeline.isValidDuration(durationTicks)) {
                throw new IllegalArgumentException(
                    "Effect descriptor duration must be between 1 and " + EffectTimeline.MAX_DURATION_TICKS + " ticks"
                );
            }
        }

        private Fields withTransform(EffectTransform transform) {
            return new Fields(
                this.instanceId,
                this.kind,
                this.typeId,
                this.dimensionId,
                Objects.requireNonNull(transform, "transform"),
                this.scale,
                this.seed,
                this.startGameTime,
                this.durationTicks,
                this.audience,
                this.capabilities,
                this.persistence,
                this.ownerSessionId
            );
        }

        private Fields withScale(float scale) {
            return new Fields(
                this.instanceId,
                this.kind,
                this.typeId,
                this.dimensionId,
                this.transform,
                scale,
                this.seed,
                this.startGameTime,
                this.durationTicks,
                this.audience,
                this.capabilities,
                this.persistence,
                this.ownerSessionId
            );
        }

        private Fields withOwnerSessionId(UUID ownerSessionId) {
            return new Fields(
                this.instanceId,
                this.kind,
                this.typeId,
                this.dimensionId,
                this.transform,
                this.scale,
                this.seed,
                this.startGameTime,
                this.durationTicks,
                this.audience,
                this.capabilities,
                this.persistence,
                ownerSessionId
            );
        }
    }
}
