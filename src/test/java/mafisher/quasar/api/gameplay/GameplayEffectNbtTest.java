package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.gameplay.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.persistence.EffectDescriptorNbt;
import mafisher.quasar.testing.GameplayEffectFixtures;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class GameplayEffectNbtTest {
    @Test
    void preservesTheBlockChangeBudgetInDescriptorNbt() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec(new EffectTickBudget(1, 2, 3, 32));
        EffectDescriptor descriptor = GameplayEffectFixtures.descriptor(spec, new CompoundTag());

        EffectDescriptor loaded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(32, GameplayEffectNbt.loadSpec(loaded).budget().maxBlockChanges());
    }

    @Test
    void defaultsTheBlockChangeBudgetForOlderDescriptors() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec(new EffectTickBudget(1, 2, 3, 32));
        CompoundTag tag = EffectDescriptorNbt.save(
            GameplayEffectFixtures.descriptor(spec, new CompoundTag())
        );
        tag.getCompound("state").getCompound("spec").getCompound("budget")
            .remove("max_block_changes");

        EffectDescriptor loaded = EffectDescriptorNbt.load(tag);

        assertEquals(0, GameplayEffectNbt.loadSpec(loaded).budget().maxBlockChanges());
    }

    @Test
    void preservesTheWorldSampleBudgetInDescriptorNbt() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec(
            new EffectTickBudget(1, 2, 3, 32, 64)
        );
        EffectDescriptor descriptor = GameplayEffectFixtures.descriptor(spec, new CompoundTag());

        EffectDescriptor loaded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(64, GameplayEffectNbt.loadSpec(loaded).budget().maxWorldSamples());
    }

    @Test
    void preservesTheForceAndDebrisBudgetsInDescriptorNbt() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec(
            new EffectTickBudget(1, 2, 3, 32, 64, 16, 24)
        );
        EffectDescriptor descriptor = GameplayEffectFixtures.descriptor(spec, new CompoundTag());

        EffectDescriptor loaded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(16, GameplayEffectNbt.loadSpec(loaded).budget().maxForceTargets());
        assertEquals(24, GameplayEffectNbt.loadSpec(loaded).budget().maxDebrisPieces());
    }

    @Test
    void defaultsTheForceAndDebrisBudgetsForOlderDescriptors() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec(
            new EffectTickBudget(1, 2, 3, 32, 64, 16, 24)
        );
        CompoundTag tag = EffectDescriptorNbt.save(
            GameplayEffectFixtures.descriptor(spec, new CompoundTag())
        );
        tag.getCompound("state").getCompound("spec").getCompound("budget")
            .remove("max_force_targets");
        tag.getCompound("state").getCompound("spec").getCompound("budget")
            .remove("max_debris_pieces");

        EffectDescriptor loaded = EffectDescriptorNbt.load(tag);

        assertEquals(0, GameplayEffectNbt.loadSpec(loaded).budget().maxForceTargets());
        assertEquals(0, GameplayEffectNbt.loadSpec(loaded).budget().maxDebrisPieces());
    }

    @Test
    void defaultsTheWorldSampleBudgetForOlderDescriptors() {
        GameplayEffectSpec spec = GameplayEffectFixtures.spec(
            new EffectTickBudget(1, 2, 3, 32, 64)
        );
        CompoundTag tag = EffectDescriptorNbt.save(
            GameplayEffectFixtures.descriptor(spec, new CompoundTag())
        );
        tag.getCompound("state").getCompound("spec").getCompound("budget")
            .remove("max_world_samples");

        EffectDescriptor loaded = EffectDescriptorNbt.load(tag);

        assertEquals(0, GameplayEffectNbt.loadSpec(loaded).budget().maxWorldSamples());
    }

    @Test
    void preservesPriorityInDescriptorNbt() {
        GameplayEffectSpec spec = GameplayEffectFixtures.specWithPriority(EffectPriority.HIGH);
        EffectDescriptor descriptor = GameplayEffectFixtures.descriptor(spec, new CompoundTag());

        EffectDescriptor loaded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(EffectPriority.HIGH, GameplayEffectNbt.loadSpec(loaded).priority());
    }

    @Test
    void defaultsPriorityForOlderDescriptors() {
        GameplayEffectSpec spec = GameplayEffectFixtures.specWithPriority(EffectPriority.HIGH);
        CompoundTag tag = EffectDescriptorNbt.save(
            GameplayEffectFixtures.descriptor(spec, new CompoundTag())
        );
        tag.getCompound("state").getCompound("spec").remove("priority");

        EffectDescriptor loaded = EffectDescriptorNbt.load(tag);

        assertEquals(EffectPriority.NORMAL, GameplayEffectNbt.loadSpec(loaded).priority());
    }
}
