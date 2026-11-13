package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.api.effect.budget.EffectBudgetReport;
import mafisher.quasar.api.effect.budget.EffectBudgets;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.runtime.TestEffectRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import mafisher.quasar.testing.GlobalStateTest;

@GlobalStateTest
class GameplayEffectSpawnTest {
    private ServerLevel level;
    private GameplayEffectContext context;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenReturn(0L);
        when(this.level.addFreshEntity(any(Entity.class))).thenReturn(true);
        TestEffectRuntime.install(this.level);
        this.context = new GameplayEffectContext(
            this.level,
            new EffectScope(this.level),
            spec(1),
            UUID.randomUUID(),
            EffectCapabilities.NONE,
            new BlockJournal()
        );
    }

    @AfterEach
    void tearDown() {
        this.context.close();
        TestEffectRuntime.uninstall(this.level);
    }

    @Test
    void dropsSpawnsAboveTheSessionBudgetWithoutThrowing() {
        Entity entity = entity();

        assertNotNull(this.context.spawnEntity(entity));
        assertFalse(this.context.canSpawnEntity());
        assertNull(this.context.spawnEntity(entity));
        assertEquals(1, EffectBudgetService.report(this.level).droppedEntitySpawns());
    }

    @Test
    void dropsSpawnsAboveTheLevelTickBudgetWithoutThrowing() {
        this.context.close();
        this.context = new GameplayEffectContext(
            this.level,
            new EffectScope(this.level),
            spec(1_000),
            UUID.randomUUID(),
            EffectCapabilities.NONE,
            new BlockJournal()
        );
        Entity entity = entity();

        int spawned = 0;
        while (true) {
            if (this.context.spawnEntity(entity) == null) {
                break;
            }
            spawned++;
        }

        EffectBudgetReport report = EffectBudgetService.report(this.level);
        assertEquals(EffectBudgets.entitySpawnsPerLevelTick(), spawned);
        assertEquals(1, report.droppedEntitySpawns());
    }

    private Entity entity() {
        Entity entity = mock(Entity.class);
        when(entity.level()).thenReturn(this.level);
        when(entity.isRemoved()).thenReturn(false);
        return entity;
    }

    private static GameplayEffectSpec spec(int maxEntitySpawns) {
        return new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "spawn_test"),
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            Vec3.ZERO,
            EffectPhasePlan.of(new EffectPhase("run", 100)),
            7L,
            new EffectTickBudget(0, 0, maxEntitySpawns),
            OwnerPolicy.CONTINUE
        );
    }
}
