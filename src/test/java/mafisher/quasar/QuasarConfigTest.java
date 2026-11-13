package mafisher.quasar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.budget.EffectBudgets;
import mafisher.quasar.api.gameplay.block.EffectBulkBlockEditor;
import mafisher.quasar.testing.GlobalStateTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
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
    void exposesSpecDefaultsThroughGetters() {
        TestConfig.loadDefaults();

        assertEquals(QuasarConfig.BLOCK_JOURNAL_MAX_ENTRIES.getDefault(),
            QuasarConfig.blockJournalMaxEntries());
        assertEquals(QuasarConfig.BLOCK_JOURNAL_MAX_SECTIONS.getDefault(),
            QuasarConfig.blockJournalMaxSections());
        assertEquals(QuasarConfig.STANDALONE_VISUAL_BUDGET.getDefault(),
            QuasarConfig.standaloneVisualBudget());
        assertEquals(QuasarConfig.SEGMENT_BUDGET.getDefault(), QuasarConfig.segmentBudget());
        assertEquals(QuasarConfig.GAMEPLAY_SESSION_BUDGET.getDefault(),
            QuasarConfig.gameplaySessionBudget());
        assertEquals(QuasarConfig.GAMEPLAY_SESSIONS_PER_OWNER_BUDGET.getDefault(),
            QuasarConfig.gameplaySessionsPerOwnerBudget());
        assertEquals(QuasarConfig.GAMEPLAY_STARTS_PER_OWNER_PER_TICK_BUDGET.getDefault(),
            QuasarConfig.gameplayStartsPerOwnerPerTickBudget());
        assertEquals(QuasarConfig.GAMEPLAY_PENDING_BUDGET.getDefault(),
            QuasarConfig.gameplayPendingBudget());
        assertEquals(QuasarConfig.CINEMATIC_SESSION_BUDGET.getDefault(),
            QuasarConfig.cinematicSessionBudget());
        assertEquals(QuasarConfig.BLOCK_CHANGES_PER_LEVEL_TICK_BUDGET.getDefault(),
            QuasarConfig.blockChangesPerLevelTickBudget());
        assertEquals(QuasarConfig.SECTION_EDITS_PER_LEVEL_TICK_BUDGET.getDefault(),
            QuasarConfig.sectionEditsPerLevelTickBudget());
        assertEquals(QuasarConfig.TERRAIN_TICKET_BUDGET.getDefault(),
            QuasarConfig.terrainTicketBudget());
        assertEquals(QuasarConfig.ENTITY_SPAWNS_PER_LEVEL_TICK_BUDGET.getDefault(),
            QuasarConfig.entitySpawnsPerLevelTickBudget());
    }

    @Test
    void readsLoadedBudgetValues() {
        TestConfig.load(config -> {
            config.set("standalone_visual_budget", 7);
            config.set("section_edits_per_level_tick", 3);
            config.set("block_journal_max_entries", 12_345);
        });

        assertEquals(7, QuasarConfig.standaloneVisualBudget());
        assertEquals(7, EffectBudgets.standaloneVisuals());
        assertEquals(3, QuasarConfig.sectionEditsPerLevelTickBudget());
        assertEquals(3, EffectBudgets.sectionEditsPerLevelTick());
        assertEquals(12_345, QuasarConfig.blockJournalMaxEntries());
    }

    @Test
    void clampsPerOwnerSessionsToTheLevelBudget() {
        TestConfig.load(config -> {
            config.set("gameplay_session_budget", 4);
            config.set("gameplay_sessions_per_owner_budget", 10);
        });

        assertEquals(4, QuasarConfig.gameplaySessionBudget());
        assertEquals(4, QuasarConfig.gameplaySessionsPerOwnerBudget());
        assertEquals(4, EffectBudgets.gameplaySessionsPerOwner());
    }

    @Test
    void terrainTicketBudgetCoversOneMaximumBulkEdit() {
        assertTrue(QuasarConfig.TERRAIN_TICKET_BUDGET.getDefault() >= EffectBulkBlockEditor.maxTicketChunks());
        TestConfig.loadDefaults();

        assertTrue(QuasarConfig.terrainTicketBudget() >= EffectBulkBlockEditor.maxTicketChunks());
    }
}
