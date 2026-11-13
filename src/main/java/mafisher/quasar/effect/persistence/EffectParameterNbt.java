package mafisher.quasar.effect.persistence;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectParameterState;
import mafisher.quasar.api.persistence.EffectDescriptor;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.ApiStatus;

/** NBT codec for server-authored parameter overrides stored in a visual descriptor. */
@ApiStatus.Internal
public final class EffectParameterNbt {
    /** Reserved engine key inside descriptor state. */
    public static final String STATE_KEY = "quasar_parameters";

    private EffectParameterNbt() {
    }

    public static void saveInto(CompoundTag descriptorState, EffectParameterState state) {
        Objects.requireNonNull(descriptorState, "descriptorState");
        Objects.requireNonNull(state, "state");
        if (state.isEmpty()) {
            descriptorState.remove(STATE_KEY);
        } else {
            descriptorState.put(STATE_KEY, state.save());
        }
    }

    public static EffectParameterState loadFrom(EffectDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return loadFrom(descriptor.stateView());
    }

    public static EffectParameterState loadFrom(CompoundTag descriptorState) {
        Objects.requireNonNull(descriptorState, "descriptorState");
        if (!descriptorState.contains(STATE_KEY)) {
            return EffectParameterState.EMPTY;
        }
        return EffectParameterState.load(descriptorState.getCompound(STATE_KEY));
    }
}
