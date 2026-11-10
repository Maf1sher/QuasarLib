package mafisher.quasar;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side configuration for destructive gameplay effects. */
public final class QuasarConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec.BooleanValue DESTRUCTIVE_EFFECTS_ENABLED = BUILDER
        .comment(
            "Allows gameplay effects that declare the destructive capability to start and restore.",
            "Disabling this prevents execution; effect definitions and persisted descriptors are kept.",
            "Requires a world restart."
        )
        .worldRestart()
        .define("destructive_effects_enabled", true);

    static final ModConfigSpec.IntValue BLOCK_JOURNAL_MAX_ENTRIES = BUILDER
        .comment(
            "Maximum recorded block changes per destructive effect.",
            "Overflow still applies changes but disables rollback for that effect.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("block_journal_max_entries", 65_536, 1, 1_000_000);

    static final ModConfigSpec.IntValue BLOCK_JOURNAL_MAX_SECTIONS = BUILDER
        .comment(
            "Maximum recorded bulk section snapshots per destructive effect.",
            "Bulk section edits compact hundreds of thousands of block changes into one snapshot.",
            "Overflow still applies changes but disables rollback for that effect.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("block_journal_max_sections", 4_096, 1, 65_536);

    static final ModConfigSpec.IntValue STANDALONE_VISUAL_BUDGET = BUILDER
        .comment(
            "Soft per-level budget for standalone client visuals.",
            "When over budget the engine culls the lowest-priority effect instead of rejecting starts.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("standalone_visual_budget", 128, 1, 4_096);

    static final ModConfigSpec.IntValue SEGMENT_BUDGET = BUILDER
        .comment(
            "Soft per-level budget for segmented cells.",
            "Segmented cells never consume standalone visual slots.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("segment_budget", 1_024, 128, 65_536);

    static final ModConfigSpec.IntValue GAMEPLAY_SESSION_BUDGET = BUILDER
        .comment(
            "Soft per-level budget for active gameplay sessions.",
            "Starts above the budget wait in a priority queue instead of throwing.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("gameplay_session_budget", 128, 1, 4_096);

    static final ModConfigSpec.IntValue GAMEPLAY_SESSIONS_PER_OWNER_BUDGET = BUILDER
        .comment(
            "Soft per-owner gameplay session budget.",
            "Starts above the budget wait in a priority queue instead of throwing.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("gameplay_sessions_per_owner_budget", 8, 1, 1_024);

    static final ModConfigSpec.IntValue GAMEPLAY_STARTS_PER_OWNER_PER_TICK_BUDGET = BUILDER
        .comment(
            "Maximum immediate gameplay starts per owner per tick.",
            "Further starts are deferred to the next tick instead of throwing.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("gameplay_starts_per_owner_per_tick", 1, 1, 64);

    static final ModConfigSpec.IntValue GAMEPLAY_PENDING_BUDGET = BUILDER
        .comment(
            "Maximum queued gameplay starts per level.",
            "When full, the lowest-priority queued start is dropped and counted.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("gameplay_pending_budget", 256, 1, 4_096);

    static final ModConfigSpec.IntValue CINEMATIC_SESSION_BUDGET = BUILDER
        .comment(
            "Soft per-level budget for cinematic sessions.",
            "When over budget the engine culls the lowest-priority cinematic instead of rejecting starts.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("cinematic_session_budget", 16, 1, 1_024);

    static final ModConfigSpec.IntValue BLOCK_CHANGES_PER_LEVEL_TICK_BUDGET = BUILDER
        .comment(
            "Maximum destructive block changes applied across all gameplay effects per level tick.",
            "This is a safety threshold; large cinematic effects degrade into a longer carve instead of",
            "freezing the server. Raise it for very large black holes or terrain edits.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("block_changes_per_level_tick", 4_096, 1, 16_384);

    static final ModConfigSpec.IntValue SECTION_EDITS_PER_LEVEL_TICK_BUDGET = BUILDER
        .comment(
            "Maximum bulk 16x16x16 section edits applied across all gameplay effects per level tick.",
            "Bulk edits clear whole sections in one engine operation and resend affected chunks once.",
            "This is a safety threshold; effects slow down instead of being rejected.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("section_edits_per_level_tick", 8, 1, 256);

    static final ModConfigSpec.IntValue ENTITY_SPAWNS_PER_LEVEL_TICK_BUDGET = BUILDER
        .comment(
            "Maximum effect entity spawns applied per level tick.",
            "Excess spawns are dropped and counted; gameplay effects never throw for this budget.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("entity_spawns_per_level_tick", 256, 1, 65_536);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private QuasarConfig() {
    }

    /** Whether destructive gameplay effects may start or resume on this server. */
    public static boolean destructiveEffectsEnabled() {
        try {
            return DESTRUCTIVE_EFFECTS_ENABLED.get();
        } catch (IllegalStateException notLoaded) {
            return DESTRUCTIVE_EFFECTS_ENABLED.getDefault();
        }
    }

    /** Maximum recorded block changes per effect before rollback is disabled. */
    public static int blockJournalMaxEntries() {
        try {
            return BLOCK_JOURNAL_MAX_ENTRIES.get();
        } catch (IllegalStateException notLoaded) {
            return BLOCK_JOURNAL_MAX_ENTRIES.getDefault();
        }
    }

    /** Maximum recorded bulk section snapshots per effect before rollback is disabled. */
    public static int blockJournalMaxSections() {
        try {
            return BLOCK_JOURNAL_MAX_SECTIONS.get();
        } catch (IllegalStateException notLoaded) {
            return BLOCK_JOURNAL_MAX_SECTIONS.getDefault();
        }
    }

    /** Soft per-level budget for standalone visuals. */
    public static int standaloneVisualBudget() {
        return value(STANDALONE_VISUAL_BUDGET);
    }

    /** Soft per-level budget for segmented cells. */
    public static int segmentBudget() {
        return value(SEGMENT_BUDGET);
    }

    /** Soft per-level budget for active gameplay sessions. */
    public static int gameplaySessionBudget() {
        return value(GAMEPLAY_SESSION_BUDGET);
    }

    /** Soft per-owner budget for active gameplay sessions. */
    public static int gameplaySessionsPerOwnerBudget() {
        return value(GAMEPLAY_SESSIONS_PER_OWNER_BUDGET);
    }

    /** Immediate gameplay starts allowed per owner per tick. */
    public static int gameplayStartsPerOwnerPerTickBudget() {
        return value(GAMEPLAY_STARTS_PER_OWNER_PER_TICK_BUDGET);
    }

    /** Maximum queued gameplay starts per level. */
    public static int gameplayPendingBudget() {
        return value(GAMEPLAY_PENDING_BUDGET);
    }

    /** Soft per-level budget for cinematic sessions. */
    public static int cinematicSessionBudget() {
        return value(CINEMATIC_SESSION_BUDGET);
    }

    /** Maximum destructive block changes applied per level tick. */
    public static int blockChangesPerLevelTickBudget() {
        return value(BLOCK_CHANGES_PER_LEVEL_TICK_BUDGET);
    }

    /** Maximum bulk section edits applied across all effects per level tick. */
    public static int sectionEditsPerLevelTickBudget() {
        return value(SECTION_EDITS_PER_LEVEL_TICK_BUDGET);
    }

    /** Maximum effect entity spawns applied per level tick. */
    public static int entitySpawnsPerLevelTickBudget() {
        return value(ENTITY_SPAWNS_PER_LEVEL_TICK_BUDGET);
    }

    private static int value(ModConfigSpec.IntValue config) {
        try {
            return config.get();
        } catch (IllegalStateException notLoaded) {
            return config.getDefault();
        }
    }
}
