package mafisher.quasar.effect.block;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One server-authoritative intent to replace the block at a position. The engine validates the
 * position, applies the change with a full vanilla block update, and keeps overflow queued.
 * Rollback and protection are not part of this record; they are applied by the engine.
 */
public record BlockChange(BlockPos position, BlockState state) {
    public BlockChange {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
    }
}
