package mafisher.quasar.api.gameplay;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** One matching block returned by {@link EffectWorldQuery#scan}. */
public record EffectBlockSample(BlockPos position, BlockState state) {
    public EffectBlockSample {
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(state, "state");
    }
}
