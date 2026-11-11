package mafisher.quasar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuasarConfigTest {
    @BeforeEach
    @AfterEach
    void unloadConfig() {
        TestConfig.unload();
    }

    @Test
    void enablesDestructiveEffectsByDefault() {
        assertTrue(QuasarConfig.DESTRUCTIVE_EFFECTS_ENABLED.getDefault());
    }

    @Test
    void publishesANonEmptyConfigSpec() {
        assertFalse(QuasarConfig.SPEC.isEmpty());
    }

    @Test
    void failsClosedBeforeTheConfigLoads() {
        assertFalse(QuasarConfig.destructiveEffectsEnabled());
    }

    @Test
    void limitsBlockJournalsByDefault() {
        assertEquals(65_536, QuasarConfig.BLOCK_JOURNAL_MAX_ENTRIES.getDefault());
        assertEquals(65_536, QuasarConfig.blockJournalMaxEntries());
    }

    @Test
    void exposesSoftCapacityBudgets() {
        assertEquals(128, QuasarConfig.standaloneVisualBudget());
        assertEquals(1_024, QuasarConfig.segmentBudget());
        assertEquals(128, QuasarConfig.gameplaySessionBudget());
        assertEquals(8, QuasarConfig.gameplaySessionsPerOwnerBudget());
        assertEquals(1, QuasarConfig.gameplayStartsPerOwnerPerTickBudget());
        assertEquals(256, QuasarConfig.gameplayPendingBudget());
        assertEquals(16, QuasarConfig.cinematicSessionBudget());
        assertEquals(4_096, QuasarConfig.terrainTicketBudget());
        assertEquals(256, QuasarConfig.entitySpawnsPerLevelTickBudget());
    }
}
