package mafisher.quasar.effect.gameplay;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.audio.EffectAudioSpec;
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
    OwnerPolicy ownerPolicy,
    EffectAudioSpec audio,
    EffectPriority priority
) {
    public GameplayEffectSpec {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(phases, "phases");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(ownerPolicy, "ownerPolicy");
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(priority, "priority");
        if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z)) {
            throw new IllegalArgumentException("Gameplay effect anchor must be finite");
        }
        audio.requireFitsWithin(phases.totalDurationTicks());
    }

    /** Source-compatible constructor for effects that use the default priority. */
    public GameplayEffectSpec(
        ResourceLocation typeId,
        UUID ownerId,
        Vec3 anchor,
        EffectPhasePlan phases,
        long seed,
        EffectTickBudget budget,
        OwnerPolicy ownerPolicy,
        EffectAudioSpec audio
    ) {
        this(typeId, ownerId, anchor, phases, seed, budget, ownerPolicy, audio, EffectPriority.NORMAL);
    }

    public GameplayEffectSpec(
        ResourceLocation typeId,
        UUID ownerId,
        Vec3 anchor,
        EffectPhasePlan phases,
        long seed,
        EffectTickBudget budget,
        OwnerPolicy ownerPolicy
    ) {
        this(typeId, ownerId, anchor, phases, seed, budget, ownerPolicy, EffectAudioSpec.NONE,
            EffectPriority.NORMAL);
    }
}
