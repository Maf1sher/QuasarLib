package mafisher.quasar;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side configuration for gameplay, terrain, and effect capacity budgets.
 *
 * <p>Values are read through {@link ModConfigSpec.ConfigValue#get()}, which already caches the
 * loaded value and respects {@code worldRestart()} semantics. Before the config loads, getters
 * return their defaults; destructive effects stay disabled until the config is available.</p>
 */
public final class QuasarConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuasarConfig.class);
    private static final int MIN_TERRAIN_TICKET_BUDGET = 1_024;
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

    static final ModConfigSpec.IntValue TERRAIN_TICKET_BUDGET = BUILDER
        .comment(
            "Maximum chunk tickets held by bulk terrain edits across one level.",
            "A bulk edit reserves all of its chunks at once; a request above the remaining budget waits.",
            "The minimum keeps one maximum-size edit (1024 chunks) able to start.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("terrain_ticket_budget", 4_096, MIN_TERRAIN_TICKET_BUDGET, 131_072);

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

    /**
     * Whether destructive gameplay effects may start or resume on this server. Fails closed
     * before the config is loaded so an unavailable config cannot enable destruction.
     */
    public static boolean destructiveEffectsEnabled() {
        return SPEC.isLoaded() && DESTRUCTIVE_EFFECTS_ENABLED.get();
    }

    /** Maximum recorded block changes per effect before rollback is disabled. */
    public static int blockJournalMaxEntries() {
        return value(BLOCK_JOURNAL_MAX_ENTRIES);
    }

    /** Maximum recorded bulk section snapshots per effect before rollback is disabled. */
    public static int blockJournalMaxSections() {
        return value(BLOCK_JOURNAL_MAX_SECTIONS);
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

    /** Soft per-owner budget for active gameplay sessions; capped by the level budget. */
    public static int gameplaySessionsPerOwnerBudget() {
        return Math.min(value(GAMEPLAY_SESSIONS_PER_OWNER_BUDGET), gameplaySessionBudget());
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

    /** Maximum chunk tickets held by bulk terrain edits across one level. */
    public static int terrainTicketBudget() {
        return Math.max(value(TERRAIN_TICKET_BUDGET), MIN_TERRAIN_TICKET_BUDGET);
    }

    /** Maximum effect entity spawns applied per level tick. */
    public static int entitySpawnsPerLevelTickBudget() {
        return value(ENTITY_SPAWNS_PER_LEVEL_TICK_BUDGET);
    }

    /** Logs cross-field adjustments once per config load. */
    static void validate() {
        if (!SPEC.isLoaded()) {
            return;
        }
        int sessions = GAMEPLAY_SESSION_BUDGET.get();
        int perOwner = GAMEPLAY_SESSIONS_PER_OWNER_BUDGET.get();
        if (perOwner > sessions) {
            LOGGER.warn(
                "gameplay_sessions_per_owner_budget ({}) exceeds gameplay_session_budget ({}); using {} per owner",
                perOwner, sessions, sessions
            );
        }
        int tickets = TERRAIN_TICKET_BUDGET.get();
        if (tickets < MIN_TERRAIN_TICKET_BUDGET) {
            LOGGER.warn(
                "terrain_ticket_budget ({}) is below one maximum bulk edit ({}); using {}",
                tickets, MIN_TERRAIN_TICKET_BUDGET, MIN_TERRAIN_TICKET_BUDGET
            );
        }
    }

    /** {@link ModConfigSpec.ConfigValue#get()} is cached by NeoForge; only the unloaded fallback needs a check. */
    private static int value(ModConfigSpec.IntValue config) {
        return SPEC.isLoaded() ? config.get() : config.getDefault();
    }
}
