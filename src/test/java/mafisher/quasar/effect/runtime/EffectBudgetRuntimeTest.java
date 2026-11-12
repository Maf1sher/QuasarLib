package mafisher.quasar.effect.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectBudgetRuntimeTest {
    private final ServerLevel level = mock(ServerLevel.class);
    private final TestBudgetSource budgets = new TestBudgetSource().entitySpawnsPerLevelTick(2);
    private final EffectBudgetRuntime runtime = new EffectBudgetRuntime(this.level, this.budgets);
    private long gameTime;

    @BeforeEach
    void setUp() {
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
    }

    @Test
    void countsCullDeferralAndAudioEvents() {
        this.runtime.recordCull();
        this.runtime.recordDeferral();
        this.runtime.recordAudioEviction();
        this.runtime.recordAudioDrop();

        EffectBudgetRuntime.BudgetCounters counters = this.runtime.counters();
        assertEquals(1L, counters.culledEffects());
        assertEquals(1L, counters.deferredStarts());
        assertEquals(1L, counters.audioEvictions());
        assertEquals(1L, counters.audioDrops());
        assertEquals(0L, counters.droppedEntitySpawns());
    }

    @Test
    void spawnBudgetResetsOnTheNextGameTick() {
        assertTrue(this.runtime.entitySpawnBudgetAvailable());
        assertTrue(this.runtime.tryAcquireEntitySpawn());
        assertTrue(this.runtime.tryAcquireEntitySpawn());
        assertFalse(this.runtime.entitySpawnBudgetAvailable());
        assertFalse(this.runtime.tryAcquireEntitySpawn());
        assertEquals(1L, this.runtime.counters().droppedEntitySpawns());

        this.gameTime++;

        assertTrue(this.runtime.entitySpawnBudgetAvailable());
        assertTrue(this.runtime.tryAcquireEntitySpawn());
        assertEquals(1L, this.runtime.counters().droppedEntitySpawns());
    }

    @Test
    void clearDropsCountersAndSpawnBudget() {
        this.runtime.recordCull();
        assertTrue(this.runtime.tryAcquireEntitySpawn());
        assertTrue(this.runtime.tryAcquireEntitySpawn());

        this.runtime.clear();

        assertEquals(0L, this.runtime.counters().culledEffects());
        assertTrue(this.runtime.entitySpawnBudgetAvailable());
        assertTrue(this.runtime.tryAcquireEntitySpawn());
        assertTrue(this.runtime.tryAcquireEntitySpawn());
    }
}
