package mafisher.quasar.api.persistence;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.budget.EffectLimits;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

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

    public static Builder builder(
        UUID instanceId,
        EffectKind kind,
        ResourceLocation typeId,
        ResourceLocation dimensionId
    ) {
        return new Builder(instanceId, kind, typeId, dimensionId);
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

    /** Optional parent attachment; {@code null} for a standalone effect. */
    @Nullable
    public EffectAttachment attachment() {
        return this.fields.attachment();
    }

    /** A defensive copy of the consumer state. */
    public CompoundTag state() {
        return this.state.copy();
    }

    /** Internal read-only view used by persistence to avoid copying state on every access. */
    public CompoundTag stateView() {
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

    /** Builder that groups placement, timing, and ownership instead of a 14-argument constructor. */
    public static final class Builder {
        private final UUID instanceId;
        private final EffectKind kind;
        private final ResourceLocation typeId;
        private final ResourceLocation dimensionId;
        private EffectTransform transform;
        private float scale = 1.0F;
        private long seed;
        private long startGameTime;
        private int durationTicks = 1;
        private EffectAudience audience = EffectAudience.dimensionWide();
        private EffectCapabilities capabilities = EffectCapabilities.NONE;
        private EffectPersistence persistence = EffectPersistence.COSMETIC;
        private UUID ownerSessionId;
        @Nullable
        private EffectAttachment attachment;
        private CompoundTag state = new CompoundTag();
        private boolean timingSet;

        private Builder(
            UUID instanceId,
            EffectKind kind,
            ResourceLocation typeId,
            ResourceLocation dimensionId
        ) {
            this.instanceId = instanceId;
            this.kind = kind;
            this.typeId = typeId;
            this.dimensionId = dimensionId;
        }

        public Builder placement(EffectTransform transform, float scale) {
            this.transform = transform;
            this.scale = scale;
            return this;
        }

        public Builder timing(long seed, long startGameTime, int durationTicks) {
            this.seed = seed;
            this.startGameTime = startGameTime;
            this.durationTicks = durationTicks;
            this.timingSet = true;
            return this;
        }

        public Builder audience(EffectAudience audience) {
            this.audience = audience;
            return this;
        }

        public Builder capabilities(EffectCapabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder persistence(EffectPersistence persistence) {
            this.persistence = persistence;
            return this;
        }

        public Builder ownerSessionId(UUID ownerSessionId) {
            this.ownerSessionId = ownerSessionId;
            return this;
        }

        /** Sets or clears the effect's parent attachment. */
        public Builder attachment(@Nullable EffectAttachment attachment) {
            this.attachment = attachment;
            return this;
        }

        public Builder state(CompoundTag state) {
            this.state = Objects.requireNonNull(state, "state");
            return this;
        }

        public EffectDescriptor build() {
            if (!this.timingSet) {
                throw new IllegalStateException("Effect descriptor timing is required");
            }
            return new EffectDescriptor(
                new Fields(
                    this.instanceId,
                    this.kind,
                    this.typeId,
                    this.dimensionId,
                    Objects.requireNonNull(this.transform, "transform"),
                    this.scale,
                    this.seed,
                    this.startGameTime,
                    this.durationTicks,
                    this.audience,
                    this.capabilities,
                    this.persistence,
                    this.ownerSessionId,
                    this.attachment
                ),
                Objects.requireNonNull(this.state, "state").copy()
            );
        }
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
        UUID ownerSessionId,
        @Nullable EffectAttachment attachment
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
            if (!Float.isFinite(scale) || scale <= 0.0F || scale > EffectLimits.MAX_SCALE) {
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
                this.ownerSessionId,
                this.attachment
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
                this.ownerSessionId,
                this.attachment
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
                ownerSessionId,
                this.attachment
            );
        }
    }
}
