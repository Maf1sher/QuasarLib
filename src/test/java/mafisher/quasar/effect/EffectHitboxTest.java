package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectDamageShape;
import mafisher.quasar.api.effect.EffectHitResult;
import mafisher.quasar.api.effect.EffectHitbox;
import mafisher.quasar.api.effect.EffectPoint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EffectHitboxTest {
    @Test
    void doesNotConsumeTheCooldownWhenDamageFails() {
        ServerLevel level = mock(ServerLevel.class);
        DamageSource source = mock(DamageSource.class);
        LivingEntity target = mock(LivingEntity.class);
        when(level.isClientSide()).thenReturn(false);
        when(target.getUUID()).thenReturn(UUID.randomUUID());
        when(target.getBoundingBox()).thenReturn(new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5));
        when(target.isAlive()).thenReturn(true);
        when(target.hurt(source, 4.0F)).thenReturn(false, true);
        when(level.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class)))
            .thenReturn(List.of(target));

        EffectHitbox hitbox = new EffectHitbox(
            new EffectDamageShape.Sphere(new EffectPoint(0.0, 0.0, 0.0), 2.0),
            4.0F,
            0,
            8
        );
        EffectHitResult first = hitbox.applyDetailed(level, 0, null, source, ignored -> true, 8);
        EffectHitResult second = hitbox.applyDetailed(level, 0, null, source, ignored -> true, 8);

        assertEquals(0, first.damaged());
        assertEquals(1, second.damaged());
        assertEquals(1, first.examined());
        assertEquals(1, second.examined());
    }
}
