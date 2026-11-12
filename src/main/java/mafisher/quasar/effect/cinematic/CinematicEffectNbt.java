package mafisher.quasar.effect.cinematic;

import java.util.Objects;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** NBT codec for the optional world anchor stored in a cinematic descriptor. */
public final class CinematicEffectNbt {
    private CinematicEffectNbt() {
    }

    /** Writes the anchor state; a zero anchor means the timeline stays unanchored. */
    public static void saveAnchor(CompoundTag state, Vec3 anchor) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(anchor, "anchor");
        state.putBoolean("anchored", !anchor.equals(Vec3.ZERO));
        state.putDouble("anchor_x", anchor.x);
        state.putDouble("anchor_y", anchor.y);
        state.putDouble("anchor_z", anchor.z);
    }

    /** Persisted anchor, or {@code null} when the timeline was not anchored. */
    public static Vec3 anchorOrNull(EffectDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        CompoundTag state = descriptor.state();
        if (!state.getBoolean("anchored")) {
            return null;
        }
        return new Vec3(
            state.getDouble("anchor_x"),
            state.getDouble("anchor_y"),
            state.getDouble("anchor_z")
        );
    }
}
