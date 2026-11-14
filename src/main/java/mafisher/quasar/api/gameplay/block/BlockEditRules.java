package mafisher.quasar.api.gameplay.block;

import java.util.Set;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One destructive-edit predicate shared by the bulk editor and the surface tear.
 *
 * <p>The rules are intentionally small and structural: world bounds, the protected bottom layer,
 * volume containment, air/replacement, and the indestructible block set. Protection, journaling,
 * light, and client resends remain the writer's responsibility.</p>
 */
final class BlockEditRules {
    /** Blocks that a carve or tear never removes, even inside the authored volume. */
    static final Set<Block> INDESTRUCTIBLE = Set.of(
        Blocks.BEDROCK,
        Blocks.BARRIER,
        Blocks.COMMAND_BLOCK,
        Blocks.CHAIN_COMMAND_BLOCK,
        Blocks.REPEATING_COMMAND_BLOCK,
        Blocks.STRUCTURE_BLOCK,
        Blocks.JIGSAW,
        Blocks.END_PORTAL_FRAME,
        Blocks.END_PORTAL
    );

    private BlockEditRules() {
    }

    /** The bottom world layer stays so a large carve never opens a see-through void floor. */
    static boolean isFloor(ServerLevel level, int y) {
        return y <= level.getMinBuildHeight();
    }

    /** Whether the current state is air, already the replacement, or indestructible. */
    static boolean isUneditable(BlockState current, BlockState replacement) {
        return current.isAir()
            || current.equals(replacement)
            || INDESTRUCTIBLE.contains(current.getBlock());
    }

    /** Whether the block center lies inside the authored volume. */
    static boolean insideVolume(EffectBlockVolume volume, BlockPos position) {
        return volume.contains(
            position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5
        );
    }

    /**
     * The full structural check for one candidate block. Callers still need a loaded chunk and
     * must run protection and journaling at the write.
     */
    static boolean canEdit(
        ServerLevel level,
        EffectBlockVolume volume,
        BlockPos position,
        BlockState current,
        BlockState replacement
    ) {
        return level.isInWorldBounds(position)
            && !isFloor(level, position.getY())
            && insideVolume(volume, position)
            && !isUneditable(current, replacement);
    }
}
