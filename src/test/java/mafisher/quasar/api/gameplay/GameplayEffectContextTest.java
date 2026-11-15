package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.damage.EffectDamageShape;
import mafisher.quasar.api.effect.damage.EffectHitbox;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class GameplayEffectContextTest {
    @Test
    void runsEveryCloseActionEvenWhenOneThrows() {
        GameplayEffectContext context = context();
        AtomicInteger completed = new AtomicInteger();
        context.onClose(() -> {
            throw new RuntimeException("cleanup failure");
        });
        context.onClose(completed::incrementAndGet);

        context.close();

        assertEquals(1, completed.get());
    }

    @Test
    void tracksAtMostFourDistinctHitboxesForTheInspector() {
        GameplayEffectContext context = context();
        ServerLevel level = context.level();
        when(level.getEntitiesOfClass(
            org.mockito.ArgumentMatchers.eq(net.minecraft.world.entity.LivingEntity.class),
            any(net.minecraft.world.phys.AABB.class)
        )).thenReturn(List.of());

        EffectDamageShape shape = new EffectDamageShape.Sphere(new EffectPoint(0.0, 0.0, 0.0), 2.0);
        for (int index = 0; index < 5; index++) {
            context.applyHitbox(
                new EffectHitbox(shape, 1.0F, 0, 1),
                mock(net.minecraft.world.damagesource.DamageSource.class),
                ignored -> true
            );
        }

        List<EffectHitbox> tracked = GameplayEffectAccess.debugHitboxes(context);
        assertEquals(4, tracked.size());
        assertEquals(shape, tracked.getFirst().shape());
    }

    private static GameplayEffectContext context() {
        ServerLevel level = mock(ServerLevel.class);
        EffectScope scope = mock(EffectScope.class);
        GameplayEffectSpec spec = mock(GameplayEffectSpec.class);
        when(spec.typeId()).thenReturn(
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "cleanup_test")
        );
        when(spec.budget()).thenReturn(EffectTickBudget.unlimited());
        return new GameplayEffectContext(
            level,
            scope,
            spec,
            UUID.fromString("00000000-0000-0000-0000-000000000099"),
            EffectCapabilities.NONE,
            new BlockJournal()
        );
    }
}
