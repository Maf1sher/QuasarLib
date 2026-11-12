package mafisher.quasar.api.gameplay;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Admission-time view of one destructive gameplay effect. {@code ownerId} may be {@code null}
 * when the effect has no owner entity.
 */
public record DestructiveEffectPolicyContext(
    ServerLevel level,
    ResourceLocation typeId,
    UUID ownerId,
    Vec3 anchor,
    EffectCapabilities capabilities
) {
    public DestructiveEffectPolicyContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(capabilities, "capabilities");
        if (!capabilities.destructive()) {
            throw new IllegalArgumentException("Policy context requires the destructive capability");
        }
        if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z)) {
            throw new IllegalArgumentException("Policy context anchor must be finite");
        }
    }
}
