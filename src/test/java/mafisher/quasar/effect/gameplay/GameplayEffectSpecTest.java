package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GameplayEffectSpecTest {
    @Test
    void storesValidParameters() {
        GameplayEffectSpec spec = spec(new Vec3(1.0, 2.0, 3.0));

        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "test"), spec.typeId());
        assertEquals(30, spec.phases().totalDurationTicks());
        assertEquals(10, spec.budget().maxTargetsExamined());
        assertEquals(0, spec.budget().maxBlockChanges());
        assertEquals(OwnerPolicy.STOP_WHEN_UNAVAILABLE, spec.ownerPolicy());
    }

    @Test
    void rejectsMissingAndNonFiniteParameters() {
        assertThrows(NullPointerException.class, () -> new GameplayEffectSpec(
            null, UUID.randomUUID(), Vec3.ZERO, plan(), 0L, budget(), OwnerPolicy.CONTINUE
        ));
        assertThrows(NullPointerException.class, () -> new GameplayEffectSpec(
            id(), UUID.randomUUID(), null, plan(), 0L, budget(), OwnerPolicy.CONTINUE
        ));
        assertThrows(NullPointerException.class, () -> new GameplayEffectSpec(
            id(), UUID.randomUUID(), Vec3.ZERO, null, 0L, budget(), OwnerPolicy.CONTINUE
        ));
        assertThrows(NullPointerException.class, () -> new GameplayEffectSpec(
            id(), UUID.randomUUID(), Vec3.ZERO, plan(), 0L, null, OwnerPolicy.CONTINUE
        ));
        assertThrows(NullPointerException.class, () -> new GameplayEffectSpec(
            id(), UUID.randomUUID(), Vec3.ZERO, plan(), 0L, budget(), null
        ));
        assertThrows(IllegalArgumentException.class, () -> spec(new Vec3(Double.NaN, 0.0, 0.0)));
    }

    private static GameplayEffectSpec spec(Vec3 anchor) {
        return new GameplayEffectSpec(
            id(),
            UUID.randomUUID(),
            anchor,
            plan(),
            42L,
            budget(),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE
        );
    }

    private static ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    }

    private static EffectPhasePlan plan() {
        return EffectPhasePlan.of(
            new EffectPhase("telegraph", 20),
            new EffectPhase("strike", 10)
        );
    }

    private static EffectTickBudget budget() {
        return new EffectTickBudget(10, 2, 1);
    }
}
