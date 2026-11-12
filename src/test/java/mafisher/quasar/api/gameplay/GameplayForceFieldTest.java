package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectForceField;
import mafisher.quasar.api.effect.EffectForceResult;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameplayForceFieldTest {
    private ServerLevel level;
    private GameplayEffectContext context;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenReturn(0L);
        this.context = context(2);
    }

    @AfterEach
    void tearDown() {
        this.context.close();
    }

    @Test
    void appliesNearestTargetsUpToTheBudget() {
        Entity near = entity(1.0);
        Entity middle = entity(3.0);
        Entity far = entity(5.0);
        when(this.level.getEntitiesOfClass(eq(Entity.class), any(AABB.class)))
            .thenReturn(List.of(far, middle, near));

        EffectForceResult result = this.context.applyForceField(
            EffectForceField.shockwave(new EffectPoint(0.0, 0.0, 0.0), 10.0, 1.0, 0.0, 0.0, 8),
            target -> true
        );

        assertEquals(2, result.examined());
        assertEquals(2, result.affected());
        assertTrue(result.truncated());
        assertEquals(2, this.context.counters().forceTargetsExamined());
        assertEquals(2, this.context.counters().forceTargetsAffected());
        verify(near).push(any(Vec3.class));
        verify(middle).push(any(Vec3.class));
        verify(far, never()).push(any(Vec3.class));
    }

    @Test
    void respectsTheTargetPolicy() {
        Entity allowed = entity(1.0);
        Entity rejected = entity(2.0);
        when(this.level.getEntitiesOfClass(eq(Entity.class), any(AABB.class)))
            .thenReturn(List.of(allowed, rejected));

        EffectForceResult result = this.context.applyForceField(
            EffectForceField.pull(new EffectPoint(0.0, 0.0, 0.0), 10.0, 1.0, 0.0, 8),
            target -> target == allowed
        );

        assertEquals(1, result.examined());
        assertEquals(1, result.affected());
        assertFalse(result.truncated());
        verify(allowed).push(any(Vec3.class));
        verify(rejected, never()).push(any(Vec3.class));
    }

    @Test
    void zeroBudgetIsSoftAndTruncated() {
        this.context.close();
        this.context = context(0);
        Entity target = entity(1.0);
        when(this.level.getEntitiesOfClass(eq(Entity.class), any(AABB.class)))
            .thenReturn(List.of(target));

        EffectForceResult result = this.context.applyForceField(
            EffectForceField.pull(new EffectPoint(0.0, 0.0, 0.0), 10.0, 1.0, 0.0, 8),
            entity -> true
        );

        assertEquals(0, result.examined());
        assertEquals(0, result.affected());
        assertTrue(result.truncated());
        verify(target, never()).push(any(Vec3.class));
    }

    private GameplayEffectContext context(int maxForceTargets) {
        return new GameplayEffectContext(
            this.level,
            new EffectScope(this.level),
            new GameplayEffectSpec(
                ResourceLocation.fromNamespaceAndPath("quasar_lib", "force_test"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                new Vec3(0.0, 0.0, 0.0),
                EffectPhasePlan.of(new EffectPhase("run", 100)),
                7L,
                new EffectTickBudget(0, 0, 0, 0, 0, maxForceTargets, 0),
                OwnerPolicy.CONTINUE
            ),
            UUID.randomUUID(),
            EffectCapabilities.NONE,
            new BlockJournal()
        );
    }

    private Entity entity(double x) {
        Entity entity = mock(Entity.class);
        when(entity.isRemoved()).thenReturn(false);
        when(entity.isAlive()).thenReturn(true);
        when(entity.getUUID()).thenReturn(UUID.randomUUID());
        when(entity.getBoundingBox()).thenReturn(new AABB(
            x - 0.5, -0.5, -0.5, x + 0.5, 0.5, 0.5
        ));
        return entity;
    }
}
