package mafisher.quasar.api.persistence;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Optional persistence and capability settings for a newly started effect. The default is a
 * resumable effect with no declared capabilities and empty consumer state.
 */
public record EffectPersistenceOptions(
    EffectPersistence persistence,
    EffectCapabilities capabilities,
    CompoundTag state,
    UUID ownerSessionId
) {
    public static final EffectPersistenceOptions DEFAULT =
        new EffectPersistenceOptions(
            EffectPersistence.RESUMABLE, EffectCapabilities.NONE, new CompoundTag(), null
        );

    public EffectPersistenceOptions {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(state, "state");
        state = state.copy();
    }

    public EffectPersistenceOptions(
        EffectPersistence persistence,
        EffectCapabilities capabilities,
        CompoundTag state
    ) {
        this(persistence, capabilities, state, null);
    }

    /** Explicit opt-out for cheap, purely cosmetic effects. */
    public static EffectPersistenceOptions cosmetic() {
        return new EffectPersistenceOptions(
            EffectPersistence.COSMETIC, EffectCapabilities.NONE, new CompoundTag(), null
        );
    }

    public EffectPersistenceOptions withCapabilities(EffectCapabilities capabilities) {
        return new EffectPersistenceOptions(this.persistence, capabilities, this.state, this.ownerSessionId);
    }

    public EffectPersistenceOptions withState(CompoundTag state) {
        return new EffectPersistenceOptions(this.persistence, this.capabilities, state, this.ownerSessionId);
    }

    public EffectPersistenceOptions withOwnerSessionId(UUID ownerSessionId) {
        return new EffectPersistenceOptions(this.persistence, this.capabilities, this.state, ownerSessionId);
    }

    @Override
    public CompoundTag state() {
        return this.state.copy();
    }
}
