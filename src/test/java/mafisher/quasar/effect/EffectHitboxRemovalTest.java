package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectDamageShape;
import mafisher.quasar.api.effect.EffectHitResult;
import mafisher.quasar.api.effect.EffectHitbox;
import mafisher.quasar.api.effect.EffectPoint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EffectHitboxRemovalTest {
    @Test
    void doesNotConsumeTheCooldownWhenRemovalFails() {
        ServerLevel level = mock(ServerLevel.class);
        DamageSource source = mock(DamageSource.class);
        when(level.isClientSide()).thenReturn(false);

        LivingEntity mob = living(new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5));
        when(mob.isRemoved()).thenReturn(false, true);
        when(level.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class)))
            .thenReturn(List.of(mob));

        EffectHitbox hitbox = EffectHitbox.removal(
            new EffectDamageShape.Sphere(new EffectPoint(0.0, 0.0, 0.0), 2.0), 8
        );
        EffectHitResult first = hitbox.applyDetailed(level, 0, null, source, target -> true, 8);
        EffectHitResult second = hitbox.applyDetailed(level, 0, null, source, target -> true, 8);

        assertEquals(0, first.damaged());
        assertEquals(1, second.damaged());
        verify(mob, org.mockito.Mockito.times(2)).discard();
    }

    @Test
    void removesMobsAndKillsPlayersInsideTheHorizon() {
        ServerLevel level = mock(ServerLevel.class);
        DamageSources sources = mock(DamageSources.class);
        DamageSource source = mock(DamageSource.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.damageSources()).thenReturn(sources);
        when(sources.fellOutOfWorld()).thenReturn(source);

        LivingEntity mob = living(new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5));
        doAnswer(invocation -> {
            when(mob.isRemoved()).thenReturn(true);
            return null;
        }).when(mob).discard();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.getBoundingBox()).thenReturn(new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5));
        when(player.isAlive()).thenReturn(true);
        when(player.isRemoved()).thenReturn(false);
        when(player.hurt(source, Float.MAX_VALUE)).thenAnswer(invocation -> {
            when(player.isAlive()).thenReturn(false);
            return true;
        });
        when(level.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class)))
            .thenReturn(List.of(mob, player));

        EffectHitbox hitbox = EffectHitbox.removal(
            new EffectDamageShape.Sphere(new EffectPoint(0.0, 0.0, 0.0), 2.0), 8
        );
        EffectHitResult result = hitbox.applyDetailed(level, 0, null, source, target -> true, 8);

        assertEquals(2, result.examined());
        assertEquals(2, result.damaged());
        verify(mob).discard();
        verify(player).hurt(source, Float.MAX_VALUE);
    }

    private static LivingEntity living(AABB bounds) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUUID()).thenReturn(UUID.randomUUID());
        when(entity.getBoundingBox()).thenReturn(bounds);
        when(entity.isAlive()).thenReturn(true);
        when(entity.isRemoved()).thenReturn(false);
        return entity;
    }
}
