package mafisher.quasar.api.gameplay;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One block hit returned by {@link EffectWorldQuery#raycast}. The state is the block at the hit
 * position and {@code distance} is measured from the ray origin.
 */
public record EffectBlockHit(
    BlockPos position,
    BlockState state,
    Vec3 location,
    Direction face,
    double distance
) {
    public EffectBlockHit {
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(face, "face");
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Hit distance must be finite and non-negative");
        }
    }
}
