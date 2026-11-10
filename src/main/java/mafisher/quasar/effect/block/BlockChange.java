package mafisher.quasar.effect.block;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One server-authoritative intent to replace the block at a position. The engine validates the
 * position, applies the change, and keeps overflow queued. Rollback and protection are not part of
 * this record; they are applied by the engine.
 *
 * <p>{@code updateNeighbors} is {@code true} for normal changes. Interior bulk operations may set
 * it to {@code false}; the engine still sends client and light updates but suppresses neighbour
 * shape and block updates. Boundary blocks must keep normal updates.</p>
 */
public record BlockChange(BlockPos position, BlockState state, boolean updateNeighbors) {
    public BlockChange {
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(state, "state");
    }

    public BlockChange(BlockPos position, BlockState state) {
        this(position, state, true);
    }

    /** An air replacement that suppresses neighbour updates; intended for volume interiors. */
    public static BlockChange airWithoutNeighbourUpdates(BlockPos position) {
        return new BlockChange(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
    }
}
