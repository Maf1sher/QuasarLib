package mafisher.quasar.effect.block;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One application-time block change presented to protection callbacks. {@code ownerId} may be
 * {@code null} when the effect has no owner entity.
 */
public record BlockProtectionContext(
    ServerLevel level,
    BlockChange change,
    BlockState currentState,
    ResourceLocation effectTypeId,
    UUID effectSessionId,
    UUID ownerId
) {
    public BlockProtectionContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(effectTypeId, "effectTypeId");
        Objects.requireNonNull(effectSessionId, "effectSessionId");
    }

    public BlockPos position() {
        return this.change.position();
    }

    public BlockState newState() {
        return this.change.state();
    }
}
