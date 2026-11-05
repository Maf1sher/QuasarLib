package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Immutable server-authored parameters for one gameplay effect session. */
public record GameplayEffectSpec(
    ResourceLocation typeId,
    UUID ownerId,
    Vec3 anchor,
    EffectPhasePlan phases,
    long seed,
    EffectTickBudget budget,
    OwnerPolicy ownerPolicy
) {
    public GameplayEffectSpec {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(phases, "phases");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(ownerPolicy, "ownerPolicy");
        if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z)) {
            throw new IllegalArgumentException("Gameplay effect anchor must be finite");
        }
    }
}
